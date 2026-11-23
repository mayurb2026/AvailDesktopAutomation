package pages.channels;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import base.BasePage;
import desktop.DesktopDriver;
import desktop.DesktopElement;
import desktop.DesktopLocator;
import utils.Log;
import utils.WaitUtil;

/**
 * CHANNELS tab: the CHANNEL LIST and the CONTENT of one channel.
 *
 * PAGE OBJECT - locators in the constructor, actions as methods, NO assertions.
 *
 * TWO VIEWS (both confirmed in the live app, 23-Nov-2026):
 *   CHANNEL LIST   container AutomationId "CrossChannelSideControl"; buttons "Channels", "+ New Channel";
 *                  list "ChannelListView" (VIRTUALIZED - only the tiles on screen exist, scroll to find others)
 *                  one tile = ListItem "Avail.UI.ViewModels.ChannelViewModel" with TextBlocks:
 *                  [channel name, description, channel name, "Created <date>"] + a toggle Button (favorite)
 *   ONE CHANNEL    container AutomationId "ChannelControl"; breadcrumb "_BreadCrumbControl" with Buttons
 *                  "Channels" / "<channel name>"; files list "ContentListView"; filter panel "FiltersControl";
 *                  "filter by:" / "sort by:" ComboBoxes; view buttons "ViewModeToggleButton", "SliderButton"
 *                  one file tile = ListItem "Avail.UI.ViewModels.ContentViewModel", file name = text of the
 *                  Button inside the tile (same as the AVAIL Stream framework's AvailDesktop.ps1)
 *
 * OPENING A CHANNEL: a channel tile has NO UI Automation "Invoke" - it is opened with a DOUBLE-CLICK like a user.
 * The driver only double-clicks when AVAIL Desktop is the FOREGROUND window (BaseTest brings it to the front),
 * so nobody may use the PC during that step.
 */
public class ChannelPage extends BasePage {

	/** UI Automation Name of a channel tile / a file tile (WPF shows the view-model type as Name). */
	public static final String CHANNEL_TILE = "Avail.UI.ViewModels.ChannelViewModel";
	public static final String FILE_TILE = "Avail.UI.ViewModels.ContentViewModel";

	// channel list view
	private final DesktopLocator channelListView;
	private final DesktopLocator channelList;
	private final DesktopLocator channelTiles;
	private final DesktopLocator newChannelButton;
	// one channel view
	private final DesktopLocator channelView;
	private final DesktopLocator breadcrumb;
	private final DesktopLocator breadcrumbChannelsButton;
	private final DesktopLocator contentList;
	private final DesktopLocator fileTiles;
	private final DesktopLocator filtersPanel;
	private final DesktopLocator viewModeToggle;

	public ChannelPage(DesktopDriver driver) {
		super(driver);
		channelListView = window.byAutomationId("CrossChannelSideControl");
		channelList = window.byAutomationId("ChannelListView");
		channelTiles = channelList.byControlType("ListItem").withName(CHANNEL_TILE).directChildren();
		newChannelButton = channelListView.byControlType("Button").withName("+ New Channel");

		channelView = window.byAutomationId("ChannelControl");
		breadcrumb = window.byAutomationId("_BreadCrumbControl");
		breadcrumbChannelsButton = breadcrumb.byControlType("Button").withName("Channels");
		contentList = window.byAutomationId("ContentListView");
		fileTiles = contentList.byControlType("ListItem").withName(FILE_TILE);
		filtersPanel = window.byAutomationId("FiltersControl");
		viewModeToggle = window.byAutomationId("ViewModeToggleButton");
	}

	// ------------------------------------------------------------------ channel list

	/** True if the channel LIST is shown (not a single channel). */
	public boolean isChannelListShown() {
		return channelList.exists();
	}

	/** True if ONE channel is open (breadcrumb + files list shown). */
	public boolean isChannelOpen() {
		return channelView.exists() && contentList.exists();
	}

	/**
	 * Goes to the channel list. Inside a channel: clicks "Channels" in the breadcrumb (UI Automation Invoke,
	 * no mouse). Waits until the list is shown.
	 */
	public void goToChannelList() {
		if (!isChannelListShown()) {
			Log.step("Opening the channel list (breadcrumb 'Channels')");
			breadcrumbChannelsButton.click();
		}
		channelList.waitFor(WaitUtil.scale(20000));
	}

	/** True if the "+ New Channel" button is shown on the channel list. */
	public boolean isNewChannelButtonShown() {
		return newChannelButton.exists();
	}

	/** Channel names of the tiles that exist right now (= on screen; the list is virtualized). */
	public List<String> getVisibleChannelNames() {
		List<String> names = new ArrayList<>();
		for (DesktopElement tile : channelTiles.all())
			names.add(tile.text());
		return names;
	}

