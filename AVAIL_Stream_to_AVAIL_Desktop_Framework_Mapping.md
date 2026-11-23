# AVAIL Stream → AVAIL Desktop: Framework Mapping

| | AVAIL Stream | AVAIL Desktop |
|---|---|---|
| Location | `D:\AvailAutomation\Playwright2\AvailStream` | `D:\AvailAutomation\Playwright2\AVAILDesktopAutomation` |
| Application | Web app (Angular) | Native Windows app (WPF, `Avail.exe`) |
| Automation engine | Playwright Java (Chromium) | **Windows UI Automation** via one long-running PowerShell session (`DesktopDriver.ps1`) driven from Java |
| Locator API | `com.microsoft.playwright.Locator` | `desktop.DesktopLocator` (same lazy, Playwright-style API) |
| Test runner | TestNG 7.12, Surefire 3.6.0 (scans classes, ignores `testng.xml`) | TestNG 7.12, **Surefire 3.5.2** (runs `testng.xml`, so listeners and order apply) |
| Reports | Extent + Excel (POI) | Extent (Excel report: future) |
| Evidence | Playwright screenshot/video + `ScreenRecorder` for Desktop | `ScreenshotUtil` (Robot) + `ScreenRecorder` (MP4) of the app window |

## Why not Playwright for AVAIL Desktop?
Playwright automates **browsers only**. AVAIL Desktop is a WPF application, so it is read and driven through
**Windows UI Automation**, the accessibility API built into Windows (nothing to install). This is the same technique the
AVAIL Stream framework already used in `desktop/AvailDesktopApp.java` + `scripts/AvailDesktop.ps1`.
The difference: AVAIL Stream started a **new PowerShell for every call**. AVAIL Desktop starts **one per test class** and keeps it
running (JSON request/response over stdin/stdout), so each command takes milliseconds.
Playwright Java stays in `pom.xml` for future **hybrid** tests (do something in AVAIL Stream, then check AVAIL Desktop).

Alternatives considered: WinAppDriver (unmaintained, needs Developer Mode), Appium Windows driver (needs Node + Appium
server), Java UIA via JNA (extra native library). The PowerShell UI Automation route was chosen because it needs nothing
installed and is already proven against AVAIL Desktop on this machine.

## 1. Reused classes (copied; only the comments were adapted)
| Class | From (AVAIL Stream) | Notes |
|---|---|---|
| `utils.ConfigReader` | `utils.ConfigReader` | Same `config.properties` + `-D` override logic |
| `utils.MediaMode` | `utils.MediaMode` | Same `off` / `fail` / `all` for screenshots and videos |
| `utils.ScreenRecorder` | `utils.ScreenRecorder` | Same Robot + JCodec MP4 recorder (5 fps) |
| DPI / multi-monitor conversion | `AvailDesktopApp.toScreenCoordinates` | Now in `desktop.AvailDesktopApp` |
| Window "bring to front" logic | `AvailDesktop.ps1 -Action Show` | Now the `show` command of `DesktopDriver.ps1` |
| Visible-text rule (TextBlock before Name, WPF `_` issue) | `AvailDesktop.ps1 Get-Text` | Now in `DesktopDriver.ps1` |

