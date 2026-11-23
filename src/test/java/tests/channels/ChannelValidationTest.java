package tests.channels;

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import base.BaseTest;
import pages.channels.ChannelPage;
import utils.Log;
import utils.TestDataProvider;

/**
 * TC_D010 - TC_D012 - CHANNELS tab: channel list and opening a channel.
 *
 * WHAT IS VERIFIED
 *   TC_D010 the channel list is shown with "+ New Channel" and at least one channel tile        (no mouse)
 *   TC_D011 every visible channel tile shows a name and a "Created <date>" text                  (no mouse)
 *   TC_D012 DATA-DRIVEN (testdata/channels.csv): the channel opens, the breadcrumb shows its name,
 *           filter panel + view toggle are shown, and (if expectAtLeastOneItem=true) it lists files
 *           -> uses a DOUBLE-CLICK on the tile: AVAIL Desktop must be in front, don't use the PC
 *
 * Nothing is created or changed - "+ New Channel" is only checked, never clicked.
 * Run alone: mvn test -Dtest=ChannelValidationTest
 */
public class ChannelValidationTest extends BaseTest {

	@Test(priority = 1, description = "TC_D010 - Verify channel list is shown with New Channel button and channel tiles")
	public void channelListShown() {
		ChannelPage channels = new ChannelPage(driver);
		channels.goToChannelList();
		Assert.assertTrue(channels.isNewChannelButtonShown(), "'+ New Channel' button is not shown on the channel list.");
		List<String> names = channels.getVisibleChannelNames();
		Log.step("Visible channel tiles: {} (first: {})", names.size(), names.isEmpty() ? "-" : names.get(0));
		Assert.assertFalse(names.isEmpty(), "The channel list shows no channel.");
	}

	@Test(priority = 2, description = "TC_D011 - Verify each channel tile shows a name and created date")
	public void channelTilesShowNameAndDate() {
		ChannelPage channels = new ChannelPage(driver);
		channels.goToChannelList();
		List<String> names = channels.getVisibleChannelNames();
		for (int i = 0; i < names.size(); i++) {
			List<String> texts = channels.getChannelTileTexts(i);
			Assert.assertFalse(names.get(i).isEmpty(), "Channel tile " + (i + 1) + " has no name: " + texts);
			Assert.assertTrue(texts.stream().anyMatch(t -> t.startsWith("Created ")),
					"Channel tile '" + names.get(i) + "' has no 'Created <date>' text: " + texts);
		}
		Log.step("Checked {} channel tiles", names.size());
	}

	@Test(priority = 3, dataProvider = "channels", dataProviderClass = TestDataProvider.class,
			description = "TC_D012 - Verify channel opens and shows its name, filters and files")
	public void openChannel(String channelName, String expectAtLeastOneItem) {
		ChannelPage channels = new ChannelPage(driver);
		channels.openChannel(channelName);
		Assert.assertEquals(channels.getOpenChannelName(), channelName, "Breadcrumb does not show the opened channel.");
		Assert.assertTrue(channels.isFiltersPanelShown(), "Filter panel is not shown in the channel.");
		Assert.assertTrue(channels.isViewModeToggleShown(), "Tile / List view toggle is not shown in the channel.");
		List<String> files = channels.getFileNames();
		Log.step("Channel '{}' shows {} files", channelName, files.size());
		if (Boolean.parseBoolean(expectAtLeastOneItem))
			Assert.assertFalse(files.isEmpty(), "Channel '" + channelName + "' shows no files.");
	}
}
