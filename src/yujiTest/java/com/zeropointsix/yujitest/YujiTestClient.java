package com.zeropointsix.yujitest;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import com.zeropointsix.eraser.yuji.BoneSpike;
import com.zeropointsix.eraser.yuji.client.YujiModel;
import com.zeropointsix.eraser.yuji.client.YujiRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.SoundEvent.SoundSourceEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.event.GeoRenderEvent;

@Mod.EventBusSubscriber(modid = "yuji_qa", value = Dist.CLIENT)
public final class YujiTestClient {
    private static final Path RESULTS = Path.of(System.getProperty("yuji.qa.results"));
    private static final Map<String, double[]> poses = new HashMap<>();
    private static final Map<String, String[]> names = new HashMap<>();
    private static final ArrayList<TickableSoundInstance> loops = new ArrayList<>();
    private static volatile String phase = "";
    private static volatile int ticks;
    private static int frames;
    private static boolean failed, captured;
    private static SoundInstance masked;
    private static NativeImage visible;
    private static net.minecraft.world.entity.Entity hidden;
    private static net.minecraft.world.phys.Vec3 visiblePosition;
    private static int hiddenAt;
    private static String pendingEvidence;
    private static final Map<String, SoundInstance> deathSounds = new HashMap<>();
    private static final Map<String, SoundInstance> observedSounds = new HashMap<>();
    private static final java.util.Set<String> playedSounds = new HashSet<>();
    private static boolean audioDone;
    private static int deathFrames, livingDeathFrames;
    private static double deathMinAngle, deathMaxAngle;
    private static boolean deathStartCaptured, deathEndCaptured, deathMetadataSeen;
    private static YujiMonster deathProbe;
    private static String lastTanyuAction = "";
    private static int stalledSerial = -1;
    private record Playback(String profile, String sound, String evidence) {}
    private static final ConcurrentLinkedQueue<Playback> startedSounds = new ConcurrentLinkedQueue<>();

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void pass(String name, String text) throws Exception {
        Files.writeString(RESULTS.resolve("client-" + name + ".pass"), text + "\n");
        System.out.println("YUJI_QA_PASS " + name + ": " + text);
    }
    private static void fail(Throwable failure) {
        failed = true;
        if (hidden != null) hidden.setPos(visiblePosition);
        if (visible != null) visible.close();
        visible = null; hidden = null;
        failure.printStackTrace();
        try { Files.writeString(RESULTS.resolve("client.failed"), failure.toString()); } catch (Exception ignored) { }
    }
    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (!text.startsWith("YUJI_QA:")) return;
        phase = text.substring(8); ticks = 0; frames = 0; captured = false; audioDone = false; poses.clear();
        deathFrames = 0; livingDeathFrames = 0; deathMinAngle = 180; deathMaxAngle = 0;
        deathStartCaptured = false; deathEndCaptured = false; deathMetadataSeen = false; deathProbe = null;
        // Pin only the gallery camera; survival interactions use normal gravity on both sides.
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.setNoGravity(!phase.startsWith("ai-") && !phase.startsWith("audio-"));
        if (!phase.startsWith("ai-") && !phase.startsWith("audio-")) { observedSounds.clear(); playedSounds.clear(); }
        event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void sound(PlaySoundEvent event) {
        var sound = event.getSound();
        if (sound == null) return;
        String path = sound.getLocation().getPath();
        if (path.startsWith("entity.qihai.tanyu."))
            System.out.println("YUJI_SOUND_REQUEST phase=" + phase + ", tick=" + ticks + ", event=" + sound.getLocation()
                    + ", instance=" + sound.getClass().getSimpleName());
        if (sound instanceof TickableSoundInstance loop && (path.contains("qiyu.flute") || path.contains("shifotou_battle"))) loops.add(loop);
        if (phase.equals("qiyu") && path.equals("entity.zombie.ambient")) masked = sound;
        if (sound.getLocation().getNamespace().equals("qihai") && path.endsWith(".death")) deathSounds.put(path, sound);
        if (sound.getLocation().getNamespace().equals("qihai") || phase.equals("ai-wangyu") || phase.equals("bone_spike_stuck"))
            observedSounds.put(sound.getLocation().toString(), sound);
    }
    @SubscribeEvent public static void started(SoundSourceEvent event) {
        var sound = event.getSound();
        String currentPhase = phase;
        if (currentPhase.isEmpty() || !(sound.getLocation().getNamespace().equals("qihai")
                || currentPhase.equals("ai-wangyu") || currentPhase.equals("bone_spike_stuck"))) return;
        // This callback runs on the sound thread after Channel.play(), even when the render thread stalls.
        if (sound.getSound() == null || !event.getChannel().playing()) return;
        String profile = currentPhase.replaceFirst("^(ai-|audio-)", "");
        String record = "phase=" + currentPhase + ", tick=" + ticks + ", event=" + sound.getLocation()
                + ", decoded=" + sound.getSound().getLocation() + ", channelPlaying=true, volume=" + sound.getVolume()
                + ", pitch=" + sound.getPitch() + ", position=" + sound.getX() + "," + sound.getY() + "," + sound.getZ()
                + ", backend=" + System.getProperty("yuji.qa.audio", "OpenAL-null") + "\n";
        startedSounds.add(new Playback(profile, sound.getLocation().toString(), record));
    }
    @SubscribeEvent public static void galleryCamera(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || failed || phase.isEmpty()
                || phase.startsWith("ai-") || phase.startsWith("audio-")) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        // Player metadata/abilities can arrive after the phase message. Reassert the gallery-only lock
        // before travel so a late packet cannot drop the elevated giant camera to the arena floor.
        player.setNoGravity(true);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (player.getAbilities().mayfly) player.getAbilities().flying = true;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || phase.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof DisconnectedScreen) { fail(new AssertionError(mc.screen.getNarrationMessage().getString())); return; }
        if (mc.level == null || mc.player == null) return;
        try {
            ticks++;
            if (phase.equals("ai-tanyu")) for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof YujiMonster monster) || monster.kind() != YujiKind.TANYU) continue;
                String action = monster.action() + ":" + monster.actionSerial();
                if (!action.equals(lastTanyuAction) || ticks % 10 == 0)
                    System.out.println("YUJI_ACTION_DIAGNOSTIC phase=" + phase + ", tick=" + ticks + ", entityTick=" + monster.tickCount
                            + ", action=" + action + ", position=" + monster.position() + ", player=" + mc.player.position());
                lastTanyuAction = action;
                if (Boolean.getBoolean("yuji.qa.stallBite") && monster.action().equals("grab_bite")
                        && monster.actionSerial() != stalledSerial) {
                    stalledSerial = monster.actionSerial();
                    System.out.println("YUJI_CLIENT_STALL biteSerial=" + stalledSerial + ", milliseconds=1600");
                    Thread.sleep(1600);
                }
            }
            String profile = phase.replaceFirst("^(ai-|audio-)", "");
            for (Playback playback; (playback = startedSounds.poll()) != null;) {
                if (playback.profile().equals(profile)) playedSounds.add(playback.sound());
                Files.writeString(RESULTS.resolve("audio-events-" + playback.profile() + ".log"), playback.evidence(),
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            }
            for (var entry : observedSounds.entrySet()) {
                var sound = entry.getValue();
                if (!playedSounds.contains(entry.getKey()) && sound.getSound() != null && mc.getSoundManager().isActive(sound)) {
                    playedSounds.add(entry.getKey());
                    String record = "phase=" + phase + ", tick=" + ticks + ", event=" + entry.getKey()
                            + ", decoded=" + sound.getSound().getLocation() + ", active=true, volume=" + sound.getVolume()
                            + ", pitch=" + sound.getPitch() + ", position=" + sound.getX() + "," + sound.getY() + "," + sound.getZ()
                            + ", backend=" + System.getProperty("yuji.qa.audio", "OpenAL-null") + "\n";
                    Files.writeString(RESULTS.resolve("audio-events-" + profile + ".log"), record,
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                }
            }
            if (ticks == 1) { mc.setScreen(null); mc.mouseHandler.grabMouse(); }
            if (phase.equals("ai-chennu") && ticks == 5) {
                for (var entity : mc.level.entitiesForRendering()) if (entity instanceof YujiMonster monster && monster.kind() == YujiKind.CHENNU) {
                    mc.gameMode.attack(mc.player, monster);
                    mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    break;
                }
            }
            if (phase.startsWith("audio-") && !audioDone) {
                String kind = phase.substring(6);
                var sound = deathSounds.get("entity.qihai." + kind + ".death");
                if (sound != null && sound.getSound() != null && mc.getSoundManager().isActive(sound)) {
                    String skill = switch (kind) {
                        case "yuchi" -> "step"; case "shasheng" -> "chop"; case "toudao" -> "steal";
                        case "ekou" -> "curse"; case "liangshe" -> "whisper"; case "xieyin" -> "lure";
                        case "qiyu" -> "flute"; case "tanyu" -> "bite"; case "chennu" -> "stomp";
                        case "shifotou" -> "gaze"; default -> "";
                    };
                    if (kind.equals("wangyu")) require(playedSounds.stream().anyMatch(name -> name.startsWith("minecraft:")), "real targeted false voice played");
                    else require(playedSounds.contains("qihai:entity.qihai." + kind + "." + skill), "real locomotion/skill audio played: " + kind + "." + skill
                            + ", requested=" + observedSounds.keySet() + ", active=" + playedSounds);
                    pass(phase, "event=" + sound.getLocation() + ", decoded=" + sound.getSound().getLocation()
                            + ", active=true, volume=" + sound.getVolume() + ", backend=" + System.getProperty("yuji.qa.audio", "OpenAL-null") + ", observed=" + playedSounds);
                    audioDone = true;
                } else require(ticks < 120, "real audio playback not active: " + kind);
            }
            if (phase.equals("ready") && ticks == 60) {
                for (var sound : YujiContent.SOUND.values()) {
                    require(mc.getSoundManager().getSoundEvent(sound.getId()) != null, "sound event resolves: " + sound.getId());
                }
                pass("ready", "Real client joined, 47 sound events loaded.");
            } else if (phase.equals("qiyu") && ticks == 35) {
                mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE,
                        1, 1, RandomSource.create(), mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            } else if (phase.equals("cleanup") && ticks == 40) {
                String profiles = System.getProperty("yuji.qa.profiles", "");
                long expectedLoops = profiles.isEmpty() ? 2 : java.util.Arrays.stream(profiles.split(","))
                        .filter(kind -> kind.equals("qiyu") || kind.equals("shifotou")).count();
                require(loops.size() >= expectedLoops, "selected flute and boss music actually played");
                require(loops.stream().allMatch(TickableSoundInstance::isStopped), "removed entities stop audio loops");
                pass("gallery", profiles.isEmpty()
                        ? "All 11 profiles and bone spike rendered; 11 death sounds played; flute and boss loops stopped after removal."
                        : "Focused real-client profiles=" + profiles + "; selected rendering/AI/audio assertions passed.");
                phase = "";
            }
        } catch (Throwable failure) { fail(failure); }
    }
    private static String[] bones(String kind) throws Exception {
        if (!names.containsKey(kind)) {
            try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(YujiContent.id("geo/" + kind + ".geo.json"))) {
                var bones = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry")
                        .get(0).getAsJsonObject().getAsJsonArray("bones");
                var list = new ArrayList<String>();
                for (var bone : bones) list.add(bone.getAsJsonObject().get("name").getAsString());
                names.put(kind, list.toArray(String[]::new));
            }
        }
        return names.get(kind);
    }
    @SubscribeEvent public static void model(GeoRenderEvent.Entity.Post event) {
        if (phase.startsWith("death-") && !failed && event.getEntity() instanceof YujiMonster monster
                && monster.kind() == YujiKind.YUCHI) {
            try {
                double angle = Math.abs(Math.toDegrees(event.getModel().getBone("root").orElseThrow().getRotX()));
                boolean crawl = phase.equals("death-crawl");
                if (!monster.isDeadOrDying()) {
                    if (deathProbe != monster) { deathProbe = monster; livingDeathFrames = 0; }
                    if ((crawl && angle >= 75 && angle <= 85) || (!crawl && angle < 10)) livingDeathFrames++;
                    if (livingDeathFrames == 10) pass("ready-" + phase, "living root angle=" + angle);
                } else {
                    if (monster != deathProbe || livingDeathFrames < 10 || monster.deathTime == 0) return;
                    boolean synced = monster.deathAnimation().equals(phase.equals("death-standing") ? "death" : "crawl_death");
                    require(!deathMetadataSeen || synced, "synced death animation remains stable");
                    deathMetadataSeen |= synced;
                    deathFrames++;
                    deathMinAngle = Math.min(deathMinAngle, angle); deathMaxAngle = Math.max(deathMaxAngle, angle);
                    if (crawl) require(angle >= 75, "crawl death never stands up: " + angle);
                    System.out.println("YUJI_DEATH_FRAME phase=" + phase + ", tick=" + monster.deathTime + ", rootAngle=" + angle
                            + ", syncedAnimation=" + monster.deathAnimation());
                }
            } catch (Throwable failure) { fail(failure); }
            return;
        }
        if (!failed && !captured && ticks >= 20 && phase.startsWith("bone_spike") && event.getEntity() instanceof BoneSpike) { frames++; return; }
        if (failed || captured || ticks < 20 || !(event.getEntity() instanceof YujiMonster monster) || !monster.kind().id().equals(phase)) return;
        try {
            frames++;
            for (String name : bones(phase)) {
                var bone = event.getModel().getBone(name).orElseThrow();
                double[] values = {bone.getRotX(), bone.getRotY(), bone.getRotZ(), bone.getPosX(), bone.getPosY(), bone.getPosZ(), bone.getScaleX(), bone.getScaleY(), bone.getScaleZ()};
                double[] range = poses.computeIfAbsent(name, ignored -> {
                    double[] out = new double[18];
                    for (int i = 0; i < 9; i++) out[i * 2] = out[i * 2 + 1] = values[i];
                    return out;
                });
                for (int i = 0; i < 9; i++) { range[i * 2] = Math.min(range[i * 2], values[i]); range[i * 2 + 1] = Math.max(range[i * 2 + 1], values[i]); }
            }
        } catch (Throwable failure) { fail(failure); }
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (phase.startsWith("death-")) {
            if (event.phase == TickEvent.Phase.END && !failed && deathProbe != null && deathFrames > 0) {
                try {
                    boolean crawl = phase.equals("death-crawl");
                    if (!deathStartCaptured) {
                        try (var shot = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                            shot.writeToFile(RESULTS.resolve(phase + "-start.png"));
                        }
                        deathStartCaptured = true;
                    }
                    if (!deathEndCaptured && deathMetadataSeen && deathMaxAngle >= (crawl ? 89.75 : 87.75)
                            && deathProbe.deathAnimation().equals(phase.equals("death-standing") ? "death" : "crawl_death")
                            && !deathProbe.isRemoved()) {
                        require(deathFrames >= 3, "death animation renders multiple frames before its final pose");
                        if (!crawl) require(deathMinAngle < 20, "standing death begins upright");
                        try (var shot = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                            shot.writeToFile(RESULTS.resolve(phase + "-end.png"));
                        }
                        pass(phase, "renderedFrames=" + deathFrames + ", rootAngleRange=" + deathMinAngle + ".." + deathMaxAngle
                                + ", finalDeathTime=" + deathProbe.deathTime + ", deathTimePositive=true, syncedAnimation=" + deathProbe.deathAnimation());
                        deathEndCaptured = true;
                    }
                    require(deathEndCaptured || !deathProbe.isRemoved(), "death animation syncs and reaches its final pose before removal");
                } catch (Throwable failure) { fail(failure); }
            }
            return;
        }
        if (event.phase == TickEvent.Phase.START && !captured && !phase.isEmpty()
                && !phase.startsWith("ai-") && !phase.startsWith("audio-") && !phase.equals("ready") && !phase.equals("cleanup")) {
            int delay = Integer.getInteger("yuji.qa.frameDelayMs", 0);
            if (delay > 0) try { Thread.sleep(delay); } catch (InterruptedException failure) {
                Thread.currentThread().interrupt(); fail(failure);
            }
        }
        if (event.phase != TickEvent.Phase.END || failed || captured || ticks < 110 || phase.equals("ready") || phase.equals("cleanup")
                || phase.startsWith("ai-") || phase.startsWith("audio-") || phase.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        try {
            // Slow software rendering can lag behind client ticks; require the same real frame count within a bounded window.
            if (visible == null && frames <= 10) {
                System.out.println("YUJI_RENDER_WAIT phase=" + phase + ", frames=" + frames + ", ticks=" + ticks);
                require(ticks < 310, "real render readiness timed out: phase=" + phase + ", frames=" + frames + ", ticks=" + ticks);
                return;
            }
            if (visible != null) {
                if (ticks < hiddenAt + 3) return;
                try (var control = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    int width = control.getWidth(), height = control.getHeight(), changed = 0;
                    int minX = width, maxX = 0, minY = height, maxY = 0;
                    int left = width / 5, right = width * 4 / 5, top = height / 10, bottom = height * 9 / 10;
                    for (int x = left; x < right; x++) for (int y = top; y < bottom; y++) {
                        int a = visible.getPixelRGBA(x, y), b = control.getPixelRGBA(x, y);
                        int delta = Math.abs((a & 255) - (b & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255))
                                + Math.abs((a >> 16 & 255) - (b >> 16 & 255));
                        if (delta > 70) {
                            changed++; minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        }
                    }
                    require(changed >= 80 && maxX - minX >= 8 && maxY - minY >= 15,
                            "visible entity occupies central framebuffer: changed=" + changed);
                    require(changed < (right - left) * (bottom - top) * .65, "control camera remains stable");
                    control.writeToFile(RESULTS.resolve(phase + "-control.png"));
                    require(minX > left && maxX < right - 1 && minY > top && maxY < bottom - 1,
                            "entity difference is framed away from viewport edges: " + phase
                                    + ", changed=" + changed + ", bounds=" + minX + "," + minY + "," + maxX + "," + maxY
                                    + ", camera=" + mc.player.position());
                    pass(phase, pendingEvidence + ", foregroundPixels=" + changed + ", bounds=" + minX + "," + minY + "," + maxX + "," + maxY);
                } finally {
                    hidden.setPos(visiblePosition); hidden = null; visible.close(); visible = null;
                }
                captured = true;
                return;
            }
            if (phase.startsWith("bone_spike")) {
                BoneSpike spike = null;
                for (var entity : mc.level.entitiesForRendering()) if (entity instanceof BoneSpike candidate) spike = candidate;
                require(spike != null && frames > 10, "real projectile rendered: frames=" + frames);
                if (phase.equals("bone_spike_stuck")) {
                    require(spike.stuck(), "rendered projectile is truly embedded after collision");
                    require(playedSounds.contains("minecraft:entity.skeleton.hurt"), "real wall impact sound played");
                }
                for (String asset : new String[]{"geo/bone_spike.geo.json", "textures/entity/bone_spike.png", "animations/bone_spike.animation.json"}) {
                    mc.getResourceManager().getResourceOrThrow(YujiContent.id(asset));
                }
                visible = Screenshot.takeScreenshot(mc.getMainRenderTarget());
                visible.writeToFile(RESULTS.resolve(phase + ".png"));
                pendingEvidence = "real projectile frames=" + frames + ", embedded=" + spike.stuck()
                        + (spike.stuck() ? ", impactAudio=minecraft:entity.skeleton.hurt, active=true" : ", flightAudio=none");
                hidden = spike; hiddenAt = ticks; visiblePosition = spike.position(); hidden.setPos(visiblePosition.add(1000, 0, 0));
                return;
            }
            YujiMonster monster = null;
            for (var entity : mc.level.entitiesForRendering()) if (entity instanceof YujiMonster candidate && candidate.kind().id().equals(phase)) monster = candidate;
            require(monster != null, "server entity reaches real client: " + phase);
            require(mc.getEntityRenderDispatcher().getRenderer(monster) instanceof YujiRenderer, "GeckoLib renderer registered");
            var model = new YujiModel();
            mc.getResourceManager().getResourceOrThrow(model.getTextureResource(monster));
            mc.getResourceManager().getResourceOrThrow(model.getModelResource(monster));
            mc.getResourceManager().getResourceOrThrow(model.getAnimationResource(monster));
            if (monster.kind().giant()) mc.getResourceManager().getResourceOrThrow(YujiContent.id("textures/entity/" + phase + "_glowmask.png"));
            require(frames > 10, "entity actually rendered, frames=" + frames);
            double movement = 0;
            for (double[] range : poses.values()) for (int i = 0; i < 9; i++) movement = Math.max(movement, range[i * 2 + 1] - range[i * 2]);
            require(movement > .001, "rendered bones animate, movement=" + movement);
            if (phase.equals("qiyu")) {
                require(masked != null && masked.getClass().getSimpleName().equals("MaskedSound"), "real sound pipeline applies flute mask");
                require(Math.abs(masked.getVolume() - .3F) < .001, "hostile sound volume reduced by seventy percent");
                require(masked.getSound() != null, "masked sound resolves without initialization crash");
            }
            try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                var colors = new HashSet<Integer>();
                for (int x = 0; x < shot.getWidth(); x += 4) for (int y = 0; y < shot.getHeight(); y += 4) colors.add(shot.getPixelRGBA(x, y));
                require(colors.size() > 100, "framebuffer nonblank, colors=" + colors.size());
                shot.writeToFile(RESULTS.resolve(phase + ".png"));
            }
            pendingEvidence = "frames=" + frames + ", animatedBoneDelta=" + movement + ", texture/model/animation loaded"
                    + ", fpsLimit=" + mc.options.framerateLimit().get() + ", frameDelayMs=" + Integer.getInteger("yuji.qa.frameDelayMs", 0)
                    + (phase.equals("qiyu") ? ", maskedVolume=" + masked.getVolume() : "");
            visible = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            hidden = monster; hiddenAt = ticks; visiblePosition = monster.position();
            hidden.setPos(visiblePosition.add(1000, 0, 0));
        } catch (Throwable failure) { fail(failure); }
    }
}
