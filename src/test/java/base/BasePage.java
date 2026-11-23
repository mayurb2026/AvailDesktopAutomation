package base;

import desktop.DesktopDriver;
import desktop.DesktopLocator;

/**
 * Parent of every AVAIL Desktop page object.
 *
 * SAME STANDARD AS AVAIL Stream pages: locators are created in the CONSTRUCTOR, actions are methods,
 * NO assertions in pages (they belong in the tests). The only difference: pages get a DesktopDriver
 * instead of a Playwright Page, and locators are DesktopLocator (same Playwright-style API).
 *
 *   public class ChannelPage extends BasePage {
 *       private final DesktopLocator contentList;
 *       public ChannelPage(DesktopDriver driver) {
 *           super(driver);
 *           contentList = window.byAutomationId("ContentListView");
 *       }
 *   }
 *
 * FINDING LOCATORS: run Accessibility Insights for Windows (or Inspect.exe from the Windows SDK), hover the
 * element in AVAIL Desktop and copy its AutomationId (best), Name or ControlType.
 */
public abstract class BasePage {

	protected final DesktopDriver driver;
	/** The AVAIL Desktop main window - start every locator here. */
	protected final DesktopLocator window;

	protected BasePage(DesktopDriver driver) {
		this.driver = driver;
		this.window = driver.window();
	}
}
