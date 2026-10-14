package com.zeropointsix.gravitytest;

import com.mojang.blaze3d.platform.NativeImage;
import com.zeropointsix.eraser.client.WingsClientData;
import com.zeropointsix.eraser.registry.ModSounds;
import com.zeropointsix.eraser.wings.net.SetDeployedPacket;
import com.zeropointsix.eraser.wings.net.WingsNet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.client.event.sound.SoundEvent.SoundSourceEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Actual AL source playback and post-render animation evidence, only in the test mod. */
@Mod.EventBusSubscriber(modid = "gravity_qa", value = Dist.CLIENT)
public final class WingsPresentationQa {
    private static final Map<String, AtomicInteger> PLAYED = new ConcurrentHashMap<>();
    private static final List<String> FRAMES = new ArrayList<>();
    private static Path results;
    private static CameraType oldCamera;
    private static boolean oldHideGui, oldBobView;
    private static int oldFov;
    private static int step;
    private static int ticks;
    private static int frames;
    private static int changedFrames;
    private static float previousPose;
    private static NativeImage previousFrame;
    private static boolean active;
    private static boolean recording;
    private static boolean target;
    private static Throwable renderFailure;

    @SubscribeEvent
    public static void staticSound(PlaySoundSourceEvent event) { played(event); }

    @SubscribeEvent
    public static void streamingSound(PlayStreamingSourceEvent event) { played(event); }

    private static void played(SoundSourceEvent event) {
        if (!"wingswearer".equals(System.getProperty("wings.qa.role"))) return;
        // Forge emits this on the audio executor after OpenAL Channel.play(), not
        // merely when gameplay requests a sound. A disabled sound engine cannot pass.
        if (event.getChannel().playing()) {
            String sound = event.getSound().getLocation().toString();
            PLAYED.computeIfAbsent(sound, ignored -> new AtomicInteger()).incrementAndGet();
            System.out.println("WINGS_AUDIO_SOURCE_PLAYING " + sound);
        }
    }

    static void begin(Minecraft mc, Path directory) {
        results = directory;
        oldCamera = mc.options.getCameraType();
        oldHideGui = mc.options.hideGui;
        oldBobView = mc.options.bobView().get();
        oldFov = mc.options.fov().get();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        mc.options.hideGui = true;
        mc.options.bobView().set(false);
        mc.options.fov().set(70);
        step = ticks = 0;
        active = true;
        mc.player.connection.sendCommand("wingsqa phase visualSetup");
    }

    static boolean tick(Minecraft mc, String phase) throws Exception {
        if (renderFailure != null) throw new AssertionError("Render evidence failed", renderFailure);
        if (++ticks > 200) throw new AssertionError("Animation evidence timed out at step " + step);
        if (step == 0) {
            var info = WingsClientData.get(mc.player);
            if (!phase.equals("visualSetup") || info == null || info.deployed()
                    || info.deployAnim() > 0.01f || mc.player.getY() < 235) return false;
            step = 1;
            ticks = 0;
        } else if (step == 1 && ticks >= 20) {
            start(mc, true);
            step = 2;
        } else if ((step == 2 || step == 3) && ticks >= 30) {
            require(frames >= 3 && changedFrames >= 2,
                    (target ? "deploy" : "stow") + " has >=3 rendered poses and >=2 changed wing-region frames: "
                            + frames + "/" + changedFrames);
            require(target ? previousPose > 0.99f : previousPose < 0.01f, "animation reached its endpoint");
            if (step == 2) {
                start(mc, false);
                step = 3;
            } else {
                Files.writeString(results.resolve("wings-animation-frames.json"), "[\n" + String.join(",\n", FRAMES) + "\n]\n");
                Files.writeString(results.resolve("wings-animation.pass"),
                        "PASS post-render deploy/stow sequences, monotonic interpolated poses, changed wing-sideband pixels excluding the player body, fixed camera and hidden HUD\n");
                writeAudioReport(mc, results);
                restore(mc);
                return true;
            }
        }
        return false;
    }

