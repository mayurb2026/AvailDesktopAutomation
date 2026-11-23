package desktop;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import utils.Log;

/**
 * THE "BROWSER" OF THIS FRAMEWORK - a session with the AVAIL Desktop window through Windows UI Automation.
 *
 * WHY: AVAIL Desktop is a native WPF app. Playwright only automates browsers, so (like AvailDesktopApp in the
 * AVAIL Stream framework) the app is driven through Windows UI Automation from PowerShell. Difference to AVAIL
 * Stream: ONE PowerShell process (src/test/resources/scripts/DesktopDriver.ps1) is started per test class and
 * kept running, instead of a new PowerShell for every call - each command then takes milliseconds, not seconds.
 *
 * PROTOCOL: Java writes one JSON request per line to the script's STDIN, the script answers one JSON line.
 *   {"cmd":"find","parent":"w0","automationId":"ContentListView"}  ->  {"ok":true,"result":[{...}]}
 *
 * You normally do NOT call this class from tests or pages - use DesktopLocator (Playwright-style):
 *   DesktopLocator list = driver.window().byAutomationId("ContentListView");
 *   list.byControlType("ListItem").count();
 *
 * LIFECYCLE: BaseTest creates it in @BeforeClass (start + attach) and closes it in @AfterClass.
 * THREAD SAFETY: one driver per test class instance; send() is synchronized.
 */
public class DesktopDriver implements AutoCloseable {

