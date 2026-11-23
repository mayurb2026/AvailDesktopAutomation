package pages.login;

import base.BasePage;
import desktop.DesktopDriver;
import desktop.DesktopLocator;
import utils.Log;
import utils.WaitUtil;

/**
 * AVAIL Desktop SIGN-IN screen.
 *
 * PAGE OBJECT - locators in the constructor, actions as methods, NO assertions (same standard as AVAIL Stream).
 *
 * HOW THE SCREEN IS BUILT (checked in the live app, 23-Nov-2026):
 *   When nobody is signed in, AVAIL Desktop covers its main window with a WPF view of class "LoginFullView".
 *   Inside it an embedded browser (WebView2, window title "AVAIL - Sign In") shows the Okta sign-in page.
 *   Windows UI Automation can read that web page too, so it is driven WITHOUT the mouse or keyboard:
 *     Email text box   AutomationId "idp-discovery-username"   (ValuePattern -> fill)
 *     Next button      AutomationId "idp-discovery-submit"     (InvokePattern -> click)
 *     "Sign in with ArchVision" link, "Need help signing in?", "Sign up"
 *   When the user IS signed in, "LoginFullView" does not exist.
 *
 * PASSWORD STEP - TO CONFIRM: it appears only after a valid email + Next. Locators below follow the same Okta page
 * as the AVAIL Stream login (a text box named "Password" and a "Sign In" button) and are matched by name, so they
 * work even without AutomationIds. Check them with Accessibility Insights on the password step if the test fails there.
 *
 * WHERE ARE THE INPUT VALUES?  user.email / user.password / user.invalidPassword in config.properties (passed in by
 * DesktopLoginTest). The password is NEVER written to the log or the report.
 */
public class DesktopLoginPage extends BasePage {

	/** Okta message after a wrong password (same Okta tenant as AVAIL Stream). */
	public static final String FAILED_LOGIN_MESSAGE = "Unable to sign in";

	private final DesktopLocator loginView;
	private final DesktopLocator signInPage;
	private final DesktopLocator emailBox;
	private final DesktopLocator nextButton;
	private final DesktopLocator archVisionLink;
	private final DesktopLocator passwordBox;
	private final DesktopLocator signInButton;
	private final DesktopLocator failedLoginMessage;

	public DesktopLoginPage(DesktopDriver driver) {
		super(driver);
		loginView = window.byClassName("LoginFullView");
		signInPage = window.byAutomationId("okta-sign-in");
		emailBox = window.byAutomationId("idp-discovery-username");
		nextButton = window.byAutomationId("idp-discovery-submit");
		archVisionLink = window.byControlType("Hyperlink").withName("Sign in with ArchVision");
		// password step - matched by name (TO CONFIRM, see class comment)
		passwordBox = window.byControlType("Edit").containingText("Password");
		signInButton = window.byControlType("Button").withName("Sign In");
		failedLoginMessage = window.byControlType("Text").containingText(FAILED_LOGIN_MESSAGE);
	}

	/** True if the sign-in screen covers AVAIL Desktop (= nobody is signed in). */
	public boolean isLoginScreenShown() {
		return loginView.exists();
	}

	/** Waits until the Okta sign-in page inside the app has loaded (the embedded browser needs a moment). */
	public void waitForSignInPage(long timeoutMs) {
		signInPage.waitFor(timeoutMs);
	}

	/** True if the first Okta step (Email + Next) is shown. */
	public boolean isEmailStepShown() {
		return emailBox.exists() && nextButton.exists();
	}

	/**
	 * True if the Okta PASSWORD step (Password box + Sign In button) is shown. Okta stays on this step after a
	 * wrong password, so a sign-in screen can legitimately open here instead of on the Email step.
	 */
	public boolean isPasswordStepShown() {
		return passwordBox.exists() && signInButton.exists();
	}

	/** Which Okta step is shown right now: "email", "password" or "" (neither / still loading). */
	public String getCurrentStep() {
		if (isEmailStepShown())
			return "email";
		if (isPasswordStepShown())
			return "password";
		return "";
	}

	/** True if the "Sign in with ArchVision" link is shown. */
	public boolean isArchVisionLinkShown() {
		return archVisionLink.exists();
	}

	/**
	 * Signs in: Email -> Next -> Password -> Sign In (UI Automation, no mouse / keyboard).
	 * Also works when Okta is already on the password step (e.g. after a wrong password).
	 * Does NOT wait for the result - the test checks it (isLoginScreenShown / getErrorMessage).
	 */
	public void login(String email, String password) {
		waitForSignInPage(WaitUtil.scale(60000));
		if (emailBox.exists()) {
			Log.step("Entering email '{}' -> Next", email);
			emailBox.fill(email);
			nextButton.click();
		}
		passwordBox.waitFor(WaitUtil.scale(30000));
		Log.step("Entering password (hidden) -> Sign In");
		passwordBox.fill(password);
		signInButton.click();
	}

	/** Waits until the sign-in screen is gone (signed in) - fails after timeoutMs. */
	public void waitForSignedIn(long timeoutMs) {
		loginView.waitForHidden(timeoutMs);
	}

	/** Text of the Okta error after a failed sign-in, or "" if none is shown within timeoutMs. */
	public String getErrorMessage(long timeoutMs) {
		if (!WaitUtil.within(failedLoginMessage::exists, timeoutMs))
			return "";
		return failedLoginMessage.text();
	}
}
