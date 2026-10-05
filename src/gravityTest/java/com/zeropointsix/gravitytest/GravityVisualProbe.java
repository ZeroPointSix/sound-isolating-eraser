package com.zeropointsix.gravitytest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Measures the production render pass, without drawing replacement test geometry. */
@Mod.EventBusSubscriber(modid = "gravity_qa", value = Dist.CLIENT)
public final class GravityVisualProbe {
    private static final Path RESULTS = Path.of(System.getProperty("gravity.qa.results"));
    private static final String ROLE = System.getProperty("gravity.qa.role");
    private static final int[][] EDGES = {{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},
            {2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
    private static String pending;
    private static AABB bounds;
    private static boolean fill;
    private static NativeImage before;
    private static NativeImage after;
    private static List<double[]> edges;
    private static Matrix4f cameraView;

    public static void request(String name, AABB box, boolean expectsFill) {
        if (pending != null) throw new AssertionError("Unfinished visual probe " + pending);
        pending = name;
        bounds = box;
        fill = expectsFill;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeWorldOverlay(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            cameraView = new Matrix4f(event.getPoseStack().last().pose());
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || pending == null || before != null) return;
        try {
            before = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
            edges = projectEdges(event, before.getWidth(), before.getHeight());
            System.out.println("GRAVITY_PIXEL_MATRICES " + ROLE + " " + pending
                    + " pose=" + cameraView
                    + " modelView=" + RenderSystem.getModelViewMatrix()
                    + " shaderColor=" + java.util.Arrays.toString(RenderSystem.getShaderColor()));
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterWorldOverlay(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || before == null || after != null) return;
        try {
            after = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void finalFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || after == null) return;
        try (NativeImage frame = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
                NativeImage difference = new NativeImage(after.getWidth(), after.getHeight(), false)) {
            String prefix = ROLE + "-" + pending;
            before.writeToFile(RESULTS.resolve(prefix + "-baseline.png"));
            after.writeToFile(RESULTS.resolve(prefix + "-world.png"));
            frame.writeToFile(RESULTS.resolve(prefix + ".png"));
            int changed = 0, interior = 0, persistent = 0, checkedFinal = 0;
            for (int y = 0; y < after.getHeight(); y++) {
                for (int x = 0; x < after.getWidth(); x++) {
                    int delta = delta(before.getPixelRGBA(x, y), after.getPixelRGBA(x, y));
                    difference.setPixelRGBA(x, y, delta >= 3 ? 0xFFFFFFFF : 0xFF000000);
                    if (delta < 3) continue;
                    changed++;
                    if (edgeDistance(x, y) > 5) interior++;
                    if (unobscured(x, y)) {
                        checkedFinal++;
                        // Vanilla's HUD vignette darkens the composed world. Verify retained
                        // overlay contrast, not byte equality across different render stages.
                        if (positiveDelta(frame.getPixelRGBA(x, y), before.getPixelRGBA(x, y))
                                >= Math.max(3, delta * 0.5)) persistent++;
                    }
                }
            }
            difference.writeToFile(RESULTS.resolve(prefix + "-diff.png"));
            int visibleEdges = 0, testedEdges = 0, finalEdges = 0, testedFinalEdges = 0;
            for (double[] edge : edges) {
                int visible = 0, samples = 0, visibleFinal = 0, samplesFinal = 0;
                for (int i = 1; i < 40; i++) {
                    double t = i / 40D;
                    int x = (int) Math.round(edge[0] + (edge[2] - edge[0]) * t);
                    int y = (int) Math.round(edge[1] + (edge[3] - edge[1]) * t);
                    if (x < 4 || x >= after.getWidth() - 4 || y < 4 || y >= after.getHeight() - 4) continue;
                    samples++;
                    boolean hit = false, hitFinal = false;
                    for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
                        if (delta(before.getPixelRGBA(x + dx, y + dy), after.getPixelRGBA(x + dx, y + dy)) >= (fill ? 40 : 8)) hit = true;
                        if (positiveDelta(frame.getPixelRGBA(x + dx, y + dy), before.getPixelRGBA(x + dx, y + dy)) >= (fill ? 40 : 8)) hitFinal = true;
                    }
                    if (hit) visible++;
                    if (unobscured(x, y)) {
                        samplesFinal++;
                        if (hitFinal) visibleFinal++;
                    }
                }
                if (samples >= 15) {
                    testedEdges++;
                    if (visible >= samples * 0.55) visibleEdges++;
                }
                if (samplesFinal >= 15) {
                    testedFinalEdges++;
                    if (visibleFinal >= samplesFinal * 0.55) finalEdges++;
                }
            }
            String result = "GRAVITY_PIXEL_ASSERT role=" + ROLE + " phase=" + pending
                    + " changed=" + changed + " interior=" + interior + " edges=" + visibleEdges + "/" + testedEdges
                    + " finalEdges=" + finalEdges + "/" + testedFinalEdges
                    + " retainedContrast=" + persistent + "/" + checkedFinal;
            System.out.println(result);
            Files.writeString(RESULTS.resolve(prefix + "-pixels.log"), result + "\n");
            check(testedEdges >= 6 && visibleEdges >= 6, "at least six projected world-space edges must be visible");
            check(!fill || interior >= 2000, "preview must contain a translucent face, not just entity outlines");
            check(testedFinalEdges >= 6 && finalEdges >= 6, "at least six world-space edges must remain in the final screenshot");
            check(checkedFinal >= 100 && persistent >= checkedFinal * 0.85, "world overlay must survive final frame composition");
            Files.writeString(RESULTS.resolve(prefix + "-pixels.pass"), result + "\n");
        } catch (Throwable failure) { fail(failure); }
        finally { close(); }
    }

    private static List<double[]> projectEdges(RenderLevelStageEvent event, int width, int height) {
        Vec3 camera = event.getCamera().getPosition();
        double[][] points = new double[8][];
        for (int i = 0; i < 8; i++) {
            Vector4f point = new Vector4f((float) (((i & 1) == 0 ? bounds.minX : bounds.maxX) - camera.x),
                    (float) (((i & 2) == 0 ? bounds.minY : bounds.maxY) - camera.y),
                    (float) (((i & 4) == 0 ? bounds.minZ : bounds.maxZ) - camera.z), 1);
            point.mul(cameraView).mul(event.getProjectionMatrix());
            if (point.w > 0) points[i] = new double[]{(point.x / point.w + 1) * width / 2,
                    (1 - point.y / point.w) * height / 2};
        }
        List<double[]> result = new ArrayList<>();
        for (int[] edge : EDGES) {
            double[] a = points[edge[0]], b = points[edge[1]];
            if (a != null && b != null) result.add(new double[]{a[0], a[1], b[0], b[1]});
        }
        return result;
    }

    private static double edgeDistance(double x, double y) {
        double nearest = Double.POSITIVE_INFINITY;
        for (double[] edge : edges) {
            double dx = edge[2] - edge[0], dy = edge[3] - edge[1];
            double t = Math.max(0, Math.min(1, ((x - edge[0]) * dx + (y - edge[1]) * dy) / (dx * dx + dy * dy)));
            nearest = Math.min(nearest, Math.hypot(x - edge[0] - t * dx, y - edge[1] - t * dy));
        }
        return nearest;
    }

    private static int delta(int a, int b) {
        return Math.max(Math.abs((a & 255) - (b & 255)), Math.max(Math.abs((a >> 8 & 255) - (b >> 8 & 255)),
                Math.abs((a >> 16 & 255) - (b >> 16 & 255))));
    }

    private static int positiveDelta(int a, int b) {
        return Math.max((a & 255) - (b & 255), Math.max((a >> 8 & 255) - (b >> 8 & 255),
                (a >> 16 & 255) - (b >> 16 & 255)));
    }

    private static boolean unobscured(int x, int y) {
        // HUD/chat occupy the bottom; the login toast is in the top right.
        return y < after.getHeight() * 0.78
                && !(x > after.getWidth() / 2 && y < after.getHeight() * 0.4);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void fail(Throwable failure) {
        failure.printStackTrace();
        try { Files.writeString(RESULTS.resolve(ROLE + "-pixels.failed"), failure.toString()); }
        catch (Exception ignored) { }
        close();
    }

    private static void close() {
        if (before != null) before.close();
        if (after != null) after.close();
        before = null;
        after = null;
        pending = null;
    }

    private GravityVisualProbe() { }
}