	/** Location of the UI Automation driver script (relative to the project folder). */
	private static final Path SCRIPT = Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "scripts", "DesktopDriver.ps1");

	/** Max time for ONE command (find over the whole window tree is usually < 1 s). */
	private static final long COMMAND_TIMEOUT_MS = 60000;

	private final Gson gson = new Gson();
	private final BlockingQueue<String> responses = new LinkedBlockingQueue<>();
	private final StringBuilder stderr = new StringBuilder();
	private Process process;
	private BufferedWriter stdin;
	private DesktopElement window;
	private long nextRequestId = 1;

	/**
	 * Starts the PowerShell UI Automation session (does not touch the app yet - call attach()).
	 *   -NoProfile                -> don't load the user's PowerShell profile (faster, same on every PC)
	 *   -ExecutionPolicy Bypass   -> allows running the script without changing Windows settings
	 */
	public DesktopDriver start() {
		try {
			process = new ProcessBuilder(Arrays.asList("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
					"-File", SCRIPT.toString())).start();
			stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
			Thread out = new Thread(() -> pump(new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)), true), "uia-out");
			Thread err = new Thread(() -> pump(new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)), false), "uia-err");
			out.setDaemon(true);
			err.setDaemon(true);
			out.start();
			err.start();
			String pong = send(Map.of("cmd", "ping")).getAsString();
			Log.info("UI Automation driver started ({}, ping={})", SCRIPT.getFileName(), pong);
			return this;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Could not start " + SCRIPT.getFileName() + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Finds the top-level window by its title (e.g. "AVAIL Desktop") and makes it the root for all locators.
	 * Fails with "Window '...' not found - is AVAIL Desktop running and logged in?" after timeoutMs.
	 */
	public DesktopElement attach(String windowTitle, long timeoutMs) {
		return attach(windowTitle, "", timeoutMs);
	}

	/**
	 * Same, but the window must ALSO have this AutomationId (e.g. "mMainWindow"), so a splash screen or dialog with
	 * the same title is never picked. Empty automationId = title only.
	 */
	public DesktopElement attach(String windowTitle, String automationId, long timeoutMs) {
		JsonElement r = send(Map.of("cmd", "attach", "title", windowTitle, "automationId", automationId, "timeoutMs", timeoutMs));
		window = gson.fromJson(r, DesktopElement.class);
		Log.info("Attached to window '{}' ({}x{})", windowTitle, window.width(), window.height());
		return window;
	}

	/** Root locator = the attached main window. All page locators start here. */
	public DesktopLocator window() {
		if (window == null)
			throw new IllegalStateException("Not attached - BaseTest attaches to AVAIL Desktop before each test class");
		return DesktopLocator.root(this);
	}

	/** Info of the attached window (refreshed). */
	public DesktopElement windowInfo() {
		return info("w0");
	}

	// ------------------------------------------------------------------ commands used by DesktopLocator

	/** Finds elements under parentId (see DesktopLocator for the criteria). Returns an empty list when none. */
	List<DesktopElement> find(String parentId, Map<String, Object> criteria, boolean all) {
		Map<String, Object> req = new java.util.LinkedHashMap<>(criteria);
		req.put("cmd", "find");
		req.put("parent", parentId);
		req.put("all", all);
		List<DesktopElement> list = new ArrayList<>();
		for (JsonElement e : send(req).getAsJsonArray())
			list.add(gson.fromJson(e, DesktopElement.class));
		return list;
	}

	DesktopElement info(String id) {
		return gson.fromJson(send(Map.of("cmd", "info", "id", id)), DesktopElement.class);
	}

	String text(String id) {
		JsonElement r = send(Map.of("cmd", "text", "id", id));
		return r == null || r.isJsonNull() ? "" : r.getAsString();
	}

	/** Returns how it was clicked: invoke / toggle / select / expand / mouse. */
	String click(String id) {
		return send(Map.of("cmd", "click", "id", id)).getAsString();
	}

	void select(String id) {
		send(Map.of("cmd", "select", "id", id));
	}

	void setValue(String id, String value) {
		send(Map.of("cmd", "setValue", "id", id, "value", value));
	}

	void expand(String id) {
		send(Map.of("cmd", "expand", "id", id));
	}

	/** Real mouse double-click - REFUSED by the script unless AVAIL Desktop is the foreground window. */
	void doubleClick(String id) {
		send(Map.of("cmd", "doubleClick", "id", id));
	}

	void scrollIntoView(String id) {
		send(Map.of("cmd", "scrollIntoView", "id", id));
	}

	/** Scrolls a list to verticalPercent (0..100). Returns {scrollable, percent, viewSize}. */
	JsonObject scroll(String id, double verticalPercent) {
		return send(Map.of("cmd", "scroll", "id", id, "verticalPercent", verticalPercent)).getAsJsonObject();
	}

	/** True if AVAIL Desktop is the active (foreground) window right now. */
	public boolean isForeground() {
		return send(Map.of("cmd", "isForeground")).getAsBoolean();
	}

	/** Maximizes + brings the attached window to the front; returns its fresh info (position in physical pixels). */
	public DesktopElement show() {
		return gson.fromJson(send(Map.of("cmd", "show", "id", "w0")), DesktopElement.class);
	}

	/** Lets the script forget old element handles (memory) - BaseTest calls it after every test. */
	public void release() {
		send(Map.of("cmd", "release"));
	}

	// ------------------------------------------------------------------ plumbing

	/** Sends one command and waits for its answer. Throws DesktopException with the script's error message. */
	synchronized JsonElement send(Map<String, Object> request) {
		if (process == null || !process.isAlive())
			throw new DesktopException("UI Automation driver is not running" + (stderr.length() > 0 ? ": " + stderr : ""));
		// every request gets a number; the script echoes it, so a LATE answer (e.g. after a timeout) is ignored
		// instead of being taken as the answer to the next command
		long rid = nextRequestId++;
		Map<String, Object> numbered = new java.util.LinkedHashMap<>(request);
		numbered.put("rid", rid);
		String line = gson.toJson(numbered);
		// text typed into fields (e.g. a password) must never appear in error messages / logs / the report
		String shown = request.containsKey("value") ? gson.toJson(request).replaceAll("\"value\":\"(?:[^\"\\\\]|\\\\.)*\"", "\"value\":\"***\"") : gson.toJson(request);
		// commands with their own wait (attach timeoutMs) may take that long + a margin
		long waitMs = COMMAND_TIMEOUT_MS;
		if (request.get("timeoutMs") instanceof Number)
			waitMs = Math.max(waitMs, ((Number) request.get("timeoutMs")).longValue() + 15000);
		try {
			stdin.write(line);
			stdin.newLine();
			stdin.flush();
			long deadline = System.currentTimeMillis() + waitMs;
			JsonObject json = null;
			while (json == null) {
				long left = deadline - System.currentTimeMillis();
				String answer = left > 0 ? responses.poll(left, TimeUnit.MILLISECONDS) : null;
				if (answer == null)
					throw new DesktopException("No answer from the UI Automation driver within " + waitMs + " ms for " + shown);
				JsonObject candidate = JsonParser.parseString(answer).getAsJsonObject();
				if (candidate.has("rid") && candidate.get("rid").getAsLong() != rid) {
					Log.debug("Ignoring late answer of request {}", candidate.get("rid").getAsLong());
					continue;
				}
				json = candidate;
			}
			if (!json.get("ok").getAsBoolean())
				throw new DesktopException(json.get("error").getAsString() + "  [request: " + shown + "]");
			return json.get("result");
		} catch (DesktopException e) {
			throw e;
		} catch (Exception e) {
			throw new DesktopException("UI Automation driver failed: " + e.getMessage() + "  [request: " + shown + "]", e);
		}
	}

	private void pump(BufferedReader reader, boolean isOut) {
		try {
			String l;
			while ((l = reader.readLine()) != null) {
				if (isOut) {
					if (l.startsWith("{"))
						responses.add(l);
				} else {
					synchronized (stderr) {
						stderr.append(l).append(' ');
					}
				}
			}
		} catch (Exception ignored) {
			// process ended
		}
	}

	/** Ends the PowerShell session (BaseTest @AfterClass). Never throws. */
	@Override
	public void close() {
		try {
			if (process != null && process.isAlive()) {
				send(Map.of("cmd", "quit"));
				process.waitFor(5, TimeUnit.SECONDS);
			}
		} catch (Exception ignored) {
			// fall through to destroy
		} finally {
			if (process != null && process.isAlive())
				process.destroyForcibly();
		}
	}
}
