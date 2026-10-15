import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

final class NightPixelProbe {
    private record Pixels(int white, int square) { }

    private static Pixels sample(Path path) throws Exception {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new AssertionError("PNG could not be decoded: " + path);
        int white = 0, largest = 0;
        int[] previous = new int[image.getWidth() + 1];
        for (int y = image.getHeight() / 6; y < image.getHeight() * 5 / 6; y++) {
            int[] row = new int[previous.length];
            for (int x = image.getWidth() / 3; x < image.getWidth() * 2 / 3; x++) {
                int color = image.getRGB(x, y);
                if ((color & 255) > 220 && ((color >>> 8) & 255) > 220 && ((color >>> 16) & 255) > 220) {
                    white++;
                    row[x + 1] = 1 + Math.min(row[x], Math.min(previous[x], previous[x + 1]));
                    largest = Math.max(largest, row[x + 1]);
                }
            }
            previous = row;
        }
        return new Pixels(white, largest * largest);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("negative.png positive.png");
        Pixels negativeControl = sample(Path.of(args[0]));
        Pixels after = sample(Path.of(args[1]));
        System.out.println("NIGHT_ORIGINAL_PIXEL_RULE negative=" + negativeControl + ", positive=" + after);
        if (!(after.square() >= 1024 && after.square() > negativeControl.square() + 900
                && after.white() > negativeControl.white() + 1000)) {
            throw new AssertionError("continuous white plane exceeds particles-only negative control: negative="
                    + negativeControl + ", positive=" + after);
        }
        System.out.println("NIGHT_ORIGINAL_PIXEL_RULE_PASSED");
    }
}