## 2. Modified classes (same role, adapted to the desktop)
| AVAIL Desktop | From | What changed |
|---|---|---|
| `base.BaseTest` | `base.BaseTest` | Desktop session per class (`@BeforeClass` start + attach, `@AfterClass` close) instead of a browser per test. The window is brought to front, recorded (MP4) and screenshotted per `MediaMode`. Same report flow and `shortReason()` |
| `base.BasePage` | (new parent for the Stream page pattern) | Pages get a `DesktopDriver`. Locators are still created in the constructor |
| `utils.ExtentReportsManager` | `utils.ExtentManager` | Engineer/environment come from config. Current test is kept per thread (`ThreadLocal`), so `Log` and listeners can write to it |
| `utils.ScreenshotUtil` | `utils.Screenshotutil` + `AvailDesktopApp.takeScreenshot` | Captures the app window (Robot). Returns path + Base64 (no static `lastSavedPath`, so it's safe for parallel runs) |
| `utils.WaitUtil` | `utils.Timeouts` | Same `timeout.multiplier`. Adds `until()` / `within()` polling (UI Automation has no auto-wait) |
| `desktop.AvailDesktopApp` | `desktop.AvailDesktopApp` | Uses the shared `DesktopDriver`. Adds `isRunning()` / `launch()`. Channel file listing moved to `ChannelPage` |
| `scripts/DesktopDriver.ps1` | `scripts/AvailDesktop.ps1` | Two fixed actions became a generic command session: find / click / text / setValue / select / scroll / doubleClick … |
| `pom.xml` | `pom.xml` | Same TestNG / Extent / Playwright / JCodec versions. Adds Gson, SLF4J + Logback. Surefire 3.5.2 with `suiteXmlFiles`. No POI (yet) |
| `testng.xml` | `testng.xml` | Desktop test areas + `<listeners>` (RetryListener, TestLogListener) |
| `config.properties` | `config.properties` | `desktop.*` keys, `retry.count`, `report.*`. No URLs or passwords |

## 3. Desktop-specific classes (new)
| Class | Purpose |
|---|---|
| `desktop.DesktopDriver` | Starts/talks to `DesktopDriver.ps1` (JSON over stdin/stdout), one session per test class |
| `desktop.DesktopLocator` | Playwright-style lazy locator: `byAutomationId`, `byControlType`, `byName`, `withText`, `containingText`, `nth`, `click`, `doubleClick`, `select`, `fill`, `text`, `count`, `allTexts`, `waitFor`, `scrollTo` |
| `desktop.DesktopElement` | Snapshot of one UI element (control type, name, AutomationId, class, text, bounds) |
| `desktop.DesktopException` | Clear driver errors ("Element not found: …", "mouse click refused …") |
| `utils.Log` | Structured logging: one call writes the console, `test-output/logs/automation.log` and the Extent report |
| `utils.TestDataProvider` | CSV data providers (`src/test/resources/testdata/*.csv`) |
| `listeners.RetryListener` + `RetryAnalyzer` | Re-run failed tests `retry.count` times (default 0) |
| `listeners.TestLogListener` | One log line per test start/result + summary per `<test>` |
| Pages: `DesktopLoginPage`, `DesktopHomePage`, `ChannelPage`, `SearchPage`, `ThumbnailPage`, `PropertiesPage` | Desktop page objects, no Stream locators |
| Tests: `DesktopLoginTest`, `ChannelValidationTest`, `DesktopSearchTest`, `ThumbnailValidationTest`, `PropertiesValidationTest`, `ChannelSyncTest` | Desktop test cases `TC_D001`–`TC_D050` |

### Sign-in (DesktopLoginTest TC_D004–D006)
AVAIL Desktop signs in through **Okta inside an embedded browser (WebView2)**. Windows UI Automation can read that web
page, so `DesktopLoginPage` fills Email → Next → Password → Sign In with ValuePattern/InvokePattern, with **no mouse
and no keyboard**. The tests **never sign out**: if AVAIL Desktop is already signed in, TC_D004–D006 are skipped.
Credentials: `user.email`, `user.password`, `user.invalidPassword` in the git-ignored `config.properties`. An empty
password skips that test, and the password is never logged.

### Safety built into the driver
A real mouse click lands on **whatever window is in front**. `DesktopDriver.ps1` therefore **refuses** every mouse
action (`doubleClick`, and `click` on elements without a UI Automation pattern) unless AVAIL Desktop is the
**foreground window**, and fails with a clear message instead. All other actions use UI Automation patterns
(Invoke / Toggle / SelectionItem / Value / Scroll), which never move the mouse.

## 4. Locator status (AVAIL Desktop INTERNAL BUILD, checked 23-Nov-2026)
| Area | Status | Locators |
|---|---|---|
| Sign-in screen | **Confirmed** (TC_D004 passes) | WPF `LoginFullView` + embedded WebView2 "AVAIL - Sign In" (Okta): `okta-sign-in`, Email `idp-discovery-username`, Next `idp-discovery-submit`, link "Sign in with ArchVision" |
| Sign-in password step | **To confirm** | matched by name: Edit containing "Password", Button "Sign In" (same Okta page as AVAIL Stream) |
| Main window / tabs / left panel | **Confirmed** (TC_D001–D003 pass when signed in) | `mMainWindow`, `mTabControl`, `TabItemHeader`, `cLeftSidePanel`, `HomeRadioBtn`, `PaletteRadioBtn`, `StreamRadioBtn`, `FavoritesListView`, `StatusBarView` |
| Channel list | **Confirmed** | `CrossChannelSideControl`, `ChannelListView` (virtualized), tile = ListItem `Avail.UI.ViewModels.ChannelViewModel` |
| Inside a channel | **Confirmed** | `ChannelControl`, `_BreadCrumbControl`, `ContentListView`, `FiltersControl`, `ViewModeToggleButton` |
| File tiles | Confirmed name pattern (from AVAIL Stream script) | ListItem `Avail.UI.ViewModels.ContentViewModel`, file name = Button text |
| Thumbnail inside a file tile | **To confirm** | assumed `Image` control (`ThumbnailPage.THUMBNAIL`) |
| Details panel contents | **To confirm** | panel `SidePanelView` / title "Details" confirmed; property labels unknown |
| Search box | **Not found** | `DesktopSearchTest` disabled until `SearchPage.SEARCH_BOX_ID` is set |

Use **Accessibility Insights for Windows** (or `Inspect.exe`) on a channel **with files** to confirm the last three.

## 5. Future enhancements
1. **Confirm the open locators** (thumbnail, Details labels, search box), then enable `DesktopSearchTest`.
2. **Excel report**: port `ExcelReportListener` + `ExcelSummarySheet` from AVAIL Stream (needs POI), with TC IDs `TC_Dxxx`.
3. **Hybrid sync test**: use Playwright (already in the pom) to create a definition / copy files via AVAIL Stream, then `ChannelSyncTest` checks AVAIL Desktop. This is the full end-to-end flow of Stream `FileUploadTest`.
4. **Sign-in**: done (TC_D004–D006). Still to do: confirm the password-step locators on a real run, add a sign-out test once the sign-out control is identified (top-bar buttons have no names/AutomationIds yet; ask developers to add them), and move credentials to a secure store (Windows Credential Manager / CI secrets).
5. **Virtualized file lists**: `ChannelPage.getFileNames()` reads the tiles that exist. For large channels, scroll `ContentListView` like `findChannelTile()` does.
6. **Parallel runs**: not useful for one desktop app on one PC (one foreground window). Run different machines/VMs instead.
7. **CI agent**: needs an interactive, unlocked desktop session (UI Automation and Robot don't work on a locked screen or a service session).
8. **Faster mouse-free channel opening**: ask the developers for an AutomationId / Invoke pattern on channel tiles, so opening a channel doesn't need a double-click.
