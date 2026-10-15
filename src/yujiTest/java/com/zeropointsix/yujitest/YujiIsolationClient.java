package com.zeropointsix.yujitest;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "yuji_qa", value = Dist.CLIENT)
public final class YujiIsolationClient {
    private static final Path RESULTS = Path.of(System.getProperty("yuji.qa.results"));
    private static final Set<String> VOICES = Set.of("block.wooden_door.open", "block.chest.open",
            "entity.player.hurt", "entity.villager.hurt", "entity.wolf.whine", "block.stone.step");
    private static final ArrayList<SoundInstance> sounds = new ArrayList<>();
    private static final Set<SoundInstance> active = new HashSet<>();
    private static String phase = "ready", target = "", shot = "";
    private static int ticks, received, turnLevel = -1;
    private static float startYaw, maxTurn, startPitch, maxPitch;
    private static double startX, maxPull;
    private static final boolean CAMERA_TURN = Boolean.parseBoolean(System.getProperty("yuji.qa.cameraTurn", "true"));
    private static boolean failed;
    private static boolean enabled() { return Boolean.getBoolean("yuji.qa.isolation"); }
    private static String name() { return Minecraft.getInstance().getUser().getName(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void record(String value) throws Exception {
        Files.writeString(RESULTS.resolve("isolation-" + name() + ".log"), "epochMs=" + System.currentTimeMillis() + " " + value + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private static void pass(String item, String evidence) throws Exception {
        record("PASS " + item + " " + evidence);
        Files.writeString(RESULTS.resolve("client-" + item + "-" + name() + ".pass"), evidence + "\n");
    }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try { Files.writeString(RESULTS.resolve("isolation-" + name() + ".failed"), error.toString()); }
        catch (Exception ignored) { }
    }
    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        if (!enabled() || failed) return;
        String text = event.getMessage().getString();
        if (!text.startsWith("YUJI_ISOLATION:")) return;
        event.setCanceled(true);
        try {
            String[] parts = text.substring("YUJI_ISOLATION:".length()).split(":");
            if (parts[0].equals("start")) {
                phase = parts[1]; target = parts[2]; ticks = 0; received = 0; sounds.clear(); active.clear();
                startYaw = Minecraft.getInstance().player.getYRot(); maxTurn = 0; turnLevel = -1;
                startPitch = Minecraft.getInstance().player.getXRot(); maxPitch = 0;
                startX = Minecraft.getInstance().player.getX(); maxPull = 0;
                if (phase.startsWith("camera")) require(YujiConfig.CAMERA_TURN.get() == CAMERA_TURN,
                        "actual server config synchronized to client: cameraTurn=" + YujiConfig.CAMERA_TURN.get());
                record("START " + phase + " target=" + target + " yaw=" + startYaw);
            } else if (parts[0].equals("check")) {
                boolean affected = name().equals(target);
                if (phase.startsWith("voice")) {
                    require(affected ? !active.isEmpty() : received == 0,
                            "targeted audio isolation: affected=" + affected + " received=" + received + " active=" + active.size());
                    pass(phase, "target=" + target + " received=" + received + " resolvedActive=" + active.size());
                } else if (phase.startsWith("camera")) {
                    if (affected) {
                        require(turnLevel >= 1 && maxPull > .3, "camera option must preserve natural enthrallment and pulling: level=" + turnLevel + "; pull=" + maxPull);
                        require(CAMERA_TURN ? maxTurn > 10 && maxPitch > 1 : maxTurn < .1F && maxPitch < .1F,
                                "configured camera behavior: cameraTurn=" + CAMERA_TURN + " maxTurn=" + maxTurn + " maxPitch=" + maxPitch);
                    } else require(maxTurn < .1F && maxPitch < .1F && turnLevel == -1,
                            "camera isolation: unaffected maxTurn=" + maxTurn + " maxPitch=" + maxPitch + " effectLevel=" + turnLevel);
                    pass(phase, "cameraTurn=" + CAMERA_TURN + " target=" + target + " maxTurn=" + maxTurn + " maxPitch=" + maxPitch + " effectLevel=" + turnLevel + " pull=" + maxPull);
                }
                shot = phase; phase = "";
            } else if (parts[0].equals("habitat")) {
                var mob = Minecraft.getInstance().level.getEntity(Integer.parseInt(parts[2]));
                require(mob instanceof com.zeropointsix.eraser.yuji.YujiMonster, "client received actual Yuji entity");
                require(mob.getZ() >= Double.parseDouble(parts[3]) && mob.getZ() < 13, "client observes entity inside habitat: " + mob.position());
                var box = mob.getBoundingBox();
                require(Math.abs(box.minY - 65) < .01 && box.maxY <= (parts[1].equals("liangshe-indoor") ? 68 : 73),
                        "client sees body below the roof or within corridor wall height");
                require(box.minX >= (parts[1].equals("liangshe-indoor") ? -1 : 0) && box.maxX <= 2
                        && box.minZ >= 6 && box.maxZ <= 13, "client sees entire body between habitat walls: pos=" + mob.position() + "; box=" + box);
                pass(parts[1], "TCP-synchronized entity=" + mob.getUUID() + "; pos=" + mob.position() + "; body=" + mob.getBoundingBox());
                shot = parts[1];
            } else if (parts[0].equals("face")) {
                var mc = Minecraft.getInstance();
                var entity = mc.level.getEntity(Integer.parseInt(parts[2]));
                require(entity != null, "voice body synchronized before looking at it");
                var delta = entity.getEyePosition().subtract(mc.player.getEyePosition());
                mc.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
                mc.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
                pass(parts[1], "real client looking at body=" + entity.getUUID());
            } else if (parts[0].equals("crouch")) {
                Minecraft.getInstance().options.keyShift.setDown(Boolean.parseBoolean(parts[1]));
                record("crouch input=" + parts[1]);
            } else if (parts[0].equals("attack")) {
                var mc = Minecraft.getInstance();
                var entity = mc.level.getEntity(Integer.parseInt(parts[1]));
                require(entity != null && mc.gameMode != null, "attack target synchronized to client");
                mc.gameMode.attack(mc.player, entity); mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                record("real attack packet entity=" + entity.getUUID());
            } else if (parts[0].equals("loot")) {
                var entity = Minecraft.getInstance().level.getEntity(Integer.parseInt(parts[1]));
                require(entity instanceof com.zeropointsix.eraser.yuji.YujiMonster, "thief synchronized to both clients");
                var stack = ((com.zeropointsix.eraser.yuji.YujiMonster) entity).displayedLoot();
                require(stack.is(net.minecraft.world.item.Items.DIAMOND) && stack.getCount() == 37 && stack.hasTag()
                        && stack.getTag().getString("YujiReceipt").equals("a70-real-client-property"), "complete stolen stack synchronized");
                pass("theft-loot", "count/NBT observed in synchronized loot=" + stack.save(new net.minecraft.nbt.CompoundTag()));
            } else if (parts[0].equals("shot")) shot = parts[1];
            else if (parts[0].equals("done")) { pass("isolation", "two real clients completed isolation and terrain probes"); phase = ""; }
        } catch (Throwable error) { fail(error); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void sound(PlaySoundEvent event) {
        if (!enabled() || failed || !phase.startsWith("voice")) return;
        var sound = event.getSound();
        if (sound != null && sound.getSource() == SoundSource.HOSTILE
                && sound.getLocation().getNamespace().equals("minecraft") && VOICES.contains(sound.getLocation().getPath())) {
            received++; sounds.add(sound);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!enabled() || failed || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen screen) {
            fail(new AssertionError(screen.getNarrationMessage().getString())); return;
        }
        if (mc.player == null || mc.level == null) return;
        try {
            ticks++;
            if (ticks == 1) { mc.setScreen(null); mc.mouseHandler.grabMouse(); }
            if (phase.equals("ready") && ticks == 60) { pass("isolation-ready", "real TCP player=" + mc.player.getUUID()); phase = ""; }
            if ((phase.startsWith("voice") || phase.startsWith("camera")) && ticks == 20) pass("armed-" + phase, "listening before server enables natural AI");
            if (phase.startsWith("camera")) {
                maxPull = Math.max(maxPull, startX - mc.player.getX());
                maxTurn = Math.max(maxTurn, Math.abs(Mth.wrapDegrees(mc.player.getYRot() - startYaw)));
                maxPitch = Math.max(maxPitch, Math.abs(mc.player.getXRot() - startPitch));
                var effect = mc.player.getEffect(YujiContent.ENTHRALLED.get());
                if (effect != null) turnLevel = Math.max(turnLevel, effect.getAmplifier());
                if (ticks % 20 == 0) record(phase + " tick=" + ticks + " yaw=" + mc.player.getYRot() + " maxTurn=" + maxTurn + " effectLevel=" + turnLevel);
            }
            for (var sound : sounds) if (!active.contains(sound) && sound.getSound() != null && mc.getSoundManager().isActive(sound)) {
                active.add(sound);
                record(phase + " tick=" + ticks + " event=" + sound.getLocation() + " decoded=" + sound.getSound().getLocation()
                        + " active=true position=" + sound.getX() + "," + sound.getY() + "," + sound.getZ());
            }
        } catch (Throwable error) { fail(error); }
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (!enabled() || failed || shot.isEmpty() || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.screen != null) return;
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(RESULTS.resolve(shot + "-" + name() + ".png"));
            pass("shot-" + shot, "actual client framebuffer"); shot = "";
        } catch (Throwable error) { fail(error); }
    }
}
