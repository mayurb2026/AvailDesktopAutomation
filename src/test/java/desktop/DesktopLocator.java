package desktop;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import utils.WaitUtil;

/**
 * PLAYWRIGHT-STYLE LOCATOR FOR AVAIL DESKTOP - the desktop equivalent of com.microsoft.playwright.Locator.
 *
 * Page objects create locators in their CONSTRUCTOR (same standard as AVAIL Stream) and act on them in methods:
 *
 *   contentList = driver.window().byAutomationId("ContentListView");     // like page.locator("#ContentListView")
 *   items       = contentList.byControlType("ListItem");                 // like contentList.locator("li")
 *   items.count();  items.nth(0).click();  items.first().text();  items.waitFor(10000);
 *
 * LAZY like Playwright: a locator is only a DESCRIPTION. The element is searched again every time you act
 * (click, text, count ...), so it keeps working after AVAIL Desktop redraws its window.
 *
 * LOCATOR PRIORITY (most stable first) - check the values with Accessibility Insights for Windows / Inspect.exe:
 *   1. byAutomationId("ContentListView")   AutomationId (x:Name set by the developers)
 *   2. byControlType("Button").withText("Channels")   control type + visible text
 *   3. byName("Filters")                     UI Automation Name
 *   4. byClassName("ChannelView")            WPF class
 *   Combine them:  byControlType("Button").withName("Channels")
 *
 * SCOPE: by default children of ALL levels below the parent are searched (Descendants);
 *        .directChildren() searches only one level (faster, more precise).
 */
public class DesktopLocator {

	private final DesktopDriver driver;
	private final DesktopLocator parent;              // null = the main window
	private final Map<String, Object> criteria;       // automationId, name, nameContains, textEquals, controlType, className, scope
	private final int index;                          // which match (0 = first)

	private DesktopLocator(DesktopDriver driver, DesktopLocator parent, Map<String, Object> criteria, int index) {
		this.driver = driver;
		this.parent = parent;
		this.criteria = Collections.unmodifiableMap(criteria);
		this.index = index;
	}

	/** The attached main window (DesktopDriver.window()). */
	static DesktopLocator root(DesktopDriver driver) {
		return new DesktopLocator(driver, null, Map.of(), 0);
	}

	// ------------------------------------------------------------------ building locators (no search yet)

	private DesktopLocator child(String key, Object value) {
		Map<String, Object> c = new LinkedHashMap<>();
		c.put(key, value);
		return new DesktopLocator(driver, this, c, 0);
	}

	private DesktopLocator with(String key, Object value) {
		if (parent == null && criteria.isEmpty())
			return child(key, value);   // window().withName(...) = search below the window
		Map<String, Object> c = new LinkedHashMap<>(criteria);
		c.put(key, value);
		return new DesktopLocator(driver, parent, c, index);
	}

	/** Element(s) below this one with this AutomationId. */
	public DesktopLocator byAutomationId(String automationId) { return child("automationId", automationId); }
	/** Element(s) below this one of this control type: Button, Text, List, ListItem, Edit, ComboBox, TabItem, Custom ... */
	public DesktopLocator byControlType(String controlType) { return child("controlType", controlType); }
	/** Element(s) below this one with this exact UI Automation Name. */
	public DesktopLocator byName(String name) { return child("name", name); }
	/** Element(s) below this one with this WPF class name. */
	public DesktopLocator byClassName(String className) { return child("className", className); }

	/** Adds: exact UI Automation Name. */
	public DesktopLocator withName(String name) { return with("name", name); }
	/** Adds: Name or visible text CONTAINS this (like Playwright hasText). */
	public DesktopLocator containingText(String part) { return with("nameContains", part); }
	/** Adds: visible text is EXACTLY this (TextBlock inside the element). */
	public DesktopLocator withText(String text) { return with("textEquals", text); }
	/** Adds: control type. */
	public DesktopLocator withControlType(String controlType) { return with("controlType", controlType); }
	/** Adds: AutomationId. */
	public DesktopLocator withAutomationId(String automationId) { return with("automationId", automationId); }
	/** Search only the direct children of the parent (one level). */
	public DesktopLocator directChildren() { return with("scope", "children"); }

	/** The n-th match (0 = first), like Playwright nth(). */
	public DesktopLocator nth(int n) { return new DesktopLocator(driver, parent, criteria, n); }
	public DesktopLocator first() { return nth(0); }

	// ------------------------------------------------------------------ resolving (searches the app)

