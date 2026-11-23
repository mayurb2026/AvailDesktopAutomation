package desktop;

/**
 * One UI element found in AVAIL Desktop (a snapshot of its properties at the time it was found).
 *
 * Filled from the JSON the UI Automation driver (DesktopDriver.ps1) returns. Use it to READ values in tests
 * (e.g. the names of all files in a channel). To ACT on an element (click, type), use a DesktopLocator - it finds
 * the element again right before acting, so it also works after the app redraws its window.
 *
 * Fields (same names as in Accessibility Insights / Inspect.exe):
 *   controlType  - Button, Text, List, ListItem, Edit, ComboBox, Custom ...
 *   name         - UI Automation Name (WPF removes the first "_" of button names - use text for display text)
 *   automationId - x:Name / AutomationProperties.AutomationId set by the developers (most stable locator)
 *   className    - WPF class, e.g. TextBlock, ListBoxItem, ChannelView
 *   text         - visible text (TextBlock inside the element, else Value, else Name)
 *   x, y, width, height - position on screen in PHYSICAL pixels
 */
public class DesktopElement {

	String id;
	String controlType;
	String name;
	String automationId;
	String className;
	String text;
	boolean enabled;
	boolean offscreen;
	int x, y, width, height;

	/** Driver handle (e.g. "e12") - valid until the app redraws the element. */
	public String id() { return id; }
	public String controlType() { return controlType; }
	public String name() { return name == null ? "" : name; }
	public String automationId() { return automationId == null ? "" : automationId; }
	public String className() { return className == null ? "" : className; }
	public String text() { return text == null ? "" : text.trim(); }
	public boolean isEnabled() { return enabled; }
	/** True when the element is on screen (not scrolled away, not on a hidden tab). */
	public boolean isVisible() { return !offscreen && width > 0 && height > 0; }
	public int x() { return x; }
	public int y() { return y; }
	public int width() { return width; }
	public int height() { return height; }

	@Override
	public String toString() {
		return controlType + "[name='" + name() + "', id='" + automationId() + "', class='" + className() + "', text='" + text() + "']";
	}
}