    private static void start(Minecraft mc, boolean deployed) {
        if (previousFrame != null) previousFrame.close();
        previousFrame = null;
        target = deployed;
        recording = true;
        frames = changedFrames = ticks = 0;
        previousPose = deployed ? -1 : 2;
        WingsNet.CHANNEL.sendToServer(new SetDeployedPacket(deployed));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void freezeStudio(TickEvent.ClientTickEvent event) {
        if (active && event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // Only the QA studio actor is held still. State synchronization,
                // production animation ticking and the real WingsLayer remain active.
                mc.player.setDeltaMovement(Vec3.ZERO);
                mc.player.setNoGravity(true);
                mc.player.setXRot(0);
                mc.player.setYRot(-90);
                mc.player.yBodyRot = -90;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterRender(TickEvent.RenderTickEvent event) {
        if (!active || !recording || event.phase != TickEvent.Phase.END || renderFailure != null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var info = WingsClientData.get(mc.player);
        if (info == null || info.deployed() != target) return;
        float pose = WingsClientData.renderPose(mc.player, event.renderTickTime).deployment();
        if (Math.abs(pose - previousPose) < 0.05f && !(target ? pose >= 1 : pose <= 0)) return;
        if (target ? previousPose >= 1 : previousPose <= 0) return;
        try {
            require(target ? pose >= previousPose : pose <= previousPose, "render pose progresses monotonically");
            NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            String name = "animation-" + (target ? "deploy" : "stow") + "-" + frames + ".png";
            image.writeToFile(results.resolve(name));
            int changed = 0;
            if (previousFrame != null) {
                // Exclude sky margins, GUI and the center body: idle arms or the
                // standing/fall-flying body transition are not wing-animation proof.
                for (int y = image.getHeight() / 5; y < image.getHeight() * 4 / 5; y++) {
                    for (int x = image.getWidth() / 8; x < image.getWidth() * 7 / 8; x++) {
                        if (Math.abs(x - image.getWidth() / 2) < image.getWidth() / 12) continue;
                        int a = image.getPixelRGBA(x, y), b = previousFrame.getPixelRGBA(x, y);
                        int delta = Math.abs((a & 255) - (b & 255))
                                + Math.abs(((a >>> 8) & 255) - ((b >>> 8) & 255))
                                + Math.abs(((a >>> 16) & 255) - ((b >>> 16) & 255));
                        if (delta > 30) changed++;
                    }
                }
                if (changed >= 20) changedFrames++;
                previousFrame.close();
            }
            FRAMES.add("{\"file\":\"" + name + "\",\"deployment\":" + pose + ",\"gameTick\":"
                    + mc.level.getGameTime() + ",\"partialTick\":" + event.renderTickTime
                    + ",\"changedWingRegionPixels\":" + changed + "}");
            previousFrame = image;
            previousPose = pose;
            frames++;
        } catch (Throwable failure) {
            renderFailure = failure;
        }
    }

    private static void writeAudioReport(Minecraft mc, Path directory) throws Exception {
        List<String> expected = List.of(ModSounds.WIND_LOOP.get().getLocation().toString(),
                ModSounds.THUNDER_BOOM.get().getLocation().toString(), ModSounds.THUNDER_BLINK.get().getLocation().toString());
        List<String> observations = new ArrayList<>();
        for (String sound : expected) {
            int count = PLAYED.getOrDefault(sound, new AtomicInteger()).get();
            require(count > 0, "real OpenAL source played " + sound + " count=" + count);
            observations.add("\"" + sound + "\":" + count);
        }
        Files.writeString(directory.resolve("wings-audio.json"), "{\"backend\":\"OpenAL Soft null\",\"sourcePlaying\":{"
                + String.join(",", observations) + "},\"subjectiveListening\":false}\n");
        Files.writeString(directory.resolve("wings-audio.pass"),
                "PASS decoded production wind/boom/blink sounds reached playing OpenAL sources; null output, not subjective listening\n");
    }

    static void restore(Minecraft mc) {
        if (!active) return;
        active = recording = false;
        if (previousFrame != null) previousFrame.close();
        previousFrame = null;
        mc.options.setCameraType(oldCamera);
        mc.options.hideGui = oldHideGui;
        mc.options.bobView().set(oldBobView);
        mc.options.fov().set(oldFov);
        if (mc.player != null) {
            var info = WingsClientData.get(mc.player);
            mc.player.setNoGravity(info != null && info.deployed());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("WINGS_PRESENTATION_ASSERT " + message);
    }

    private WingsPresentationQa() { }
}
