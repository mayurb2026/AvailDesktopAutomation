package base;

import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;

import desktop.AvailDesktopApp;
import desktop.DesktopDriver;
import utils.ConfigReader;
import utils.ExtentReportsManager;
import utils.Log;
import utils.MediaMode;
import utils.ScreenRecorder;
import utils.ScreenshotUtil;
import utils.WaitUtil;

/**
 * BASE CLASS FOR ALL AVAIL DESKTOP TESTS - every test class must "extends BaseTest".
 *
 * ADAPTED FROM AVAIL Stream base.BaseTest - same flow, but a DESKTOP SESSION instead of a browser:
 *
 *   ONCE PER TEST CLASS (@BeforeClass / @AfterClass)
 *     startDesktopSession(): starts the UI Automation driver (DesktopDriver.ps1) and attaches to the
 *                            "AVAIL Desktop" window (AVAIL Stream: a new browser per test instead).
 *     endDesktopSession():   ends the driver. AVAIL Desktop itself keeps running.
 *
 *   BEFORE EVERY @Test (setUp)
 *     1. Extent report entry (name = method name (+ data row), description = @Test description) + log context
 *     2. screenshot.mode / video.mode from config.properties (MediaMode: off | fail | all)
 *     3. AVAIL Desktop maximized + brought to the front (desktop.bringToFront) - screenshots/videos need it
 *     4. ScreenRecorder starts recording the window (MP4) if video.mode is not off
 *
 *   AFTER EVERY @Test (teardown - runs even if the test failed)
 *     1. PASS / FAIL / SKIP in the report (with the error for a failure)
 *     2. Window screenshot embedded in the report (per screenshot.mode)
 *     3. Video stopped -> kept + linked in the report, or deleted (per video.mode)
 *     4. Report written to test-output/ExtentReport.html
 *
 * WHAT TESTS CAN USE
 *   driver  -> DesktopDriver (pages take it in their constructor: new ChannelPage(driver))
 *   app     -> AvailDesktopApp (bringToFront, windowArea)
 *   test    -> the Extent entry (prefer utils.Log.step(...) - writes report + log file)
 *   attachScreenshot("title") -> extra evidence in the middle of a test
 *
 * BEFORE YOU RUN: AVAIL Desktop RUNNING + SIGNED IN, screen UNLOCKED, don't use mouse/keyboard during the run.
 */
public class BaseTest {

	private static final Path VIDEO_DIR = Paths.get("test-output", "videos");

	protected DesktopDriver driver;
	protected AvailDesktopApp app;
	protected ExtentTest test;

	protected MediaMode screenshotMode;
	protected MediaMode videoMode;
	private ScreenRecorder recorder;
	private Path videoFile;
	private Rectangle windowArea;

	/**
	 * Starts the UI Automation session and attaches to AVAIL Desktop - once per test class.
	 * Fails the whole class with a clear message if AVAIL Desktop is not running / not found.
	 */
	@BeforeClass(alwaysRun = true)
	public void startDesktopSession() {
		Log.info("Starting desktop session for {}", getClass().getSimpleName());
		driver = new DesktopDriver().start();
		app = new AvailDesktopApp(driver);
		app.attach();
	}

	/**
	 * CALLED FROM: TestNG automatically (@BeforeMethod) before EACH @Test.
	 * Object[] params = the data-provider row (if any), shown in the report name.
	 */
	@BeforeMethod(alwaysRun = true)
	public void setUp(Method method, Object[] params) {
		Test t = method.getAnnotation(Test.class);
		String name = method.getName() + (params != null && params.length > 0
				? " [" + Arrays.stream(params).map(String::valueOf).collect(Collectors.joining(", ")) + "]" : "");
		test = ExtentReportsManager.startTest(name, t != null ? t.description() : "");
		Log.setTest(method.getName());

		// INPUT (config.properties): screenshot.mode / video.mode = off | fail | all
		screenshotMode = MediaMode.from(ConfigReader.get("screenshot.mode", "fail"));
		videoMode = MediaMode.from(ConfigReader.get("video.mode", "fail"));
		Log.info("Media: screenshot={}, video={}, timeout multiplier={}", screenshotMode, videoMode, WaitUtil.multiplier());

		// INPUT (config.properties): desktop.bringToFront - screenshots / video only show what is ON SCREEN
		if (Boolean.parseBoolean(ConfigReader.get("desktop.bringToFront", "true")))
			windowArea = app.bringToFront();
		else
			windowArea = app.windowArea();

		if (videoMode != MediaMode.OFF) {
			try {
				videoFile = VIDEO_DIR.resolve(method.getName() + "_" + timestamp() + ".mp4");
				recorder = new ScreenRecorder(windowArea, videoFile);
				recorder.start();
			} catch (Exception e) {
				recorder = null;
				Log.warn("Could not start video recording: {}", e.getMessage());
			}
		}
	}

