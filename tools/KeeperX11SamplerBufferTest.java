import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
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
        System.out.println("copies=" + copies + " allocated_bytes=" + allocated
                + " bytes_per_copy=" + allocated / (double) copies + " rgb_equal=true storage_reused=true");
    }
}
