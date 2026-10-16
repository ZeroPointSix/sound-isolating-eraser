package com.zeropointsix.keepertest;

import com.zeropointsix.eraser.registry.ModEffects;
import com.zeropointsix.eraser.registry.ModItems;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "keeper_qa", value = Dist.CLIENT)
public final class KeeperTestClient {
    private static final Path RESULTS = Path.of(System.getProperty("keeper.qa.results"));
    private static String phase = "";
    private static int ticks;
    private static boolean failed;
    private static boolean baselineSaved;
    private static int[] baselineRgb = new int[3];
    private static int baselineWhite;
    private static int bestFlashWhite;
    private static volatile boolean flashSaved;
    private static boolean photoSaved;
    private static boolean grabberStarted;

    @SubscribeEvent
    public static void message(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("KEEPER_QA:")) {
            phase = text.substring(10);
            ticks = 0;
            event.setCanceled(true);
        }
    }

    private static void input(String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("xdotool");
        command.addAll(java.util.List.of(args));
        if (new ProcessBuilder(command).start().waitFor() != 0) {
            throw new AssertionError("native input failed: " + String.join(" ", args));
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static int[] rgb(int packed) {
        return new int[] { (packed >> 16) & 255, (packed >> 8) & 255, packed & 255 };
    }

    private static BufferedImage grab(String name) throws Exception {
        Path file = RESULTS.resolve(name);
        var command = new java.util.ArrayList<String>();
        command.add("/usr/bin/import");
        String display = System.getenv("DISPLAY");
        if (display != null && !display.isEmpty()) {
            command.add("-display");
            command.add(display);
        }
        command.add("-silent");
        command.add("-window");
        command.add("root");
        command.add(file.toAbsolutePath().toString());
        int code = new ProcessBuilder(command).start().waitFor();
        require(code == 0, "X11 import failed for " + name + " code=" + code);
        BufferedImage image = ImageIO.read(file.toFile());
        require(image != null, "X11 import produced unreadable " + name);
        return image;
    }

    private static int countWhite(BufferedImage image, int min) {
        int n = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int[] c = rgb(image.getRGB(x, y));
                if (c[0] >= min && c[1] >= min && c[2] >= min) n++;
            }
        }
        return n;
    }

    private static int[] meanCenter(BufferedImage image) {
        int x0 = image.getWidth() / 4, x1 = image.getWidth() * 3 / 4;
        int y0 = image.getHeight() / 4, y1 = image.getHeight() * 3 / 4;
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int[] c = rgb(image.getRGB(x, y));
                r += c[0]; g += c[1]; b += c[2]; n++;
            }
        }
        n = Math.max(1, n);
        return new int[] { (int) (r / n), (int) (g / n), (int) (b / n) };
    }

    private static int countNear(BufferedImage image, int target, int tol, int y0, int y1) {
        int[] t = rgb(target);
        int n = 0;
        int x0 = image.getWidth() / 4, x1 = image.getWidth() * 3 / 4;
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y++) {
            for (int x = x0; x < x1; x++) {
                int[] c = rgb(image.getRGB(x, y));
                if (Math.abs(c[0] - t[0]) + Math.abs(c[1] - t[1]) + Math.abs(c[2] - t[2]) <= tol) n++;
            }
        }
        return n;
    }

    private static boolean inPhotoWorld() {
        var player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(ModEffects.IN_PHOTO.get());
    }

    private static boolean acceptFlash(BufferedImage screenshot, int index) throws Exception {
        if (inPhotoWorld()) return false;
        if (phase.equals("enter") || phase.equals("photo") || phase.equals("done")) return false;
        int white = countWhite(screenshot, 200);
        int[] mean = meanCenter(screenshot);
        boolean grey = Math.abs(mean[0] - mean[1]) <= 24 && Math.abs(mean[1] - mean[2]) <= 24;
        boolean sepiaLike = mean[0] > mean[2] + 12;
        if (white >= bestFlashWhite && !sepiaLike) {
            bestFlashWhite = white;
            ImageIO.write(screenshot, "png", RESULTS.resolve("flash.png").toFile());
        }
        Files.writeString(RESULTS.resolve("flash-grab.txt"),
                "frame=" + index + " phase=" + phase + " white=" + white
                        + " mean=" + mean[0] + "," + mean[1] + "," + mean[2]
                        + " grey=" + grey + " sepiaLike=" + sepiaLike + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        int pixels = Math.max(1, screenshot.getWidth() * screenshot.getHeight());
        boolean lum = mean[0] >= 150 && mean[1] >= 150 && mean[2] >= 150 && grey;
        boolean cover = (double) white / pixels >= 0.18 && grey;
        if (!(lum || cover)) return false;
        Files.writeString(RESULTS.resolve("flash-stats.txt"),
                "bestWhite=" + bestFlashWhite + " baselineWhite=" + baselineWhite
                        + " mean=" + mean[0] + "," + mean[1] + "," + mean[2]
                        + " frame=" + index + " pixels=" + pixels + " lum=" + lum + " cover=" + cover + System.lineSeparator());
        Files.writeString(RESULTS.resolve("flash.pass"),
                "Real camera use produced a white shutter overlay on the X11 window. bestWhite=" + bestFlashWhite
                        + " mean=" + mean[0] + "," + mean[1] + "," + mean[2] + " frame=" + index + System.lineSeparator());
        flashSaved = true;
        return true;
    }

    private static void startFlashGrabber() {
        if (grabberStarted) return;
        grabberStarted = true;
        Thread thread = new Thread(() -> {
            for (int i = 0; i < 80 && !flashSaved && !failed; i++) {
                if (inPhotoWorld() || phase.equals("enter") || phase.equals("photo")) break;
                try {
                    BufferedImage screenshot = grab("flash-live-" + i + ".png");
                    acceptFlash(screenshot, i);
                    Thread.sleep(40);
                } catch (Throwable error) {
                    try {
                        Files.writeString(RESULTS.resolve("flash-grab.txt"),
                                "error=" + error + System.lineSeparator(),
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (Exception ignored) { }
                    try { Thread.sleep(40); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
            }
        }, "keeper-qa-flash-grab");
        thread.setDaemon(true);
        thread.start();
    }

    private static void captureBaseline(String tag) throws Exception {
        if (baselineSaved) return;
        BufferedImage screenshot = grab("baseline.png");
        baselineWhite = countWhite(screenshot, 200);
        baselineRgb = meanCenter(screenshot);
        Files.writeString(RESULTS.resolve("baseline-stats.txt"),
                "tag=" + tag + " white=" + baselineWhite + " rgb=" + baselineRgb[0] + "," + baselineRgb[1] + "," + baselineRgb[2]
                        + " size=" + screenshot.getWidth() + "x" + screenshot.getHeight() + System.lineSeparator());
        baselineSaved = true;
    }

    private static void prepare(Minecraft mc) {
        mc.setScreen(null);
        GLFW.glfwFocusWindow(mc.getWindow().getWindow());
        mc.mouseHandler.grabMouse();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || phase.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            ticks++;
            if (ticks <= 8) prepare(mc);
            if (!baselineSaved && ticks == 12 && (phase.equals("baseline") || phase.equals("shoot"))) {
                captureBaseline(phase + "@" + ticks);
            }
            if (phase.equals("shoot")) {
                if (ticks == 4) startFlashGrabber();
                if (ticks == 8) input("key", "1");
                if (ticks == 20) input("click", "3");
            }
            if (phase.equals("enter")) {
                if (ticks == 8) input("key", "2");
                if (ticks == 16) input("mousedown", "3");
                if (ticks == 42) input("mouseup", "3");
            }
            if (phase.equals("photo") && ticks == 16 && !photoSaved) {
                require(mc.player.hasEffect(ModEffects.IN_PHOTO.get()), "client has the in-photo effect");
                BufferedImage screenshot = grab("photo.png");
                ImageIO.write(screenshot, "png", RESULTS.resolve("hud.png").toFile());
                int[] mean = meanCenter(screenshot);
                int h = screenshot.getHeight();
                int hud = countNear(screenshot, 0x5A4632, 48, h * 200 / 256, h);
                int brown = 0;
                int x0 = screenshot.getWidth() / 4, x1 = screenshot.getWidth() * 3 / 4;
                int y0 = screenshot.getHeight() / 4, y1 = screenshot.getHeight() * 3 / 4;
                int area = Math.max(1, (x1 - x0) * (y1 - y0));
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int[] c = rgb(screenshot.getRGB(x, y));
                        if (c[0] > c[2] + 8 && c[0] >= c[1] - 6) brown++;
                    }
                }
                Files.writeString(RESULTS.resolve("photo-stats.txt"),
                        "rgb=" + mean[0] + "," + mean[1] + "," + mean[2]
                                + " baseline=" + baselineRgb[0] + "," + baselineRgb[1] + "," + baselineRgb[2]
                                + " hud=" + hud + " brown=" + brown + "/" + area
                                + " photoSelected=" + mc.player.getInventory().getSelected().is(ModItems.KEEPER_PHOTO.get()) + System.lineSeparator());
                boolean sepia = (mean[0] > mean[2] + 8 && mean[0] >= mean[1] - 8)
                        || (brown * 3 > area)
                        || (baselineSaved && mean[0] - baselineRgb[0] >= 8 && mean[2] <= baselineRgb[2] + 4);
                require(sepia, "photo world is not brown/sepia relative to baseline: "
                        + mean[0] + "," + mean[1] + "," + mean[2]);
                Files.writeString(RESULTS.resolve("sepia.pass"),
                        "Photo-world X11 pixels shifted brown/sepia. rgb=" + mean[0] + "," + mean[1] + "," + mean[2] + System.lineSeparator());
                require(hud > 20, "polaroid HUD remaining-time color 0x5A4632 was not rendered");
                Files.writeString(RESULTS.resolve("hud.pass"),
                        "Polaroid frame HUD timer color was present. pixels=" + hud + System.lineSeparator());
                photoSaved = true;
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve("client.failed"), failure.toString()); } catch (Exception ignored) { }
            failed = true;
        }
    }
}
