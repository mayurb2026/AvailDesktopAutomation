package utils;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jcodec.api.awt.AWTSequenceEncoder;

/**
 * Records an area of the Windows screen to an MP4 file (used for the AVAIL Desktop app,
 * which Playwright's own video recording cannot see).
 *
 *   ScreenRecorder recorder = new ScreenRecorder(area, Paths.get("test-output/videos/x.mp4"));
 *   recorder.start();
 *   ... do things ...
 *   recorder.stop();
 *
 * HOW IT WORKS
 *   A background thread takes a screenshot of the area (java.awt.Robot) 5 times per second
 *   and adds each one as a frame to an H.264 MP4 video (JCodec library, see pom.xml).
 *
 * WHEN IS IT USED? Controlled by video.mode in config.properties (see BaseTest) - no input
 * values need to be changed here.
 *
 * REQUIREMENTS / NOTES
 *   (REUSED UNCHANGED from the AVAIL Stream framework.)
 *   - The screen must be unlocked and the window VISIBLE (in front) - Robot records what is on the
 *     screen, so anything covering the window is recorded too. AvailDesktopApp.bringToFront() / BaseTest do this.
 *   - ALWAYS call stop() (best in a finally block) - the MP4 is only playable after stop() finishes it.
 */
public class ScreenRecorder {

	/** Frames per second. Higher = smoother video but bigger file and more CPU. 5 is enough for a desktop app. */
	private static final int FPS = 5;

	private final Rectangle area;
	private final Path output;
	private volatile boolean running;
	private Thread thread;
	private volatile Exception error;

	/**
	 * @param area   screen area to record (e.g. the AVAIL Desktop window from AvailDesktopApp.bringToFront())
	 * @param output MP4 file to create (e.g. test-output/videos/<test>_<time>.mp4)
	 * CALLED FROM: BaseTest.setUp() (when video.mode is not off).
	 */
	public ScreenRecorder(Rectangle area, Path output) {
		// H.264 needs an even width and height
		this.area = new Rectangle(area.x, area.y, area.width & ~1, area.height & ~1);
		this.output = output;
	}

	/**
	 * Starts recording in the background (the test continues immediately).
	 * CALLED FROM: BaseTest.setUp().
	 */
	public void start() throws Exception {
		Files.createDirectories(output.getParent());
		running = true;
		thread = new Thread(this::record, "screen-recorder");
		thread.setDaemon(true);   // never keeps the JVM alive if the test ends unexpectedly
		thread.start();
	}

	/**
	 * Stops recording and finishes the MP4 file.
	 * CALLED FROM: BaseTest.teardown() (always runs, also when the test failed).
	 */
	public Path stop() throws Exception {
		running = false;
		if (thread != null)
			thread.join(60000);   // waits up to 60 s for the video to be written
		if (error != null)
			throw error;          // recording failed -> the caller logs a warning
		return output;
	}

	/**
	 * Background loop: capture a frame, add it to the video, wait for the next frame time.
	 * CALLED FROM: ScreenRecorder.start() only - runs in a background thread until stop().
	 */
	private void record() {
		AWTSequenceEncoder encoder = null;
		try {
			Robot robot = new Robot();
			encoder = AWTSequenceEncoder.createSequenceEncoder(output.toFile(), FPS);
			long frameMillis = 1000 / FPS;
			long startTime = System.currentTimeMillis();
			long framesWritten = 0;

			while (running) {
				BufferedImage frame = robot.createScreenCapture(area);
				// keep the video in real time: if capturing fell behind, repeat the frame (max 5 times)
				long framesDue = (System.currentTimeMillis() - startTime) / frameMillis + 1;
				int repeats = 0;
				do {
					encoder.encodeImage(frame);
					framesWritten++;
					repeats++;
				} while (framesWritten < framesDue && repeats < 5);

				long sleep = startTime + framesWritten * frameMillis - System.currentTimeMillis();
				if (sleep > 0)
					Thread.sleep(sleep);
			}
		} catch (Exception e) {
			error = e;
		} finally {
			// finish() writes the end of the MP4 file - without it the video cannot be played
			if (encoder != null) {
				try {
					encoder.finish();
				} catch (Exception e) {
					error = e;
				}
			}
		}
	}
}
