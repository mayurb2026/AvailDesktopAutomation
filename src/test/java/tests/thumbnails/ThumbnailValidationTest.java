package tests.thumbnails;

import java.util.List;
import java.util.stream.Collectors;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import base.BaseTest;
import pages.channels.ChannelPage;
import pages.thumbnails.ThumbnailPage;
import utils.ConfigReader;
import utils.Log;

/**
 * TC_D030 - TC_D031 - THUMBNAILS of the files in a channel.
 *
 * WHAT IS VERIFIED
 *   TC_D030 every file tile in desktop.channel shows a file name
 *   TC_D031 every file tile shows a thumbnail image with a size (TO CONFIRM: thumbnail control type, see ThumbnailPage)
 *
 * INPUT (config.properties): desktop.channel = a channel WITH files (opened with a double-click - don't use the PC).
 * Run alone: mvn test -Dtest=ThumbnailValidationTest
 */
public class ThumbnailValidationTest extends BaseTest {

	private List<ThumbnailPage.Tile> openChannelTiles() {
		String channel = ConfigReader.get("desktop.channel", "");
		if (channel.isEmpty())
			throw new SkipException("Set desktop.channel (a channel with files) in config.properties");
		new ChannelPage(driver).openChannel(channel);
		List<ThumbnailPage.Tile> tiles = new ThumbnailPage(driver).getTiles();
		Log.step("Channel '{}': {} file tiles", channel, tiles.size());
		if (tiles.isEmpty())
			throw new SkipException("Channel '" + channel + "' has no files - set desktop.channel to a channel with files");
		return tiles;
	}

	@Test(priority = 1, description = "TC_D030 - Verify every file tile shows a file name")
	public void tilesShowFileName() {
		List<ThumbnailPage.Tile> tiles = openChannelTiles();
		List<ThumbnailPage.Tile> noName = tiles.stream().filter(t -> t.fileName.isEmpty()).collect(Collectors.toList());
		Assert.assertTrue(noName.isEmpty(), noName.size() + " file tiles have no name.");
	}

	@Test(priority = 2, description = "TC_D031 - Verify every file tile shows a thumbnail image")
	public void tilesShowThumbnail() {
		List<ThumbnailPage.Tile> tiles = openChannelTiles();
		List<String> missing = tiles.stream().filter(t -> !t.hasThumbnail).map(t -> t.fileName).collect(Collectors.toList());
		Log.step("Tiles: {}", tiles);
		Assert.assertTrue(missing.isEmpty(), "File tiles without thumbnail: " + missing);
	}
}
