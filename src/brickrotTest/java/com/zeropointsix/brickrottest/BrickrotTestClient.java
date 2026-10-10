package com.zeropointsix.brickrottest;

import com.mojang.blaze3d.platform.NativeImage;
import com.zeropointsix.eraser.brickrot.BrickrotWallEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "brickrot_qa", value = Dist.CLIENT)
public final class BrickrotTestClient {
    private static final Path RESULTS = Path.of(System.getProperty("brickrot.qa.results"));
    private static final String ROLE = System.getProperty("brickrot.qa.role");
    private static String phase = "";
    private static int ticks;
    private static boolean failed, captured;
    private static NativeImage firstFrame;

    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();
        if (!message.startsWith("BRICKROT_QA:")) return;
        phase = message.substring("BRICKROT_QA:".length());
        ticks = 0;
        captured = false;
        if (firstFrame != null) firstFrame.close();
        firstFrame = null;
        event.setCanceled(true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void fail(Throwable error) {
        error.printStackTrace();
        try { Files.writeString(RESULTS.resolve(ROLE + ".failed"), error.toString()); } catch (Exception ignored) { }
        if (firstFrame != null) firstFrame.close();
        firstFrame = null;
        failed = true;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || phase.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            ticks++;
            if (ticks == 1) {
                mc.setScreen(null);
                GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.mouseHandler.grabMouse();
            }
            if (ROLE.equals("user") && ticks == 40 && (phase.equals("spawn") || phase.equals("attack"))) {
                require(new ProcessBuilder("xdotool", "click", phase.equals("spawn") ? "3" : "1")
                        .start().waitFor() == 0, "native mouse input failed");
            }
        } catch (Throwable error) { fail(error); }
    }

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || captured || ticks < 50
                || !(phase.equals("intact") || phase.equals("breached") || phase.equals("restarted"))) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        try {
            var heads = mc.level.getEntitiesOfClass(BrickrotWallEntity.class, mc.player.getBoundingBox().inflate(64));
            require(heads.size() == 1, "client sees exactly one parent");
            var head = heads.get(0);
            require(head.getParts().length == 9, "nine client multipart instances");
            require(head.phaseTwo() == !phase.equals("intact"), "phase synchronized to both clients");
            require(head.getParts()[0].modelName().equals(phase.equals("intact")
                    ? "brickrot_neck" : "brickrot_neck_breached"), "correct original neck model selected");
            if (firstFrame == null) {
                firstFrame = Screenshot.takeScreenshot(mc.getMainRenderTarget());
                firstFrame.writeToFile(RESULTS.resolve(ROLE + "-" + phase + "-first.png"));
                return;
            }
            if (ticks < 75) return;
            try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                screenshot.writeToFile(RESULTS.resolve(ROLE + "-" + phase + ".png"));
                var colors = new HashSet<Integer>();
                int redPixels = 0, changedBodyPixels = 0;
                for (int x = 0; x < screenshot.getWidth(); x++) for (int y = 0; y < screenshot.getHeight(); y++) {
                    int pixel = screenshot.getPixelRGBA(x, y);
                    if (x % 8 == 0 && y % 8 == 0) colors.add(pixel);
                    int r = pixel & 255, g = (pixel >>> 8) & 255, b = (pixel >>> 16) & 255;
                    if (r > 60 && r > g * 1.35 && r > b * 1.2) {
                        redPixels++;
                        if (pixel != firstFrame.getPixelRGBA(x, y)) changedBodyPixels++;
                    }
                }
                require(colors.size() > 100 && redPixels > 400, "textured brick body is visible in native framebuffer");
                require(changedBodyPixels > 30, "supplied animation visibly moves between real rendered frames");
                Files.writeString(RESULTS.resolve(ROLE + "-" + phase + ".pass"),
                        "Native Forge client: parent + nine parts, model/phase sync, textured framebuffer and motion passed.\n"
                        + "redPixels=" + redPixels + "; changedBodyPixels=" + changedBodyPixels + "\n");
            }
            firstFrame.close();
            firstFrame = null;
            captured = true;
        } catch (Throwable error) { fail(error); }
    }
}
