package com.zeropointsix.yujitest;

import com.mojang.blaze3d.platform.NativeImage;
import com.zeropointsix.eraser.yuji.flare.FallingSupplyCrate;
import com.zeropointsix.eraser.yuji.flare.FlareContent;
import com.zeropointsix.eraser.yuji.flare.FlareDecoy;
import com.zeropointsix.eraser.yuji.flare.FlareProjectile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
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
    private static int ringStableFrames, ringDiagnosticTick;
    private static long previousFrame;
    private static boolean failed, complete;
    private static boolean classifierChecked;

    private record RingPixels(int pixels, int sectors, int outside, int angles, int maxGap, double radius) {
        boolean visible() { return pixels >= Math.max(96, Math.ceil(2 * Math.PI * radius)) && sectors == 32
                && outside <= pixels / 4 && angles >= 354 && maxGap <= 2; }
        String evidence() { return "ringPixels=" + pixels + " ringSectors=" + sectors + " ringOutside=" + outside
                + " ringAngles=" + angles + " ringMaxGap=" + maxGap
                + " projectedRadius=" + radius + " ringStableFrames=" + ringStableFrames; }
    }

    private static boolean redPixel(int pixel) {
        int red = pixel & 255, green = pixel >>> 8 & 255, blue = pixel >>> 16 & 255;
        return red >= 100 && red >= green * 1.6 && red >= blue * 1.5;
    }

    // Inspect the actual framebuffer around the projected gameplay boundary, not QA-drawn geometry.
    private static RingPixels ringPixels(NativeImage shot, double radius) {
        if (!Double.isFinite(radius) || radius < 4 || radius >= Math.min(shot.getWidth(), shot.getHeight()) / 2. - 8)
            return new RingPixels(0, 0, 0, 0, 360, radius);
        int[] sectors = new int[32]; int pixels = 0, outside = 0;
        double tolerance = Math.max(3, radius * .025);
        for (int y = 0; y < shot.getHeight(); y++) for (int x = 0; x < shot.getWidth(); x++) {
            double dx = x + .5 - shot.getWidth() / 2., dy = y + .5 - shot.getHeight() / 2.;
            double distance = Math.hypot(dx, dy);
            if (distance < radius * .5 || distance > radius * 1.3) continue;
            if (!redPixel(shot.getPixelRGBA(x, y))) continue;
            if (Math.abs(distance - radius) <= tolerance) {
                pixels++;
                int sector = Math.min(31, (int) ((Math.atan2(dy, dx) + Math.PI) * 32 / (Math.PI * 2)));
                sectors[sector]++;
            } else outside++;
        }
        int occupied = 0;
        for (int count : sectors) if (count >= 3) occupied++;
        boolean[] hits = new boolean[360]; int angles = 0;
        for (int i = 0; i < hits.length; i++) {
            double angle = Math.PI * 2 * i / hits.length;
            for (double offset = -tolerance; offset <= tolerance; offset += .5) {
                int x = (int) Math.floor(shot.getWidth() / 2. + Math.cos(angle) * (radius + offset));
                int y = (int) Math.floor(shot.getHeight() / 2. + Math.sin(angle) * (radius + offset));
                if (x >= 0 && x < shot.getWidth() && y >= 0 && y < shot.getHeight()
                        && redPixel(shot.getPixelRGBA(x, y))) { hits[i] = true; angles++; break; }
            }
        }
        int gap = 0, maxGap = 0;
        for (int i = 0; i < hits.length * 2; i++) {
            gap = hits[i % hits.length] ? 0 : gap + 1;
            maxGap = Math.max(maxGap, Math.min(gap, hits.length));
        }
        return new RingPixels(pixels, occupied, outside, angles, maxGap, radius);
    }

    private static void verifyRingClassifier() {
        String[] cases = {"complete", "blank", "center", "half", "wrong-radius", "solid-red", "yellow", "green", "dotted", "broken"};
        for (String sample : cases) try (var image = new NativeImage(256, 256, false)) {
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
                double distance = Math.hypot(x + .5 - 128, y + .5 - 128);
                double angle = Math.atan2(y + .5 - 128, x + .5 - 128);
                boolean ring = Math.abs(distance - (sample.equals("wrong-radius") ? 48 : 80)) <= 1.5;
                boolean lit = switch (sample) {
                    case "blank" -> false;
                    case "center" -> distance < 10;
                    case "half" -> ring && x < 128;
                    case "dotted" -> ring && Math.cos(angle * 32) > .97;
                    case "broken" -> ring && Math.abs(angle) > Math.toRadians(5);
                    case "solid-red" -> true;
                    default -> ring;
                };
                int color = sample.equals("yellow") ? 0xFF00FFFF : sample.equals("green") ? 0xFF00FF00 : 0xFF0000FF;
                image.setPixelRGBA(x, y, lit ? color : 0xFF000000);
            }
            if (ringPixels(image, 80).visible() != sample.equals("complete"))
                throw new AssertionError("red-ring pixel classifier failed control: " + sample);
        }
        classifierChecked = true;
        System.out.println("FLARE_RING_PIXEL_CONTROLS_PASSED positive=1 negative=9");
    }

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
            ringStableFrames = 0; ringDiagnosticTick = 0;
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
            if (!classifierChecked) verifyRingClassifier();
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
                        || phase.equals("green-descent") && entity instanceof FallingSupplyCrate
                        || phase.equals("red-radius") && entity instanceof FlareDecoy
                        && entity.position().distanceToSqr(target) < 4) { subject = entity; break; }
            }
            if (phase.equals("red-radius")) {
                mc.options.fov().set(70); mc.options.fovEffectScale().set(0.); mc.options.bobView().set(false);
                mc.options.particles().set(ParticleStatus.MINIMAL);
                mc.player.setYRot(0); mc.player.setXRot(90);
                if (ticks > 240 && !captured.contains(phase)) throw new AssertionError("complete red ring never became visible");
            } else {
                mc.options.particles().set(ParticleStatus.ALL);
                if (subject != null) target = subject.position().add(0, .4, 0);
                look(target);
            }
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
                RingPixels ring = null;
                if (phase.equals("red-radius")) {
                    Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
                    double height = camera.y - target.y;
                    boolean framed = subject instanceof FlareDecoy decoy && decoy.tauntRadius() == 24
                            && Math.hypot(camera.x - target.x, camera.z - target.z) < .05
                            && height > 40 && Math.abs(mc.player.getXRot() - 90) < .05
                            && mc.options.getEffectiveRenderDistance() >= 6;
                    double projected = 24 * shot.getHeight() / (2 * height * Math.tan(Math.toRadians(35)));
                    ring = ringPixels(shot, projected);
                    if (movie) {
                        ringStableFrames = framed && ring.visible() ? ringStableFrames + 1 : 0;
                        Files.writeString(RESULTS.resolve("visual-ring-pixels.log"), "ticks=" + ticks + " framed=" + framed
                                + " effectiveRenderDistance=" + mc.options.getEffectiveRenderDistance()
                                + " " + ring.evidence() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        if (ticks - ringDiagnosticTick >= 20) {
                            shot.writeToFile(RESULTS.resolve("visual-red-radius-diagnostic.png")); ringDiagnosticTick = ticks;
                        }
                    }
                    screenshot &= framed && ring.visible() && ringStableFrames >= 5 && ticks >= 20;
                }
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
                                    + " night=" + mc.level.getDayTime() + " camera=" + mc.player.position() + " target=" + target
                                    + (ring == null ? "" : " radius=24 particles=MINIMAL " + ring.evidence()) + "\n");
                    captured.add(phase);
                    System.out.println("FLARE_VISUAL_CAPTURE " + phase + " blockLight=" + brightness
                            + (ring == null ? "" : " radius=24 " + ring.evidence()));
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