	/** Handle of the element this locator points to, or null if not found right now. */
	private String resolveId() {
		if (parent == null && criteria.isEmpty())
			return "w0";
		String parentId = parent == null ? "w0" : parent.resolveId();
		if (parentId == null)
			return null;
		List<DesktopElement> found = driver.find(parentId, criteria, index > 0);
		return found.size() > index ? found.get(index).id() : null;
	}

	private String require() {
		String id = resolveId();
		if (id == null)
			throw new DesktopException("Element not found: " + this);
		return id;
	}

	/** ALL matching elements right now (snapshot), e.g. every file tile in a channel. Empty list when none. */
	public List<DesktopElement> all() {
		String parentId = parent == null ? "w0" : parent.resolveId();
		if (parentId == null)
			return List.of();
		if (parent == null && criteria.isEmpty())
			return List.of(driver.info("w0"));
		return driver.find(parentId, criteria, true);
	}

	/** Number of matching elements right now. */
	public int count() { return all().size(); }

	/** Visible texts of all matching elements (empty texts removed). */
	public List<String> allTexts() {
		return all().stream().map(DesktopElement::text).filter(t -> !t.isEmpty()).collect(Collectors.toList());
	}

	/** Properties of the element (fails if not found). */
	public DesktopElement info() { return driver.info(require()); }

	/** True if the element exists AND is on screen right now (no waiting). */
	public boolean isVisible() {
		try {
			String id = resolveId();
			return id != null && driver.info(id).isVisible();
		} catch (DesktopException e) {
			return false;
		}
	}

	/** True if the element exists right now (visible or not). */
	public boolean exists() {
		try {
			return resolveId() != null;
		} catch (DesktopException e) {
			return false;
		}
	}

	// ------------------------------------------------------------------ waiting

	/** Waits until the element exists (timeoutMs already scaled, e.g. WaitUtil.scale(10000)). */
	public DesktopLocator waitFor(long timeoutMs) {
		WaitUtil.until(() -> resolveId() != null, timeoutMs, "element " + this);
		return this;
	}

	/** Waits until the element exists, using timeout.default.ms. */
	public DesktopLocator waitFor() { return waitFor(WaitUtil.defaultTimeout()); }

	/** Waits until the element is gone. */
	public void waitForHidden(long timeoutMs) {
		WaitUtil.until(() -> resolveId() == null, timeoutMs, "element to disappear " + this);
	}

	// ------------------------------------------------------------------ actions (wait for the element first)

	/** Clicks it (Invoke / Toggle / Select / Expand pattern, or a real mouse click as last resort). */
	public String click() {
		waitFor();
		return driver.click(require());
	}

	/** Selects it (list item, tab item, radio button). */
	public void select() {
		waitFor();
		driver.select(require());
	}

	/** Types into it (text box with ValuePattern) - replaces the current text. */
	public void fill(String value) {
		waitFor();
		driver.setValue(require(), value);
	}

	/** Opens it (combo box, expander, tree item). */
	public void expand() {
		waitFor();
		driver.expand(require());
	}

	/**
	 * Real mouse double-click (e.g. to open a channel tile - tiles have no UI Automation "Invoke").
	 * SAFE: the driver refuses it unless AVAIL Desktop is the foreground window, so the click never lands in
	 * another application. Needs desktop.bringToFront=true and nobody using the PC during the run.
	 */
	public void doubleClick() {
		waitFor();
		driver.doubleClick(require());
	}

	/** Scrolls the element into view inside its list (ScrollItemPattern). */
	public void scrollIntoView() {
		waitFor();
		driver.scrollIntoView(require());
	}

	/**
	 * Scrolls THIS list to verticalPercent (0 = top, 100 = bottom). Virtualized WPF lists only create the
	 * tiles that are on screen, so to find an item you scroll step by step (see ChannelPage.findChannelTile).
	 * @return the visible part of the list in percent (step size for scrolling), or 100 if it cannot scroll
	 */
	public double scrollTo(double verticalPercent) {
		waitFor();
		com.google.gson.JsonObject s = driver.scroll(require(), verticalPercent);
		return s.get("scrollable").getAsBoolean() ? s.get("viewSize").getAsDouble() : 100;
	}

	/** Visible text (TextBlock inside, Value of a text box, or Name). */
	public String text() {
		waitFor();
		return driver.text(require()).trim();
	}

	@Override
	public String toString() {
		String self = criteria.isEmpty() ? "window" : criteria.toString() + (index > 0 ? "[" + index + "]" : "");
		return parent == null ? self : parent + " > " + self;
	}
}
