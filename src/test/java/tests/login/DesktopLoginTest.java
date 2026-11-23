package tests.login;

import java.util.List;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import base.BaseTest;
import pages.desktop.DesktopHomePage;
import pages.login.DesktopLoginPage;
import utils.ConfigReader;
import utils.Log;
import utils.WaitUtil;

/**
 * TC_D001 - TC_D006 - AVAIL Desktop SIGN-IN and the signed-in main window.
 *
 * WHAT IS VERIFIED (in this order - priority)
 *   TC_D001 the "AVAIL Desktop" main window is found (AutomationId mMainWindow)                         [always]
 *   TC_D004 the sign-in screen shows an Okta step: Email (+ Next + ArchVision link) OR Password (+ Sign In) [signed OUT only]
 *   TC_D005 a WRONG password shows "Unable to sign in..." and the user stays on the sign-in screen   [signed OUT only]
 *   TC_D006 VALID email + password sign in: the sign-in screen closes, the Channels tab is shown     [signed OUT only]
 *   TC_D002 the signed-in UI is shown: no sign-in screen, tabs Channels + Marketplace, left panel    [always]
 *   TC_D003 the FAVORITES section and the status bar are shown                                        [always]
 *
 * The tests NEVER sign out. When AVAIL Desktop is already signed in, TC_D004 - TC_D006 are SKIPPED
 * ("already signed in"). To run them: sign out of AVAIL Desktop by hand, then run this class.
 *
 * HOW: the sign-in page is Okta inside an embedded browser (WebView2); it is filled through Windows UI Automation
 * (ValuePattern / InvokePattern) - no mouse, no keyboard. See pages.login.DesktopLoginPage.
 *
 * =====================================================================================
 *  BEFORE YOU RUN - CHECKLIST (config.properties, NOT in this file)
 * =====================================================================================
 *   [ ] user.email           -> your AVAIL login email
 *   [ ] user.password        -> your CURRENT password (empty = TC_D006 is skipped)
 *   [ ] user.invalidPassword -> any WRONG password (empty = TC_D005 is skipped). NOT your real password.
 *  CAUTION: TC_D005 makes a failed sign-in attempt. Many failed attempts in a short time can lock the account in
 *           Okta - don't run it repeatedly in a loop (same caution as AVAIL Stream TC_002).
 *  Run alone: mvn test -Dtest=DesktopLoginTest
 * =====================================================================================
 */
public class DesktopLoginTest extends BaseTest {

	/** Skips the test unless the sign-in screen is shown (the tests never sign out). */
	private DesktopLoginPage requireSignedOut() {
		DesktopLoginPage login = new DesktopLoginPage(driver);
		// the sign-in view can be missing from the UI tree for a moment (e.g. right after a failed attempt while
		// the embedded browser reloads), so look for up to 10 s before deciding "already signed in"
		if (!WaitUtil.within(login::isLoginScreenShown, WaitUtil.scale(10000)))
			throw new SkipException("AVAIL Desktop is already signed in - sign out by hand to run the sign-in tests");
		login.waitForSignInPage(WaitUtil.scale(60000));
		return login;
	}

	/** Required config value for a sign-in test; skips the test when it is empty. */
	private static String required(String key) {
		String value = ConfigReader.get(key, "");
		if (value.isEmpty())
			throw new SkipException("Set " + key + " in config.properties to run this test");
		return value;
	}

	@Test(priority = 1, description = "TC_D001 - Verify AVAIL Desktop main window is running and attached")
	public void mainWindowFound() {
		DesktopHomePage home = new DesktopHomePage(driver);
		String title = home.getWindowTitle();
		String id = home.getWindowAutomationId();
		Log.step("Window title '{}', AutomationId '{}'", title, id);
		Assert.assertEquals(title, ConfigReader.get("desktop.window.title", "AVAIL Desktop"), "Wrong window title.");
		Assert.assertEquals(id, "mMainWindow", "The attached window is not the AVAIL Desktop main window (a dialog?).");
	}

