package com.zeropointsix.keepertest;

import com.zeropointsix.eraser.registry.ModEffects;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.keeper.client.KeeperVisuals;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.HexFormat;
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
    private static volatile String phase = "";
    private static int ticks;
    private static volatile boolean failed;
    private static boolean baselineSaved;
    private static int[] baselineRgb = new int[3];
    private static int baselineWhite;
    private static boolean photoSaved;
    private static boolean grabberStarted;
    private static final StringBuilder frameTimes = new StringBuilder("epoch_ms,elapsed_ms,left_ms,phase,in_photo\n");
    private static Field flashDeadline;
    private static volatile long observedDeadline;
    private static boolean timingSaved;
    private static int positiveFrames;
    private static double firstPositive = -1, lastPositive = -1, firstClear = -1;
    private static int photoSeconds;

    private static void provenance() throws Exception {
        StringBuilder output = new StringBuilder();
        for (Class<?> type : new Class<?>[] {KeeperVisuals.class, KeeperVisuals.Registration.class}) {
            String name = "/" + type.getName().replace('.', '/') + ".class";
            try (var stream = type.getResourceAsStream(name)) {
                require(stream != null, "loaded class bytes are accessible");
                output.append(type.getName()).append("\nsource=")
                        .append(type.getProtectionDomain().getCodeSource().getLocation())
                        .append("\nresource=").append(type.getResource(name))
                        .append("\nsha256=").append(HexFormat.of().formatHex(
                                MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())))
                        .append("\n");
            }
        }
        flashDeadline = KeeperVisuals.class.getDeclaredField("flashUntil");
        flashDeadline.setAccessible(true);
        var duration = KeeperVisuals.class.getDeclaredField("FLASH_NANOS");
        duration.setAccessible(true);
        require(duration.getLong(null) == 200_000_000L, "runtime uses the production 200ms default");
        output.append("duration_nanos=").append(duration.getLong(null)).append("\n");
        Files.writeString(RESULTS.resolve("runtime-classes.txt"), output);
    }

    @SubscribeEvent
    public static void rendered(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || timingSaved || flashDeadline == null) return;
        try {
            long deadline = flashDeadline.getLong(null);
            if (deadline == 0) return;
            require(observedDeadline == 0 || observedDeadline == deadline, "one native shutter action only");
            double left = (deadline - System.nanoTime()) / 1_000_000.0;
            double elapsed = 200.0 - left;
            if (observedDeadline == 0) {
                require(phase.equals("shoot") && !inPhotoWorld(), "flash precedes the photo frame");
                Files.writeString(RESULTS.resolve("flash-deadline.txt"), Long.toString(deadline));
            }
            observedDeadline = deadline;
            frameTimes.append(System.currentTimeMillis()).append(',').append(elapsed).append(',')
                    .append(left).append(',').append(phase).append(',').append(inPhotoWorld()).append('\n');
            if (left > 0) {
                positiveFrames++;
                if (firstPositive < 0) firstPositive = elapsed;
                lastPositive = elapsed;
            } else if (firstClear < 0) firstClear = elapsed;
            if (elapsed >= 650) {
                Files.writeString(RESULTS.resolve("flash-frame-times.csv"), frameTimes);
                require(positiveFrames >= 2 && firstPositive <= 100 && firstClear <= 250,
                        "insufficient real rendered frames around the 200ms deadline: first="
                                + firstPositive + " last=" + lastPositive + " clear=" + firstClear);
                Files.writeString(RESULTS.resolve("flash-observed.txt"),
                        "default_ms=200 first_positive_ms=" + firstPositive + " last_positive_ms="
                                + lastPositive + " first_clear_ms=" + firstClear + " positive_frames=" + positiveFrames + "\n");
                timingSaved = true;
            }
        } catch (Throwable error) {
            try { Files.writeString(RESULTS.resolve("client.failed"), error.toString()); } catch (Exception ignored) { }
            failed = true;
        }
    }

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

    private static volatile boolean samplerReady;

    private static void startFlashGrabber() {
        if (grabberStarted) return;
        grabberStarted = true;
        Thread thread = new Thread(() -> {
            try {
                long timeout = System.nanoTime() + 10_000_000_000L;
                while (!Files.exists(RESULTS.resolve("sampler.ready")) && System.nanoTime() < timeout)
                    Thread.sleep(1);
                require(Files.exists(RESULTS.resolve("sampler.ready")), "external X11 sampler ready");
                long before = System.nanoTime();
                Files.writeString(RESULTS.resolve("clock-request.txt"), Long.toString(before));
                String reply = "";
                while (System.nanoTime() < timeout) {
                    if (Files.exists(RESULTS.resolve("clock-response.txt")))
                        reply = Files.readString(RESULTS.resolve("clock-response.txt")).trim();
                    if (!reply.isEmpty()) break;
                    Thread.sleep(1);
                }
                long after = System.nanoTime();
                long remote = Long.parseLong(reply);
                require(remote >= before && remote <= after && after - before < 50_000_000L,
                        "sampler and client share the monotonic clock, round trip under 50ms");
                Files.writeString(RESULTS.resolve("sampler-clock-check.txt"),
                        "client_before_ns=" + before + " sampler_ns=" + remote
                        + " client_after_ns=" + after + " bound_ms=" + (after - before) / 1_000_000.0 + "\n");
                samplerReady = true;
            } catch (Throwable error) {
                try { Files.writeString(RESULTS.resolve("client.failed"), error.toString()); } catch (Exception ignored) { }
                failed = true;
            }
        }, "keeper-qa-clock-check");
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
            if (flashDeadline == null) provenance();
            if (ticks <= 8) prepare(mc);
            if (!baselineSaved && ticks == 12 && (phase.equals("baseline") || phase.equals("shoot"))) {
                captureBaseline(phase + "@" + ticks);
            }
            if (phase.equals("shoot")) {
                if (ticks == 4) startFlashGrabber();
                if (ticks == 8) input("key", "1");
                if (ticks == 20) {
                    require(samplerReady, "X11 capture and monotonic clock verified before shutter");
                    input("click", "3");
                }
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
                photoSeconds = (mc.player.getEffect(ModEffects.IN_PHOTO.get()).getDuration() + 19) / 20;
                Files.writeString(RESULTS.resolve("hud-seconds.txt"), "first=" + photoSeconds + "\n");
            }
            if (phase.equals("photo") && ticks == 65 && photoSaved) {
                require(inPhotoWorld(), "photo remains active for countdown regression");
                int later = (mc.player.getEffect(ModEffects.IN_PHOTO.get()).getDuration() + 19) / 20;
                BufferedImage screenshot = grab("hud-later.png");
                require(later < photoSeconds && later > 0, "remaining seconds decrease");
                require(countNear(screenshot, 0x5A4632, 48, screenshot.getHeight() * 200 / 256,
                        screenshot.getHeight()) > 20, "later HUD still rendered");
                Files.writeString(RESULTS.resolve("hud-seconds.txt"), "later=" + later + "\n", StandardOpenOption.APPEND);
                Files.writeString(RESULTS.resolve("countdown.pass"), "Real client countdown " + photoSeconds + " -> " + later + "\n");
            }
            if (phase.equals("exit")) {
                if (ticks == 4) input("mouseup", "3");
                if (ticks == 12) input("click", "3");
            }
            if (phase.equals("clear") && ticks == 12) {
                require(!inPhotoWorld(), "server exit clears client session effect");
                require(flashDeadline.getLong(null) <= System.nanoTime(), "exit has no active shutter");
                BufferedImage screenshot = grab("exit.png");
                int[] mean = meanCenter(screenshot);
                int hud = countNear(screenshot, 0x5A4632, 48, screenshot.getHeight() * 200 / 256, screenshot.getHeight());
                require(hud < 20, "exit removes countdown HUD");
                require(mean[2] > mean[0] + 10, "exit removes sepia filter in the blue-sky fixture");
                Files.writeString(RESULTS.resolve("exit.pass"), "HUD pixels=" + hud + " RGB="
                        + mean[0] + "," + mean[1] + "," + mean[2] + "\n");
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve("client.failed"), failure.toString()); } catch (Exception ignored) { }
            failed = true;
        }
    }
}