	/** CALLED FROM: TestNG automatically (@AfterMethod) after EACH @Test - also when it failed. */
	@AfterMethod(alwaysRun = true)
	public void teardown(ITestResult result) {
		boolean passed = result.getStatus() == ITestResult.SUCCESS;
		boolean failed = result.getStatus() == ITestResult.FAILURE;
		try {
			if (failed) {
				test.fail(result.getThrowable());
				if (screenshotMode.shouldCapture(false))
					attachScreenshot(Status.FAIL, "Screenshot at failure - " + shortReason(result.getThrowable()), result.getMethod().getMethodName());
			} else if (passed) {
				test.pass("Test passed");
				if (screenshotMode.shouldCapture(true))
					attachScreenshot(Status.PASS, "Screenshot at end of test", result.getMethod().getMethodName());
			} else {
				test.skip(result.getThrowable() != null ? result.getThrowable().toString() : "Test skipped");
			}
		} finally {
			finishVideo(passed, failed);
			try {
				driver.release();
			} catch (Exception e) {
				Log.warn("Could not release element handles: {}", e.getMessage());
			}
			ExtentReportsManager.flush();
			ExtentReportsManager.endTest();
			Log.setTest(null);
		}
	}

	/** Ends the UI Automation session - once per test class. AVAIL Desktop keeps running. */
	@AfterClass(alwaysRun = true)
	public void endDesktopSession() {
		if (driver != null)
			driver.close();
	}

	// ---- helpers tests can use ---------------------------------------------------------------

	/** Screenshot of the AVAIL Desktop window, embedded in the report under the current step. */
	protected void attachScreenshot(String title) {
		attachScreenshot(Status.INFO, title, "step");
	}

	private void attachScreenshot(Status status, String title, String fileName) {
		try {
			ScreenshotUtil.Shot shot = ScreenshotUtil.capture(windowArea != null ? windowArea : app.windowArea(), fileName);
			test.log(status, title, MediaEntityBuilder.createScreenCaptureFromBase64String(shot.base64, title).build());
			Log.info("Screenshot saved: {}", shot.file);
		} catch (Exception e) {
			test.warning("Could not capture screenshot: " + e.getMessage());   // never hides the real result
		}
	}

	/** Stops the recorder; keeps + links the MP4 per video.mode, otherwise deletes it. */
	private void finishVideo(boolean passed, boolean failed) {
		if (recorder == null)
			return;
		try {
			recorder.stop();
			if ((passed || failed) && videoMode.shouldCapture(passed)) {
				String relative = "videos/" + videoFile.getFileName();   // relative to test-output/ExtentReport.html
				test.log(passed ? Status.PASS : Status.FAIL, "AVAIL Desktop video:<br><video width='640' controls src='" + relative
						+ "'></video><br><a href='" + relative + "' target='_blank'>Open video in new tab</a>");
			} else {
				Files.deleteIfExists(videoFile);
			}
		} catch (Exception e) {
			Log.warn("Could not save video: {}", e.getMessage());
		} finally {
			recorder = null;
		}
	}

	private static String timestamp() {
		return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	}

	/** First line of the error, so the screenshot title says why the test failed. (Same as AVAIL Stream.) */
	private String shortReason(Throwable t) {
		if (t == null || t.getMessage() == null)
			return t == null ? "unknown" : t.getClass().getSimpleName();
		String firstLine = t.getMessage().trim().split("\\R")[0];
		return firstLine.length() > 150 ? firstLine.substring(0, 150) + "..." : firstLine;
	}
}
