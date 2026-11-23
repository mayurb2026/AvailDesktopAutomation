# AVAILDesktopAutomation

UI test automation for the **AVAIL Desktop** Windows app. It uses the same architecture and standards as the AVAIL Stream
framework (`../AvailStream`): Java 17, Maven, TestNG 7.12, Page Object Model, Extent Reports, screenshots + video.
The app is driven through **Windows UI Automation** (AVAIL Desktop is a WPF app, which Playwright can't automate).
See [`AVAIL_Stream_to_AVAIL_Desktop_Framework_Mapping.md`](AVAIL_Stream_to_AVAIL_Desktop_Framework_Mapping.md).

## Before you run
- [ ] AVAIL Desktop is **running and signed in** on this PC.
- [ ] The screen is **unlocked**, and you **don't use the mouse/keyboard** during the run. The window is brought to the
      front and recorded, and opening a channel needs a double-click, which the driver refuses if another window is in front.
- [ ] `src/test/resources/config.properties` exists (copy `config.properties.example`) with `desktop.channel` set to a channel **with files**.
- [ ] Sign-in tests (TC_D004-D006) run only when AVAIL Desktop shows its sign-in screen: set `user.email`, `user.password`, `user.invalidPassword`. They never sign out.

## Run
| What | Command |
|---|---|
| All tests (`testng.xml`, in flow order, with listeners) | `mvn test` |
| One class | `mvn test -Dtest=ChannelValidationTest` |
| One test | `mvn test -Dtest=DesktopLoginTest#signedInUiShown` |
| Read-only check that never takes the focus | `mvn test -Dtest=DesktopLoginTest -Ddesktop.bringToFront=false -Dvideo.mode=off -Dscreenshot.mode=off` |
| Evidence for every test | `mvn test -Dscreenshot.mode=all -Dvideo.mode=all` |
| Retry failed tests once | `mvn test -Dretry.count=1` |

Output: `test-output/ExtentReport.html`, `test-output/screenshots`, `test-output/videos` (MP4), `test-output/logs/automation.log`.

## Structure
```
src/test/java
├── base          BaseTest (desktop session, report, screenshot, video), BasePage
├── desktop       DesktopDriver (UI Automation session), DesktopLocator, DesktopElement, AvailDesktopApp
├── pages
│   ├── login     DesktopLoginPage      (Okta sign-in inside the app)
│   ├── desktop   DesktopHomePage
│   ├── channels  ChannelPage
│   ├── search    SearchPage            (locator to confirm)
│   ├── thumbnails ThumbnailPage
│   └── properties PropertiesPage
├── tests
│   ├── login       DesktopLoginTest        TC_D001-D006 (sign-in tests run only when signed out)
│   ├── channels    ChannelValidationTest   TC_D010-D012
│   ├── search      DesktopSearchTest       TC_D020-D021 (disabled)
│   ├── thumbnails  ThumbnailValidationTest TC_D030-D031
│   ├── properties  PropertiesValidationTest TC_D040-D041
│   └── sync        ChannelSyncTest         TC_D050
├── utils         ConfigReader, ExtentReportsManager, ScreenshotUtil, ScreenRecorder, WaitUtil,
│                 TestDataProvider, MediaMode, Log
└── listeners     RetryListener, RetryAnalyzer, TestLogListener
src/test/resources
├── config.properties(.example)  logback-test.xml
├── scripts/DesktopDriver.ps1     UI Automation driver (JSON over stdin/stdout)
└── testdata/channels.csv
```

## Adding a page / test
1. Find locators with **Accessibility Insights for Windows**. AutomationId is best, then ControlType + text, then Name.
2. Page: `extends BasePage`, create locators in the constructor (`window.byAutomationId("...")`), add action methods, no asserts.
3. Test: `extends BaseTest`, `@Test(description = "TC_Dxxx - Verify ...")`, use `Log.step(...)` for steps, assert in the test.
4. Add the class to `testng.xml`.
