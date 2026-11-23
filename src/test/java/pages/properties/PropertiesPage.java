package pages.properties;

import java.util.List;

import base.BasePage;
import desktop.DesktopDriver;
import desktop.DesktopLocator;
import pages.channels.ChannelPage;
import utils.Log;
import utils.WaitUtil;

/**
 * PROPERTIES of a file = the "Details" side panel on the right of an open channel.
 *
 * PAGE OBJECT - locators in the constructor, actions as methods, NO assertions.
 *
 * LOCATORS
 *   CONFIRMED (23-Nov-2026): side panel AutomationId "SidePanelView" with the title TextBlock "Details",
 *              its buttons in "SidePanelButtonCollection" and the scroll area "LensSidePanelScrollViewer".
 *   TO CONFIRM with Accessibility Insights on a channel WITH files: which TextBlocks the panel shows after a file
 *              is selected (name, type, size, tags ...). getPanelTexts() returns all of them, so tests can start
 *              with "the selected file name is shown" and get more precise once the property labels are known.
 *
 * SELECTING A FILE uses UI Automation SelectionItemPattern (no mouse).
 */
public class PropertiesPage extends BasePage {

	private final DesktopLocator sidePanel;
	private final DesktopLocator panelTitle;
	private final DesktopLocator panelTexts;
	private final DesktopLocator fileTiles;

	public PropertiesPage(DesktopDriver driver) {
		super(driver);
		sidePanel = window.byAutomationId("SidePanelView");
		panelTitle = sidePanel.byControlType("Text").withName("Details");
		panelTexts = sidePanel.byControlType("Text");
		fileTiles = window.byAutomationId("ContentListView").byControlType("ListItem").withName(ChannelPage.FILE_TILE);
	}

	/** True if the "Details" side panel is shown. */
	public boolean isDetailsPanelShown() {
		return sidePanel.exists() && panelTitle.exists();
	}

	/** Selects the n-th file tile of the open channel (SelectionItemPattern - no mouse) and lets the panel update. */
	public String selectFile(int n) {
		DesktopLocator tile = fileTiles.nth(n);
		String name = tile.byControlType("Button").first().text();
		Log.step("Selecting file '{}' to show its details", name);
		tile.select();
		WaitUtil.sleep(WaitUtil.scale(1500));
		return name;
	}

	/** All non-empty texts in the Details panel right now (title, property labels and values). */
	public List<String> getPanelTexts() {
		return panelTexts.allTexts();
	}

	/** True if any text of the Details panel contains this value (e.g. the selected file name). */
	public boolean panelContains(String value) {
		return getPanelTexts().stream().anyMatch(t -> t.contains(value));
	}

	/** Number of file tiles in the open channel (right now). */
	public int getFileCount() {
		return fileTiles.count();
	}
}
