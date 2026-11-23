package tests.properties;

import java.util.List;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import base.BaseTest;
import pages.channels.ChannelPage;
import pages.properties.PropertiesPage;
import utils.ConfigReader;
import utils.Log;

/**
 * TC_D040 - TC_D041 - PROPERTIES ("Details" side panel) of a file.
 *
 * WHAT IS VERIFIED
 *   TC_D040 the "Details" side panel is shown in an open channel
 *   TC_D041 after selecting the first file, the Details panel shows the file name
 *           (TO CONFIRM: the exact property labels - see PropertiesPage; getPanelTexts() is logged for that)
 *
 * INPUT (config.properties): desktop.channel = a channel WITH files. Selecting a file uses UI Automation (no mouse);
 * opening the channel uses a double-click (don't use the PC). Run alone: mvn test -Dtest=PropertiesValidationTest
 */
public class PropertiesValidationTest extends BaseTest {

	private void openChannel() {
		String channel = ConfigReader.get("desktop.channel", "");
		if (channel.isEmpty())
			throw new SkipException("Set desktop.channel (a channel with files) in config.properties");
		new ChannelPage(driver).openChannel(channel);
	}

	@Test(priority = 1, description = "TC_D040 - Verify Details side panel is shown in an open channel")
	public void detailsPanelShown() {
		openChannel();
		Assert.assertTrue(new PropertiesPage(driver).isDetailsPanelShown(), "'Details' side panel is not shown.");
	}

	@Test(priority = 2, description = "TC_D041 - Verify Details panel shows the selected file name")
	public void detailsShowSelectedFile() {
		openChannel();
		PropertiesPage properties = new PropertiesPage(driver);
		if (properties.getFileCount() == 0)
			throw new SkipException("desktop.channel has no files - set it to a channel with files");
		String fileName = properties.selectFile(0);
		List<String> texts = properties.getPanelTexts();
		Log.step("Details panel texts: {}", texts);
		attachScreenshot("Details panel of '" + fileName + "'");
		Assert.assertTrue(properties.panelContains(fileName), "Details panel does not show '" + fileName + "': " + texts);
	}
}
