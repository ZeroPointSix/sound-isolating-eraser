import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;

/** Independent X11 observer: Minecraft intentionally makes its own JVM headless. */
public final class KeeperX11Sampler {
    static final class Capture {
        private long before, after;
        private final BufferedImage image;
        private final int[] pixels;

        Capture(int width, int height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        }

        void copyFrom(BufferedImage source, long started, long finished) {
            if (source.getWidth() != image.getWidth() || source.getHeight() != image.getHeight()) {
                throw new IllegalArgumentException("Capture dimensions changed");
            }
            source.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
            before = started;
            after = finished;
        }

        long before() { return before; }
        long after() { return after; }
        BufferedImage image() { return image; }
    }

    private static long readLong(Path path) throws Exception {
        if (!Files.exists(path)) return 0;
        String value = Files.readString(path).trim();
        return value.isEmpty() ? 0 : Long.parseLong(value);
    }

    private static void atomicSignal(Path results, String name, String value) throws Exception {
        Path temporary = results.resolve(name + ".tmp");
        Files.writeString(temporary, value);
        Files.move(temporary, results.resolve(name), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static double[] centerRgb(BufferedImage image) {
        double[] rgb = new double[3];
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int count = 0;
        for (int y = 45; y < 135; y += 3) {
            for (int x = 80; x < 240; x += 3) {
                int pixel = pixels[y * image.getWidth() + x];
                rgb[0] += (pixel >> 16) & 255;
                rgb[1] += (pixel >> 8) & 255;
                rgb[2] += pixel & 255;
                count++;
            }
        }
        for (int i = 0; i < 3; i++) rgb[i] /= count;
        return rgb;
    }

    private static double whiteAlpha(double[] rgb, double[] baseline) {
        double numerator = 0, denominator = 0;
        for (int i = 0; i < 3; i++) {
            numerator += (rgb[i] - baseline[i]) * (255 - baseline[i]);
            denominator += (255 - baseline[i]) * (255 - baseline[i]);
        }
        if (denominator <= 1000) throw new AssertionError("Pre-shutter scene too white to measure");
        return numerator / denominator;
    }

    static void writeFailureEvidence(Path results, Iterable<Capture> captures,
                                     CharSequence warmup) throws Exception {
        Files.writeString(results.resolve("sampler-warmup.csv"), warmup);
        Path frames = results.resolve("sampler-failure-frames");
        Files.createDirectory(frames);
        StringBuilder csv = new StringBuilder("frame,start_ns,end_ns\n");
        int index = 0;
        for (Capture capture : captures) {
            String name = String.format("%03d.png", index++);
            ImageIO.write(capture.image(), "png", frames.resolve(name).toFile());
            csv.append(name).append(',').append(capture.before()).append(',')
                    .append(capture.after()).append('\n');
        }
        Files.writeString(results.resolve("sampler-failure-captures.csv"), csv);
    }

    public static void main(String[] args) throws Exception {
        Path results = Path.of(args[0]);
        ArrayDeque<Capture> captures = new ArrayDeque<>();
        StringBuilder warmup = new StringBuilder("epoch_ms,start_ns,end_ns,gap_ms,stable_captures\n");
        try {
            Robot robot = new Robot();
            Rectangle roi = new Rectangle(480, 270, 320, 180);
            robot.createScreenCapture(roi);
            // Retained frames reuse old storage; transient Robot images can die young.
            ArrayDeque<Capture> recycled = new ArrayDeque<>();
            for (int i = 0; i < 257; i++) recycled.add(new Capture(roi.width, roi.height));
            Files.writeString(results.resolve("sampler.ready"), "real X11 Robot; headless=false\n");
            long startupSeconds = Long.parseLong(System.getenv().getOrDefault("KEEPER_QA_TIMEOUT_SECONDS", "1200"));
            long timeout = System.nanoTime() + startupSeconds * 1_000_000_000L;
            int answered = 0;
            while (!Files.exists(results.resolve("clock-accepted.txt"))) {
                if (System.nanoTime() > timeout) throw new AssertionError("No accepted client clock handshake");
                Path request = results.resolve("clock-request.txt");
                if (Files.exists(request)) {
                    String[] fields = Files.readString(request).trim().split(",");
                    if (fields.length == 2) {
                        int attempt = Integer.parseInt(fields[0]);
                        if (attempt != answered) {
                            atomicSignal(results, "clock-response.txt", attempt + "," + System.nanoTime());
                            answered = attempt;
                        }
                    }
                }
                Thread.sleep(1);
            }
            timeout = System.nanoTime() + 60_000_000_000L;
            long deadline = 0;
            long deadlineReadAt = 0;
            int droppedCaptures = 0;
            long firstBright = 0;
            double[] baseline = null;
            boolean captureReady = false;
            long lastCaptureStart = 0, stableSince = 0;
            int stableCaptures = 0;
            while (System.nanoTime() < timeout) {
                long before = System.nanoTime();
                BufferedImage screen = robot.createScreenCapture(roi);
                long after = System.nanoTime();
                Capture capture = recycled.pollFirst();
                // Do not evict evidence if a late deadline needs more than the rolling buffer.
                if (capture == null) capture = new Capture(roi.width, roi.height);
                capture.copyFrom(screen, before, after);
                screen.flush();
                captures.add(capture);
                BufferedImage image = capture.image();
                double[] rgb = centerRgb(image);
                if (baseline == null) baseline = rgb;
                double alpha = whiteAlpha(rgb, baseline);
                if (firstBright == 0 && alpha >= .25) firstBright = before;
                // This guard reads real screen pixels, not the tautology elapsed=200-left.
                if (firstBright != 0 && before - firstBright > 250_000_000L && alpha >= .08) {
                    AssertionError overrun = new AssertionError("Real X11 shutter pixels still bright beyond 250ms");
                    // Publish failure before optional evidence I/O can stall the harness.
                    Files.writeString(results.resolve("sampler.failed"), overrun.toString());
                    try {
                        ImageIO.write(image, "png", results.resolve("flash-overrun.png").toFile());
                        Files.writeString(results.resolve("flash-overrun.txt"),
                                "first_bright_ns=" + firstBright + " capture_before_ns=" + before
                                        + " capture_after_ns=" + after + " elapsed_ms="
                                        + (before - firstBright) / 1_000_000.0 + " alpha=" + alpha + "\n");
                    } catch (Exception evidenceError) {
                        overrun.addSuppressed(evidenceError);
                    }
                    throw overrun;
                }
                if (!captureReady) {
                    long gap = lastCaptureStart == 0 ? 0 : before - lastCaptureStart;
                    if (gap > 50_000_000L || after - before > 50_000_000L) {
                        stableSince = 0;
                        stableCaptures = 0;
                    } else {
                        if (stableSince == 0) stableSince = before;
                        stableCaptures++;
                    }
                    lastCaptureStart = before;
                    warmup.append(System.currentTimeMillis()).append(',').append(before).append(',')
                            .append(after).append(',').append(gap / 1_000_000.0).append(',')
                            .append(stableCaptures).append('\n');
                    // Warm the real X11 capture path before permitting the native shutter.
                    if (stableCaptures >= 20 && after - stableSince >= 2_000_000_000L) {
                        atomicSignal(results, "capture.ready", Long.toString(after));
                        captureReady = true;
                    }
                }
                if (deadline == 0) {
                    deadline = readLong(results.resolve("flash-deadline.txt"));
                    if (deadline != 0) deadlineReadAt = System.nanoTime();
                }
                // Deferred client I/O can announce the deadline after a slow render frame.
                // Bound memory while preserving the original 200ms window for later analysis.
                while (deadline == 0 && (captures.size() > 256
                        || captures.getFirst().after() < after - 3_000_000_000L)) {
                    recycled.addLast(captures.removeFirst());
                    droppedCaptures++;
                }
                if (deadline != 0 && after > deadline + 600_000_000L) break;
                Thread.sleep(10);
            }
            Files.writeString(results.resolve("sampler-warmup.csv"), warmup);
            if (!captureReady) throw new AssertionError(
                    "Real X11 sampling did not sustain 2s with capture latency and gaps <=50ms");
            if (deadline == 0) throw new AssertionError("No real shutter deadline observed");
            Files.writeString(results.resolve("flash-buffer-stats.txt"),
                    "max_captures=256 retention_ms=3000 dropped=" + droppedCaptures
                            + " retained=" + captures.size() + " deadline_read_elapsed_ms="
                            + (deadlineReadAt - deadline + 200_000_000L) / 1_000_000.0
                            + " earliest_retained_elapsed_ms="
                            + (captures.getFirst().before() - deadline + 200_000_000L) / 1_000_000.0 + "\n");
            Path frames = results.resolve("flash-frames");
            Files.createDirectories(frames);
            StringBuilder csv = new StringBuilder("frame,start_ms,end_ms,r,g,b,white80,phase\n");
            int index = 0, bestWhite = -1;
            BufferedImage best = null;
            for (Capture capture : captures) {
                double start = (capture.before() - deadline + 200_000_000L) / 1_000_000.0;
                double end = (capture.after() - deadline + 200_000_000L) / 1_000_000.0;
                if (end < -100 || start > 500) continue;
                BufferedImage image = capture.image();
                long r = 0, g = 0, b = 0, n = 0;
                int white = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int c = image.getRGB(x, y);
                        int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
                        if (cr >= 205 && cg >= 205 && cb >= 205) white++;
                        if (x >= 80 && x < 240 && y >= 45 && y < 135) {
                            r += cr; g += cg; b += cb; n++;
                        }
                    }
                }
                String filename = String.format("%03d.png", index++);
                ImageIO.write(image, "png", frames.resolve(filename).toFile());
                csv.append(filename).append(',').append(start).append(',').append(end).append(',')
                        .append(r / (double) n).append(',').append(g / (double) n).append(',')
                        .append(b / (double) n).append(',').append(white).append(",shoot\n");
                if (start >= 0 && start < 200 && white > bestWhite) {
                    bestWhite = white;
                    best = image;
                }
            }
            Files.writeString(results.resolve("flash-x11-times.csv"), csv);
            if (best == null || bestWhite <= 100) throw new AssertionError("No interior shutter pixels");
            ImageIO.write(best, "png", results.resolve("flash.png").toFile());
            Files.writeString(results.resolve("flash-captured.txt"),
                    "X11 center=480,270,320,180 best_white80=" + bestWhite + "\n");
        } catch (Throwable failure) {
            Files.writeString(results.resolve("sampler.failed"), failure.toString());
            // A failed pixel gate must not discard the real frames or readiness trace.
            // Keep absolute timestamps because the client may not have published its deadline yet.
            try {
                writeFailureEvidence(results, captures, warmup);
            } catch (Exception evidenceError) {
                failure.addSuppressed(evidenceError);
            }
            throw failure;
        }
    }
}