	/** All texts of the n-th visible channel tile, e.g. [name, description, name, "Created Jun 1 2026"]. */
	public List<String> getChannelTileTexts(int n) {
		return channelTiles.nth(n).byControlType("Text").directChildren().allTexts();
	}

	/**
	 * Scrolls the virtualized channel list from top to bottom until a tile with this name exists.
	 * Returns the tile locator, or null if the channel does not exist.
	 */
	public DesktopLocator findChannelTile(String channelName) {
		DesktopLocator tile = channelList.byControlType("ListItem").directChildren().withText(channelName);
		double step = Math.max(5, channelList.scrollTo(0) / 2);
		for (double p = 0; p <= 100; p += step) {
			if (tile.exists())
				return tile;
			if (step >= 100)
				break;
			channelList.scrollTo(Math.min(100, p + step));
			WaitUtil.sleep(300);   // let WPF create the tiles that scrolled into view
		}
		return tile.exists() ? tile : null;
	}

	/** Every channel name (scrolls through the whole virtualized list once). */
	public List<String> getAllChannelNames() {
		Set<String> names = new LinkedHashSet<>();
		double step = Math.max(5, channelList.scrollTo(0) / 2);
		for (double p = 0; p <= 100 + step; p += step) {
			names.addAll(getVisibleChannelNames());
			if (step >= 100)
				break;
			channelList.scrollTo(Math.min(100, p + step));
			WaitUtil.sleep(300);
		}
		channelList.scrollTo(0);
		return new ArrayList<>(names);
	}

	/**
	 * Opens a channel: channel list -> find the tile (scrolling) -> double-click -> waits for its files list.
	 * Fails with "Channel '...' not found" if no tile has this name (check desktop.channel / testdata).
	 */
	public void openChannel(String channelName) {
		if (isChannelOpen() && channelName.equals(getOpenChannelName())) {
			Log.step("Channel '{}' is already open", channelName);
			return;
		}
		goToChannelList();
		DesktopLocator tile = findChannelTile(channelName);
		if (tile == null)
			throw new RuntimeException("Channel '" + channelName + "' not found in AVAIL Desktop (" + getAllChannelNames().size() + " channels listed)");
		Log.step("Opening channel '{}' (double-click on its tile)", channelName);
		tile.doubleClick();
		WaitUtil.until(() -> isChannelOpen() && channelName.equals(getOpenChannelName()), WaitUtil.scale(30000),
				"channel '" + channelName + "' to open");
	}

	// ------------------------------------------------------------------ one channel

	/** Breadcrumb texts, e.g. [Channels, 23rd Nov Egnyte - 1] (empty icon buttons removed). */
	public List<String> getBreadcrumb() {
		return breadcrumb.byControlType("Button").allTexts();
	}

	/** Name of the open channel = last breadcrumb entry ("" if no channel is open). */
	public String getOpenChannelName() {
		List<String> crumbs = getBreadcrumb();
		return crumbs.size() < 2 ? "" : crumbs.get(crumbs.size() - 1);
	}

	/** Number of file tiles that exist right now (the files list is virtualized too - see getFileNames). */
	public int getVisibleFileCount() {
		return fileTiles.count();
	}

	/**
	 * Names of the files shown in the open channel (text of the Button inside each file tile).
	 * Waits until the list has stopped changing (3 equal reads in a row), like the AVAIL Stream script.
	 */
	public List<String> getFileNames() {
		List<String> names = new ArrayList<>();
		int previous = -1, stable = 0;
		for (int i = 0; i < 40 && stable < 3; i++) {
			names = readFileNames();
			stable = names.size() == previous ? stable + 1 : 0;
			previous = names.size();
			WaitUtil.sleep(500);
		}
		return names;
	}

	private List<String> readFileNames() {
		List<String> names = new ArrayList<>();
		int n = fileTiles.count();
		for (int i = 0; i < n; i++) {
			String name = fileTiles.nth(i).byControlType("Button").first().text();
			if (!name.isEmpty())
				names.add(name);
		}
		return names;
	}

	/** Locator of the file tile showing this file name (for ThumbnailPage / PropertiesPage). */
	public DesktopLocator fileTile(String fileName) {
		return contentList.byControlType("ListItem").withName(FILE_TILE).containingText(fileName);
	}

	/** True if the filter panel ("Filters") of the open channel is shown. */
	public boolean isFiltersPanelShown() {
		return filtersPanel.exists();
	}

	/** True if the Tile / List view toggle button is shown. */
	public boolean isViewModeToggleShown() {
		return viewModeToggle.exists();
	}
}
