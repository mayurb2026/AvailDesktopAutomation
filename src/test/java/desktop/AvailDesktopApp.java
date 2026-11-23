package desktop;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import utils.ConfigReader;
import utils.Log;
import utils.WaitUtil;

/**
 * AVAIL Desktop APPLICATION level: is it running, start it, attach, bring to front, window area on screen.
 *
 * ADAPTED FROM AVAIL Stream desktop.AvailDesktopApp:
 *   - bringToFront() + the DPI / multi-monitor conversion (toScreenCoordinates) are reused unchanged
 *   - CHANGED: talks to the app through ONE DesktopDriver session instead of a new PowerShell per call
 *   - the channel-specific ListFiles logic moved to the page objects (pages.channels.ChannelPage)
 *   - NEW: isRunning() / launch() (desktop.launchIfNotRunning)
 *
 * INPUT (config.properties): desktop.window.title, desktop.app.path, desktop.launchIfNotRunning,
 *                            desktop.attachTimeoutSeconds
 */
public class AvailDesktopApp {

	private final DesktopDriver driver;

	public AvailDesktopApp(DesktopDriver driver) {
		this.driver = driver;
	}

	/** True if an "Avail" process is running on this PC. */
	public static boolean isRunning() {
		return ProcessHandle.allProcesses().anyMatch(p -> p.info().command()
				.map(c -> c.toLowerCase().endsWith("\\avail.exe")).orElse(false));
	}

	/**
	 * Starts Avail.exe (desktop.app.path). The user still has to be SIGNED IN in AVAIL Desktop -
	 * the login itself (Okta in the app) is not automated by this framework (see the mapping document).
	 */
	public static void launch() {
		Path exe = Paths.get(ConfigReader.get("desktop.app.path"));
		if (!Files.exists(exe))
			throw new RuntimeException("desktop.app.path does not exist: " + exe);
		try {
			new ProcessBuilder(exe.toString()).directory(exe.getParent().toFile()).start();
			Log.info("Started {}", exe);
		} catch (Exception e) {
			throw new RuntimeException("Could not start AVAIL Desktop: " + e.getMessage(), e);
		}
	}

	/**
	 * Makes sure AVAIL Desktop is running (starts it if desktop.launchIfNotRunning=true) and attaches the driver
	 * to its main window. CALLED FROM: BaseTest.startDesktopSession() (@BeforeClass).
	 */
	public DesktopElement attach() {
		String title = ConfigReader.get("desktop.window.title", "AVAIL Desktop");
		long timeout = WaitUtil.scale(Long.parseLong(ConfigReader.get("desktop.attachTimeoutSeconds", "60")) * 1000);
		if (!isRunning()) {
			if (Boolean.parseBoolean(ConfigReader.get("desktop.launchIfNotRunning", "false")))
				launch();
			else
				throw new RuntimeException("AVAIL Desktop is not running. Start it and sign in, "
						+ "or set desktop.launchIfNotRunning=true in config.properties.");
		}
		// INPUT (config.properties): desktop.window.automationId - the MAIN window only (the splash screen shown
		// while AVAIL Desktop starts has the same title but no AutomationId)
		return driver.attach(title, ConfigReader.get("desktop.window.automationId", "mMainWindow"), timeout);
	}

	/**
	 * Maximizes AVAIL Desktop, brings it to the front and returns its area in SCREEN coordinates
	 * (for ScreenshotUtil / ScreenRecorder). CALLED FROM: BaseTest.setUp() / teardown().
	 */
	public Rectangle bringToFront() {
		DesktopElement w = driver.show();
		return toScreenCoordinates(new Rectangle(w.x(), w.y(), w.width(), w.height()));
	}

	/** Window area in screen coordinates WITHOUT changing the window (for a screenshot when it is already in front). */
	public Rectangle windowArea() {
		DesktopElement w = driver.windowInfo();
		return toScreenCoordinates(new Rectangle(w.x(), w.y(), w.width(), w.height()));
	}

	/**
	 * REUSED UNCHANGED from AVAIL Stream AvailDesktopApp:
	 * Windows reports the window in physical pixels; java.awt.Robot works in scaled pixels when
	 * display scaling is above 100%. Convert using the monitor the window is on.
	 * Also cuts off the few pixels a maximized window hangs over the screen edge.
	 */
	static Rectangle toScreenCoordinates(Rectangle physical) {
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			Rectangle b = device.getDefaultConfiguration().getBounds();
			double scale = device.getDefaultConfiguration().getDefaultTransform().getScaleX();
			Rectangle devicePhysical = new Rectangle(b.x, b.y, (int) Math.round(b.width * scale), (int) Math.round(b.height * scale));
			int centerX = physical.x + physical.width / 2;
			int centerY = physical.y + physical.height / 2;
			if (devicePhysical.contains(centerX, centerY)) {
				Rectangle visible = physical.intersection(devicePhysical);
				return new Rectangle(
						b.x + (int) Math.round((visible.x - b.x) / scale),
						b.y + (int) Math.round((visible.y - b.y) / scale),
						(int) Math.round(visible.width / scale),
						(int) Math.round(visible.height / scale));
			}
		}
		return physical;
	}
}
