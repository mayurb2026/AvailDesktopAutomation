package desktop;

/** Error from the UI Automation driver (element not found, app not running, no clickable pattern ...). */
public class DesktopException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DesktopException(String message) {
		super(message);
	}

	public DesktopException(String message, Throwable cause) {
		super(message, cause);
	}
}
