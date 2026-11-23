package pages.desktop;

import java.util.List;
import java.util.stream.Collectors;

import base.BasePage;
import desktop.DesktopDriver;
import desktop.DesktopLocator;

/**
 * AVAIL Desktop MAIN WINDOW: title, top tabs (Channels / Marketplace), left side panel, status bar.
 *
 * PAGE OBJECT - locators in the constructor, actions as methods, NO assertions (same standard as AVAIL Stream).
 *
 * LOCATORS CONFIRMED in the live app (UI Automation tree, AVAIL Desktop INTERNAL BUILD, 23-Nov-2026):
 *   main window        AutomationId "mMainWindow", title "AVAIL Desktop"
 *   top tab control    AutomationId "mTabControl" -> TabItems whose header TextBlock is "Channels" / "Marketplace"
 *   left panel         AutomationId "cLeftSidePanel" (Expander) with RadioButtons "HomeRadioBtn",
 *                      "PaletteRadioBtn", "StreamRadioBtn", the "FAVORITES" label and list "FavoritesListView"
 *   left panel toggle  AutomationId "LeftPanelToggle"
 *   status bar         class "StatusBarView"
 *
 * WHEN DO I NEED TO CHANGE THIS FILE?  Only when the AVAIL Desktop UI changes (check with Accessibility Insights).
 */
public class DesktopHomePage extends BasePage {

	private final DesktopLocator tabControl;
	private final DesktopLocator tabHeaders;
	private final DesktopLocator leftPanel;
	private final DesktopLocator homeButton;
	private final DesktopLocator paletteButton;
	private final DesktopLocator streamButton;
	private final DesktopLocator favoritesLabel;
	private final DesktopLocator favoritesList;
	private final DesktopLocator statusBar;

	public DesktopHomePage(DesktopDriver driver) {
		super(driver);
		tabControl = window.byAutomationId("mTabControl");
		// one TabItem per top tab; its visible name is a TextBlock deep inside its "TabItemHeader"
		tabHeaders = tabControl.byControlType("TabItem").directChildren();
		leftPanel = window.byAutomationId("cLeftSidePanel");
		homeButton = leftPanel.byAutomationId("HomeRadioBtn");
		paletteButton = leftPanel.byAutomationId("PaletteRadioBtn");
		streamButton = leftPanel.byAutomationId("StreamRadioBtn");
		favoritesLabel = leftPanel.byControlType("Text").withName("FAVORITES");
		favoritesList = leftPanel.byAutomationId("FavoritesListView");
		statusBar = window.byClassName("StatusBarView");
	}

	/** Title of the attached window, e.g. "AVAIL Desktop". */
	public String getWindowTitle() {
		return window.info().name();
	}

	/** AutomationId of the main window ("mMainWindow" = the real AVAIL Desktop main window, not a dialog). */
	public String getWindowAutomationId() {
		return window.info().automationId();
	}

	/** Names of the top tabs, e.g. [Channels, Marketplace]. */
	public List<String> getTabNames() {
		List<String> names = new java.util.ArrayList<>();
		int tabs = tabHeaders.count();
		for (int i = 0; i < tabs; i++) {
			DesktopLocator headerText = tabHeaders.nth(i).byAutomationId("TabItemHeader").byClassName("TextBlock");
			names.addAll(headerText.allTexts());
		}
		return names.stream().distinct().collect(Collectors.toList());
	}

	/** Waits until the main UI (tab control) is loaded - after start / sign-in this can take a while. */
	public void waitForLoaded(long timeoutMs) {
		tabControl.waitFor(timeoutMs);
	}

	/** True if the left panel with Home / Palette / Stream buttons is shown (only after sign-in). */
	public boolean isLeftPanelShown() {
		return leftPanel.exists() && homeButton.exists() && paletteButton.exists() && streamButton.exists();
	}

	/** True if the "FAVORITES" section of the left panel is shown. */
	public boolean isFavoritesSectionShown() {
		return favoritesLabel.exists() && favoritesList.exists();
	}

	/** Number of favorite channels in the left panel. */
	public int getFavoriteCount() {
		return favoritesList.byControlType("ListItem").directChildren().count();
	}

	/** True if the status bar at the bottom exists. */
	public boolean isStatusBarShown() {
		return statusBar.exists();
	}
}