	/**
	 * TC_D004 - the sign-in screen shows an Okta step that can be completed:
	 *   EMAIL step    -> Email box + Next button + "Sign in with ArchVision" link
	 *   PASSWORD step -> Password box + Sign In button (Okta stays here after a wrong password, e.g. after TC_D005)
	 * Either step is accepted; the step found is written to the report. Waits up to 30 s for one of them, because
	 * the embedded browser needs a moment to (re)load the Okta page.
	 */
	@Test(priority = 2, description = "TC_D004 - Verify sign-in screen shows the Okta Email step or Password step")
	public void signInScreenShown() {
		DesktopLoginPage login = requireSignedOut();
		WaitUtil.within(() -> !login.getCurrentStep().isEmpty(), WaitUtil.scale(30000));
		String step = login.getCurrentStep();
		Log.step("Okta step shown: '{}'", step.isEmpty() ? "none" : step);
		Assert.assertFalse(step.isEmpty(),
				"Neither the Okta Email step (Email + Next) nor the Password step (Password + Sign In) is shown on the sign-in screen.");
		if (step.equals("email"))
			Assert.assertTrue(login.isArchVisionLinkShown(), "'Sign in with ArchVision' link is not shown on the Email step.");
		attachScreenshot("AVAIL Desktop sign-in screen (" + step + " step)");
	}

	@Test(priority = 3, description = "TC_D005 - Verify sign-in fails with an invalid password")
	public void invalidPasswordRejected() {
		DesktopLoginPage login = requireSignedOut();
		String email = required("user.email");
		String wrongPassword = required("user.invalidPassword");
		login.login(email, wrongPassword);
		String message = login.getErrorMessage(WaitUtil.scale(30000));
		Log.step("Message shown: '{}'", message);
		Assert.assertTrue(message.contains(DesktopLoginPage.FAILED_LOGIN_MESSAGE),
				"Expected an 'Unable to sign in...' message, but got: '" + message + "'");
		Assert.assertTrue(login.isLoginScreenShown(), "The sign-in screen closed although the password was wrong.");
	}

	@Test(priority = 4, description = "TC_D006 - Verify user can sign in with valid credentials")
	public void validSignIn() {
		DesktopLoginPage login = requireSignedOut();
		login.login(required("user.email"), required("user.password"));
		login.waitForSignedIn(WaitUtil.scale(90000));
		DesktopHomePage home = new DesktopHomePage(driver);
		home.waitForLoaded(WaitUtil.scale(60000));
		List<String> tabs = home.getTabNames();
		Log.step("Signed in - top tabs: {}", tabs);
		Assert.assertFalse(login.isLoginScreenShown(), "The sign-in screen is still shown after a valid sign-in.");
		Assert.assertTrue(tabs.contains("Channels"), "'Channels' tab is not shown after sign-in. Tabs: " + tabs);
	}

	@Test(priority = 5, description = "TC_D002 - Verify signed-in UI shows Channels and Marketplace tabs and the left panel")
	public void signedInUiShown() {
		DesktopLoginPage login = new DesktopLoginPage(driver);
		// the main tabs also exist UNDER the sign-in screen, so first make sure nobody has to sign in
		Assert.assertFalse(login.isLoginScreenShown(),
				"AVAIL Desktop shows the sign-in screen - sign in first (or run TC_D006 with user.email / user.password).");
		DesktopHomePage home = new DesktopHomePage(driver);
		home.waitForLoaded(WaitUtil.scale(30000));
		List<String> tabs = home.getTabNames();
		Log.step("Top tabs: {}", tabs);
		Assert.assertTrue(tabs.contains("Channels"), "'Channels' tab is missing. Tabs: " + tabs);
		Assert.assertTrue(tabs.contains("Marketplace"), "'Marketplace' tab is missing. Tabs: " + tabs);
		Assert.assertTrue(home.isLeftPanelShown(), "Left panel with Home / Palette / Stream buttons is not shown.");
	}

	@Test(priority = 6, description = "TC_D003 - Verify Favorites section and status bar are shown")
	public void favoritesAndStatusBarShown() {
		Assert.assertFalse(new DesktopLoginPage(driver).isLoginScreenShown(), "AVAIL Desktop shows the sign-in screen - sign in first.");
		DesktopHomePage home = new DesktopHomePage(driver);
		Assert.assertTrue(home.isFavoritesSectionShown(), "FAVORITES section is not shown in the left panel.");
		Log.step("Favorite channels: {}", home.getFavoriteCount());
		Assert.assertTrue(home.isStatusBarShown(), "Status bar is not shown.");
	}
}
