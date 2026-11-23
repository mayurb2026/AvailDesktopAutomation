package pages.search;

import java.util.List;

import base.BasePage;
import desktop.DesktopDriver;
import desktop.DesktopLocator;
import pages.channels.ChannelPage;
import utils.Log;
import utils.WaitUtil;

/**
 * SEARCH in AVAIL Desktop.
 *
 * *** STARTER - LOCATORS NOT CONFIRMED YET ***
 * On 23-Nov-2026 the Channels view had NO text box (UI Automation ControlType "Edit") - neither on the channel list
 * nor inside a channel. Search is probably on the Home / Palette / Stream views of the left panel
 * (RadioButtons "HomeRadioBtn", "PaletteRadioBtn", "StreamRadioBtn") or appears after a click on a search icon.
 *
 * TO FINISH THIS PAGE:
 *   1. Open AVAIL Desktop, open the search field by hand.
 *   2. Accessibility Insights for Windows -> hover the search box -> copy its AutomationId (or Name).
 *   3. Put it in SEARCH_BOX_ID below (and the button / view that opens search in openSearch()).
 *   4. Enable tests.search.DesktopSearchTest (enabled = true).
 *
 * Typing uses UI Automation ValuePattern (no keyboard needed).
 */
public class SearchPage extends BasePage {

	/** AutomationId of the search text box - TO CONFIRM (see class comment). Empty = first "Edit" control. */
	private static final String SEARCH_BOX_ID = "";

	private final DesktopLocator searchBox;
	private final DesktopLocator resultTiles;

	public SearchPage(DesktopDriver driver) {
		super(driver);
		searchBox = SEARCH_BOX_ID.isEmpty() ? window.byControlType("Edit").first() : window.byAutomationId(SEARCH_BOX_ID);
		// assumption: results are shown as the same file tiles as in a channel - TO CONFIRM
		resultTiles = window.byAutomationId("ContentListView").byControlType("ListItem").withName(ChannelPage.FILE_TILE);
	}

	/** True if a search box exists right now. */
	public boolean isSearchBoxShown() {
		return searchBox.exists();
	}

	/** Types the text into the search box (replaces what is there) and waits for the results to settle. */
	public void search(String text) {
		Log.step("Searching for '{}'", text);
		searchBox.fill(text);
		WaitUtil.sleep(WaitUtil.scale(2000));
	}

	/** File names of the result tiles. */
	public List<String> getResultNames() {
		return resultTiles.byControlType("Button").allTexts();
	}
}
