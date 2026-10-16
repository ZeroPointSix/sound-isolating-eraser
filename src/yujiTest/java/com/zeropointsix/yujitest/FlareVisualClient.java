package com.zeropointsix.yujitest;

import com.zeropointsix.eraser.yuji.flare.FallingSupplyCrate;
import com.zeropointsix.eraser.yuji.flare.FlareContent;
import com.zeropointsix.eraser.yuji.flare.FlareProjectile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "yuji_qa", value = Dist.CLIENT)
public final class FlareVisualClient {
    private static final Path RESULTS = Path.of(System.getProperty("yuji.qa.results"));
    private static final Set<String> EXPECTED = Set.of("red-flight", "red-activation", "red-radius", "yellow-flight",
            "yellow-activation", "yellow-night-light", "green-flight", "green-activation", "green-descent", "green-landed");
    private static final Set<String> captured = new HashSet<>(), fired = new HashSet<>();
    private static String phase = "", color = "";
    private static Vec3 target;
    private static Entity subject;
    private static int ticks, frames, movieFrames;
    private static long previousFrame;
    private static boolean failed, complete;

    private static boolean enabled() { return Boolean.getBoolean("flare.qa.visuals"); }
    private static void fail(Throwable failure) {
        failed = true; failure.printStackTrace();
        try { Files.writeString(RESULTS.resolve("client-visuals.failed"), failure.toString()); } catch (Exception ignored) { }
    }
    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        if (!enabled() || !event.getMessage().getString().startsWith("FLARE_VISUALS:")) return;
        try {
            String[] fields = event.getMessage().getString().split(":");
            phase = fields[1]; color = phase.substring(0, phase.indexOf('-')); ticks = 0; frames = 0;
            target = new Vec3(Double.parseDouble(fields[2]), Double.parseDouble(fields[3]), Double.parseDouble(fields[4]));
            event.setCanceled(true);
        } catch (Throwable failure) { fail(failure); }
    }
    private static void look(Vec3 point) {
        var player = Minecraft.getInstance().player;
        Vec3 delta = point.subtract(player.getEyePosition());
        player.setYRot((float) (Mth.atan2(delta.z, delta.x) * 180 / Math.PI) - 90);
        player.setXRot((float) -(Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * 180 / Math.PI));
    }
    private static int colorId() { return color.equals("red") ? 0 : color.equals("yellow") ? 1 : 2; }
    private static int blockLight(BlockPos center) {
        int brightness = 0;
        var level = Minecraft.getInstance().level;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3)))
            brightness = Math.max(brightness, level.getBrightness(LightLayer.BLOCK, pos));
        return brightness;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!enabled() || failed || complete || phase.isEmpty() || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameMode == null) return;
        try {
            ticks++;
            mc.player.setDeltaMovement(Vec3.ZERO);
            if (mc.player.getAbilities().mayfly) mc.player.getAbilities().flying = true;
            if (phase.endsWith("-armed")) {
                mc.player.setYRot(0); mc.player.setXRot(-65);
                if (ticks >= 60 && !fired.contains(color) && mc.screen == null
                        && mc.player.getMainHandItem().is(FlareContent.GUN.get())
                        && mc.player.getOffhandItem().is(FlareContent.ammunition(colorId()))) {
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    mc.player.swing(InteractionHand.MAIN_HAND); fired.add(color);
                }
                return;
            }
            subject = null;
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (phase.endsWith("-flight") && entity instanceof FlareProjectile projectile && projectile.color() == colorId()
                        || phase.equals("green-descent") && entity instanceof FallingSupplyCrate) { subject = entity; break; }
            }
            if (subject != null) target = subject.position().add(0, .4, 0);
            look(target);
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (!enabled() || failed || complete || phase.isEmpty() || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || phase.endsWith("-armed")) return;
        try {
            frames++;
            boolean flight = phase.endsWith("-flight"), activation = phase.endsWith("-activation");
            int brightness = blockLight(BlockPos.containing(target));
            boolean ready = activation ? ticks >= 1 && frames >= 2 : ticks >= 8 && frames >= 4;
            if (flight) ready &= subject != null && mc.player.hasLineOfSight(subject) && brightness >= 12;
            if (phase.equals("green-descent")) ready &= subject != null && mc.player.hasLineOfSight(subject);
            if (phase.equals("green-landed")) ready &= mc.level.getBlockState(BlockPos.containing(target)).is(FlareContent.CRATE.get());
            if (phase.equals("yellow-night-light")) ready &= brightness >= 12;
            long now = System.nanoTime();
            boolean movie = now - previousFrame >= 100_000_000L;
            boolean screenshot = ready && EXPECTED.contains(phase) && !captured.contains(phase);
            if (!movie && !screenshot) return;
            try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                if (movie) {
                    Path directory = RESULTS.resolve("visual-frames"); Files.createDirectories(directory);
                    String name = String.format(java.util.Locale.ROOT, "frame-%06d.png", movieFrames++);
                    shot.writeToFile(directory.resolve(name));
                    Files.writeString(RESULTS.resolve("visual-frames.tsv"), name + "\t" + now + "\t" + phase + "\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    previousFrame = now;
                }
                if (screenshot) {
                    shot.writeToFile(RESULTS.resolve("visual-" + phase + ".png"));
                    Files.writeString(RESULTS.resolve("client-visual-" + phase + ".pass"),
                            "phase=" + phase + " frames=" + frames + " ticks=" + ticks + " blockLight=" + brightness
                                    + " night=" + mc.level.getDayTime() + " camera=" + mc.player.position() + " target=" + target + "\n");
                    captured.add(phase);
                    System.out.println("FLARE_VISUAL_CAPTURE " + phase + " blockLight=" + brightness);
                }
            }
            if (captured.containsAll(EXPECTED)) {
                if (!fired.containsAll(Set.of("red", "yellow", "green")) || movieFrames < 20)
                    throw new AssertionError("all three real shots and at least twenty actual movie frames are required");
                Files.writeString(RESULTS.resolve("client-visuals.pass"), "actual useItem shots=3 screenshots=" + captured.size()
                        + " nativeMovieFrames=" + movieFrames + " measuredFlightBlockLight>=12\n");
                complete = true;
            }
        } catch (Throwable failure) { fail(failure); }
    }
}
