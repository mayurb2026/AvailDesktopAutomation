package utils;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

import javax.imageio.ImageIO;

/**
 * Screenshots of the AVAIL Desktop window (or any screen area).
 *
 * ADAPTED FROM AVAIL Stream utils.Screenshotutil (browser screenshot via Playwright) +
 * AvailDesktopApp.takeScreenshot (java.awt.Robot) - AVAIL Desktop is not a browser, so Robot captures the screen.
 *   - same file naming: test-output/screenshots/<testName>_<yyyyMMdd_HHmmss>.png
 *   - same Base64 output, so the image is embedded INSIDE ExtentReport.html
 *   - CHANGED: returns a Shot (path + Base64) instead of a static "lastSavedPath", so it is safe for parallel tests.
 *
 * NOTE: Robot captures what is ON SCREEN in that area - the window must be in front (BaseTest does this).
 */
public final class ScreenshotUtil {

	/** Folder for all screenshots (relative to the project folder). */
	public static final Path SCREENSHOT_DIR = Paths.get("test-output", "screenshots");

	/** Result of one screenshot: the saved file + the same image as Base64 for the Extent report. */
	public static final class Shot {
		public final Path file;
		public final String base64;

		Shot(Path file, String base64) {
			this.file = file;
			this.base64 = base64;
		}
	}

	private ScreenshotUtil() {
	}

	/**
	 * Captures the given screen area, saves it as PNG and returns path + Base64.
	 * CALLED FROM: BaseTest.teardown() (per screenshot.mode) and tests that want extra evidence.
	 */
	public static Shot capture(Rectangle area, String testName) {
		try {
			BufferedImage image = new Robot().createScreenCapture(area);
			String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			Path file = SCREENSHOT_DIR.resolve(testName + "_" + timestamp + ".png");
			Files.createDirectories(file.getParent());
			ImageIO.write(image, "png", file.toFile());
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(image, "png", bytes);
			return new Shot(file, Base64.getEncoder().encodeToString(bytes.toByteArray()));
		} catch (Exception e) {
			throw new RuntimeException("Could not take screenshot: " + e.getMessage(), e);
		}
	}
}
