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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
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
    private static boolean shutterSent;
    private static final StringBuilder frameTimes = new StringBuilder("epoch_ms,elapsed_ms,left_ms,phase,in_photo\n");
    private static Field flashDeadline;
    private static volatile long observedDeadline;
    private static boolean timingSaved;
    private static int positiveFrames;
    private static double firstPositive = -1, lastPositive = -1, firstClear = -1;
    private static int photoSeconds;
    private static int connectionRetries;
    private static boolean joinedWorld;
    private static long warmupBegan, lastWarmupFrame, stableSince;
    private static int stableFrames;
    private static long shutterClickNs, shutterStableNanos;
    private static int shutterStableFrames;
    private static boolean enterPressed;
    private static long enterBegan;
    private static final StringBuilder warmupFrames = new StringBuilder(
            "epoch_ms,monotonic_ns,gap_ms,stable_frames,baseline_saved\n");

    private static void observeWarmupFrame(long now) throws Exception {
        if (shutterSent || !(phase.equals("baseline") || phase.equals("shoot"))) return;
        if (warmupBegan == 0) warmupBegan = now;
        long gap = lastWarmupFrame == 0 ? 0 : now - lastWarmupFrame;
        if (lastWarmupFrame == 0 || gap > 250_000_000L) {
            stableSince = now;
            stableFrames = 0;
        }
        stableFrames++;
        lastWarmupFrame = now;
        warmupFrames.append(System.currentTimeMillis()).append(',').append(now).append(',')
                .append(gap / 1_000_000.0).append(',').append(stableFrames).append(',')
                .append(baselineSaved).append('\n');
        if (now - warmupBegan > 90_000_000_000L) {
            Files.writeString(RESULTS.resolve("render-warmup.csv"), warmupFrames);
            throw new AssertionError("Render readiness unavailable within 90s: need 2s of real frames"
                    + " with gaps <=250ms before native shutter; stable_frames=" + stableFrames);
        }
    }

    private static boolean renderReady() {
        long now = System.nanoTime();
        return stableFrames >= 20 && now - stableSince >= 2_000_000_000L
                && now - lastWarmupFrame <= 250_000_000L;
    }

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
        if (event.phase != TickEvent.Phase.END || failed || flashDeadline == null) return;
        try {
            observeWarmupFrame(System.nanoTime());
            if (timingSaved) return;
            long deadline = flashDeadline.getLong(null);
            if (deadline == 0) return;
            require(observedDeadline == 0 || observedDeadline == deadline, "one native shutter action only");
            double left = (deadline - System.nanoTime()) / 1_000_000.0;
            double elapsed = 200.0 - left;
            if (observedDeadline == 0) {
                require(phase.equals("shoot") && !inPhotoWorld(), "flash precedes the photo frame");
            }
            observedDeadline = deadline;
            frameTimes.append(System.currentTimeMillis()).append(',').append(elapsed).append(',')
                    .append(left).append(',').append(phase).append(',').append(inPhotoWorld()).append('\n');
            if (left > 0) {
                positiveFrames++;
                if (firstPositive < 0) firstPositive = elapsed;
                lastPositive = elapsed;
            } else if (firstClear < 0) {
                firstClear = elapsed;
            }
            if (elapsed >= 650) {
                // Keep all disk I/O outside the measured shutter window.
                atomicSignal("flash-deadline.txt", Long.toString(observedDeadline));
                Files.writeString(RESULTS.resolve("flash-frame-times.csv"), frameTimes);
                Files.writeString(RESULTS.resolve("shutter-input.txt"), "native_right_click_ns=" + shutterClickNs + "\n");
                Files.writeString(RESULTS.resolve("render-warmup.csv"), warmupFrames);
                Files.writeString(RESULTS.resolve("render-ready.txt"), "stable_frames=" + shutterStableFrames
                        + " stable_ms=" + shutterStableNanos / 1_000_000.0
                        + " warmup_max_gap_ms=250 required_stable_ms=2000\n");
                if (positiveFrames >= 2 && firstPositive <= 100 && firstClear >= 0 && firstClear <= 250) {
                    Files.writeString(RESULTS.resolve("flash-observed.txt"),
                            "default_ms=200 first_positive_ms=" + firstPositive + " last_positive_ms="
                                    + lastPositive + " first_clear_ms=" + firstClear + " positive_frames=" + positiveFrames + "\n");
                } else {
                    // Preserve this failed gate while collecting independent photo regressions.
                    Files.writeString(RESULTS.resolve("flash-render.failed"),
                            "insufficient real rendered frames around the 200ms deadline: first="
                                    + firstPositive + " last=" + lastPositive + " clear=" + firstClear);
                }
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

    private static void atomicSignal(String name, String value) throws Exception {
        Path temporary = RESULTS.resolve(name + ".tmp");
        Files.writeString(temporary, value);
        Files.move(temporary, RESULTS.resolve(name), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static void startFlashGrabber() {
        if (grabberStarted) return;
        grabberStarted = true;
        Thread thread = new Thread(() -> {
            try {
                long timeout = System.nanoTime() + 10_000_000_000L;
                while (!Files.exists(RESULTS.resolve("sampler.ready")) && System.nanoTime() < timeout)
                    Thread.sleep(1);
                require(Files.exists(RESULTS.resolve("sampler.ready")), "external X11 sampler ready");
                // Retry scheduling outliers without relaxing the 50ms clock-domain bound.
                for (int attempt = 1; attempt <= 30; attempt++) {
                    long before = System.nanoTime();
                    atomicSignal("clock-request.txt", attempt + "," + before);
                    long remote = 0;
                    while (System.nanoTime() - before < 1_000_000_000L) {
                        if (Files.exists(RESULTS.resolve("clock-response.txt"))) {
                            String[] reply = Files.readString(RESULTS.resolve("clock-response.txt")).trim().split(",");
                            if (reply.length == 2 && Integer.parseInt(reply[0]) == attempt) {
                                remote = Long.parseLong(reply[1]);
                                break;
                            }
                        }
                        Thread.sleep(1);
                    }
                    long after = System.nanoTime();
                    String record = "attempt=" + attempt + " client_before_ns=" + before + " sampler_ns=" + remote
                            + " client_after_ns=" + after + " bound_ms=" + (after - before) / 1_000_000.0 + "\n";
                    Files.writeString(RESULTS.resolve("sampler-clock-attempts.txt"), record,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    if (remote >= before && remote <= after && after - before < 50_000_000L) {
                        Files.writeString(RESULTS.resolve("sampler-clock-check.txt"), record);
                        atomicSignal("clock-accepted.txt", Integer.toString(attempt));
                        samplerReady = true;
                        return;
                    }
                    Thread.sleep(25);
                }
                throw new AssertionError("No clock handshake under 50ms in 30 attempts; see sampler-clock-attempts.txt");
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
        if (event.phase != TickEvent.Phase.END || failed) return;
        Minecraft mc = Minecraft.getInstance();
        if (phase.isEmpty()) {
            if (mc.player != null && mc.level != null) {
                joinedWorld = true;
                return;
            }
            if (mc.screen instanceof ConnectScreen) {
                return;
            }
            if (mc.screen instanceof DisconnectedScreen) {
                try {
                    if (joinedWorld) {
                        throw new AssertionError("disconnected after world join; see server.log Timed out");
                    }
                    require(connectionRetries < 5, "isolated server connection failed after five retries");
                    connectionRetries++;
                    grab("connection-failed-" + connectionRetries + ".png");
                    String address = "127.0.0.1:" + System.getenv().getOrDefault("KEEPER_QA_PORT", "25578");
                    Files.writeString(RESULTS.resolve("connection-retries.txt"),
                            "attempt=" + connectionRetries + " monotonic_ns=" + System.nanoTime()
                                    + " target=" + address + "\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    // Use Minecraft's actual connection flow; never bypass its handshake or packets.
                    ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(address),
                            new ServerData("Keeper isolated QA", address, false), false);
                } catch (Throwable error) {
                    try { Files.writeString(RESULTS.resolve("client.failed"), error.toString()); } catch (Exception ignored) { }
                    failed = true;
                }
            }
            return;
        }
        if (mc.player == null || mc.level == null) return;
        try {
            ticks++;
            if (flashDeadline == null) provenance();
            if (ticks <= 8) prepare(mc);
            if (!baselineSaved && ticks >= 12 && renderReady()
                    && (phase.equals("baseline") || phase.equals("shoot"))) {
                captureBaseline(phase + "@" + ticks);
                lastWarmupFrame = stableSince = 0;
                stableFrames = 0;
            }
            if (phase.equals("shoot")) {
                if (ticks == 8) input("key", "1");
                if (baselineSaved && renderReady()) startFlashGrabber();
                if (ticks >= 20 && !shutterSent && baselineSaved && renderReady() && samplerReady
                        && Files.exists(RESULTS.resolve("capture.ready"))) {
                    shutterClickNs = System.nanoTime();
                    shutterStableNanos = shutterClickNs - stableSince;
                    shutterStableFrames = stableFrames;
                    shutterSent = true;
                    input("click", "3");
                }
            }

            if (phase.equals("enter")) {
                if (ticks == 8) input("key", "2");
                if (ticks >= 16 && !enterPressed && !inPhotoWorld()) {
                    require(mc.player.getInventory().getSelected().is(ModItems.KEEPER_PHOTO.get()),
                            "native photo entry selects the captured photo");
                    enterBegan = System.nanoTime();
                    input("mousedown", "3");
                    enterPressed = true;
                }
                if (enterPressed && inPhotoWorld()) {
                    input("mouseup", "3");
                    enterPressed = false;
                }
                require(enterBegan == 0 || inPhotoWorld() || System.nanoTime() - enterBegan < 30_000_000_000L,
                        "native held photo use received no server entry acknowledgement within 30s");
            }
            if (phase.equals("photo") && enterPressed) {
                input("mouseup", "3");
                enterPressed = false;
                Files.writeString(RESULTS.resolve("entry-input.txt"),
                        "held_from_ns=" + enterBegan + " released_on_photo_ns=" + System.nanoTime() + "\n");
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
                Files.writeString(RESULTS.resolve("exit-stats.txt"), "HUD pixels=" + hud + " RGB="
                        + mean[0] + "," + mean[1] + "," + mean[2] + " monotonic_ns=" + System.nanoTime() + "\n");
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
