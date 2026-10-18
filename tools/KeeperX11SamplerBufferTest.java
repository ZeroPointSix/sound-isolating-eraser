import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/** Storage regression using an actual archived X11 frame, not an E2E verdict. */
public final class KeeperX11SamplerBufferTest {
    public static void main(String[] args) throws Exception {
        BufferedImage source = ImageIO.read(Path.of(args[0]).toFile());
        if (source == null) throw new AssertionError("Expected an archived real capture");
        KeeperX11Sampler.Capture slot = new KeeperX11Sampler.Capture(source.getWidth(), source.getHeight());
        BufferedImage storage = slot.image();
        for (int i = 0; i < 200; i++) slot.copyFrom(source, i, i + 1);
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        long allocated = bean.getThreadAllocatedBytes(thread);
        int copies = 2000;
        for (int i = 0; i < copies; i++) slot.copyFrom(source, i, i + 1);
        allocated = bean.getThreadAllocatedBytes(thread) - allocated;
        if (slot.image() != storage || slot.before() != copies - 1 || slot.after() != copies) {
            throw new AssertionError("Storage or capture timestamps changed");
        }
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) & 0xffffff) != (storage.getRGB(x, y) & 0xffffff)) {
                    throw new AssertionError("Archived RGB pixels changed");
                }
            }
        }
        if (allocated < 0 || allocated > copies * 256L) {
            throw new AssertionError("Snapshot copy still allocates image-sized buffers: " + allocated);
        }
        Path evidence = args.length > 1 ? Path.of(args[1]) : Files.createTempDirectory("keeper-capture-evidence-");
        if (args.length > 1) Files.createDirectory(evidence);
        long before = System.nanoTime();
        slot.copyFrom(source, before, System.nanoTime());
        String warmup = "epoch_ms,start_ns,end_ns,gap_ms,stable_captures\n";
        KeeperX11Sampler.writeFailureEvidence(evidence, List.of(slot), warmup);
        if (!Files.readString(evidence.resolve("sampler-warmup.csv")).equals(warmup)
                || !Files.readString(evidence.resolve("sampler-failure-captures.csv"))
                        .contains("000.argb," + slot.before() + "," + slot.after())) {
            throw new AssertionError("Failure capture timestamps or readiness trace were lost");
        }
        var recovered = ByteBuffer.wrap(Files.readAllBytes(evidence.resolve("sampler-failure-frames/000.argb"))).asIntBuffer();
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) & 0xffffff) != (recovered.get() & 0xffffff)) {
                    throw new AssertionError("Failure evidence changed actual archived RGB pixels");
                }
            }
        }
        boolean revoked = false;
        if (args.length > 3) {
            var readiness = new KeeperX11Sampler.Readiness();
            boolean wasReady = false;
            var captures = new TreeMap<Long, Long>();
            for (int input = 2; input <= 3; input++) {
                var lines = Files.readAllLines(Path.of(args[input]));
                for (String line : lines.subList(1, lines.size())) {
                    String[] columns = line.split(",");
                    long start = Long.parseLong(columns[1]);
                    long end = Long.parseLong(columns[2]);
                    Long previous = captures.putIfAbsent(start, end);
                    if (previous != null && previous.longValue() != end) {
                        throw new AssertionError("Conflicting recorded capture timestamps");
                    }
                }
            }
            // The retained ring overlaps warmup; replay each real capture once in time order.
            for (var capture : captures.entrySet()) {
                boolean ready = readiness.observe(capture.getKey(), capture.getValue());
                if (wasReady && !ready) revoked = true;
                wasReady = ready;
            }
            if (!revoked) throw new AssertionError("Recorded sampling gap did not revoke readiness");
        }
        System.out.println("copies=" + copies + " allocated_bytes=" + allocated
                + " bytes_per_copy=" + allocated / (double) copies
                + " rgb_equal=true storage_reused=true failure_frames_preserved=true readiness_revoked=" + revoked);
    }
}
