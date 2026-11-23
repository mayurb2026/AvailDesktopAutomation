package tests.sync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import base.BaseTest;
import pages.channels.ChannelPage;
import utils.ConfigReader;
import utils.Log;
import utils.WaitUtil;

/**
 * TC_D050 - SYNC: files expected in a channel appear in AVAIL Desktop (each exactly once).
 *
 * The DESKTOP side of the AVAIL Stream end-to-end check (Stream FileUploadTest TC_071 copied files into a monitored
 * Egnyte folder and then checked AVAIL Desktop). Here only the desktop part: the files must appear in the channel.
 * Upload / copy the files first (by hand, or later from a hybrid test - see the mapping document).
 *
 * INPUT (config.properties):
 *   desktop.channel               channel to check
 *   desktop.sync.expectedFiles    comma-separated file names (empty = test SKIPPED)
 *   desktop.sync.timeoutSeconds   how long to wait for them (default 180)
 *
 * Read-only apart from opening the channel (double-click - don't use the PC). Run: mvn test -Dtest=ChannelSyncTest
 */
public class ChannelSyncTest extends BaseTest {

	@Test(priority = 1, description = "TC_D050 - Verify expected files are synced to the AVAIL Desktop channel exactly once")
	public void expectedFilesSynced() {
		String channel = ConfigReader.get("desktop.channel", "");
		List<String> expected = Arrays.stream(ConfigReader.get("desktop.sync.expectedFiles", "").split(","))
				.map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
		if (channel.isEmpty() || expected.isEmpty())
			throw new SkipException("Set desktop.channel and desktop.sync.expectedFiles in config.properties to run the sync check");

		ChannelPage channels = new ChannelPage(driver);
		channels.openChannel(channel);
		long timeout = WaitUtil.scale(Long.parseLong(ConfigReader.get("desktop.sync.timeoutSeconds", "180")) * 1000);
		List<String> shown = new ArrayList<>();
		long deadline = System.currentTimeMillis() + timeout;
		while (true) {
			// re-open the channel so AVAIL Desktop reloads its list (same idea as the AVAIL Stream script)
			channels.goToChannelList();
			channels.openChannel(channel);
			shown = channels.getFileNames();
			if (shown.containsAll(expected) || System.currentTimeMillis() > deadline)
				break;
			Log.info("Waiting for sync - {} of {} expected files shown", expected.stream().filter(shown::contains).count(), expected.size());
			WaitUtil.sleep(10000);
		}
		Log.step("Files in '{}': {}", channel, shown);
		attachScreenshot("Channel '" + channel + "' after sync");
		for (String file : expected) {
			long times = shown.stream().filter(file::equals).count();
			Assert.assertEquals(times, 1L, "'" + file + "' is shown " + times + " times in channel '" + channel + "' (expected exactly once).");
		}
	}
}
