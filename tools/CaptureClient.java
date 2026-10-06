import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.io.File;
import javax.imageio.ImageIO;

// CI-only screenshot helper. This source is not packaged in the mod.
class CaptureClient {
    public static void main(String[] args) throws Exception {
        var screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        ImageIO.write(new Robot().createScreenCapture(screen), "png", new File(args[0]));
    }
}
