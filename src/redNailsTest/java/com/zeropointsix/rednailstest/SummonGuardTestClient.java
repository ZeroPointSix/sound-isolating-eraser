package com.zeropointsix.rednailstest;

import com.zeropointsix.eraser.client.RedNailsClient;
import com.zeropointsix.eraser.rednails.WaterGhostSummonEntity;
import com.zeropointsix.eraser.suxia.SuxiaSummonEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "red_nails_qa", value = Dist.CLIENT)
public final class SummonGuardTestClient {
    private static final boolean ENABLED = Boolean.getBoolean("rednails.qa.guard");
    private static final Path RESULTS = Path.of(System.getProperty("rednails.qa.results"));
    private static String phase = "";
    private static int ticks, frame, guardianFrame = -1, lastCaptureTick = -1, imageNumber;
    private static boolean failed, captured;
    private static ArmorStand camera;

    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        if (!ENABLED || !event.getMessage().getString().startsWith("SUMMON_GUARD_QA:")) return;
        phase = event.getMessage().getString().substring("SUMMON_GUARD_QA:".length());
        ticks = 0; lastCaptureTick = -1; captured = false; RedNailsClient.KEY.setDown(false); event.setCanceled(true);
    }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try { Files.writeString(RESULTS.resolve("client.failed"), error.toString()); } catch (Exception ignored) { }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || failed || phase.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            ticks++; mc.setScreen(null); mc.options.hideGui = true;
            if (phase.equals("suxia-fight") && ticks == 2) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            if (phase.equals("water-fight")) {
                if (ticks == 2 || ticks == 42 || ticks == 82 || ticks == 122) RedNailsClient.KEY.setDown(true);
                if (ticks == 32 || ticks == 72 || ticks == 112 || ticks == 152) RedNailsClient.KEY.setDown(false);
            }
            Vec3 center = new Vec3(0, 71, 1), at = new Vec3(15, 83, -18);
            if (camera == null) camera = new ArmorStand(mc.level, at.x, at.y, at.z);
            camera.setPos(at); camera.xo = at.x; camera.yo = at.y; camera.zo = at.z;
            Vec3 toward = center.subtract(camera.getEyePosition());
            camera.setYRot((float) Math.toDegrees(Math.atan2(toward.z, toward.x)) - 90);
            camera.setXRot((float) -Math.toDegrees(Math.atan2(toward.y, toward.horizontalDistance())));
            camera.yRotO = camera.getYRot(); camera.xRotO = camera.getXRot(); mc.setCameraEntity(camera);
        } catch (Throwable error) { fail(error); }
    }
    @SubscribeEvent public static void model(RenderLivingEvent.Post<?, ?> event) {
        if (ENABLED && (event.getEntity() instanceof SuxiaSummonEntity || event.getEntity() instanceof WaterGhostSummonEntity)) guardianFrame = frame;
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED) return;
        if (event.phase == TickEvent.Phase.START) { frame++; return; }
        if (failed || phase.isEmpty() || ticks < 15 || ticks % 5 != 0 || lastCaptureTick == ticks) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            lastCaptureTick = ticks;
            var colors = new HashSet<Integer>();
            for (int x = 0; x < image.getWidth(); x += 4) for (int y = 0; y < image.getHeight(); y += 4) colors.add(image.getPixelRGBA(x, y));
            if (colors.size() < 100) throw new AssertionError("Blank real OpenGL frame");
            Files.createDirectories(RESULTS.resolve("frames"));
            image.writeToFile(RESULTS.resolve(String.format(java.util.Locale.ROOT, "frames/%05d.png", imageNumber++)));
            boolean fighting = phase.endsWith("-fight");
            if (!captured && (!fighting || ticks >= 40 && guardianFrame == frame)) {
                image.writeToFile(RESULTS.resolve(phase + ".png"));
                Files.writeString(RESULTS.resolve("client-" + phase + ".pass"),
                        "Actual OpenGL framebuffer; frame=" + frame + "; distinctColors=" + colors.size() + "; guardianRendered=" + (guardianFrame == frame) + "\n");
                captured = true;
            }
        } catch (Throwable error) { fail(error); }
    }
}
