package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.aventstack.extentreports.ExtentTest;

/**
 * STRUCTURED LOGGING - one call writes to the console, test-output/logs/automation.log AND the Extent report.
 *
 * NEW in AVAIL Desktop (AVAIL Stream only wrote test.info(...) into the Extent report, SLF4J had no provider).
 *
 *   Log.step("Opening channel {}", channelName);   -> INFO  + Extent "info"
 *   Log.info("Found {} items", count);             -> INFO  (log file only - details that would clutter the report)
 *   Log.warn("Retrying ...");                      -> WARN  + Extent "warning"
 *   Log.error("Could not ...", exception);         -> ERROR (with stack trace) + Extent "warning"
 *                                                     (only assertions decide pass / fail)
 *
 * Every log line carries the current test name (MDC key "test"), see src/test/resources/logback-test.xml:
 *   10:42:01.123 | INFO  | main       | channelsTabShown             | ChannelValidationTest  | Opening channel X
 */
public final class Log {

	private Log() {
	}

	/** Logger named after the class that called Log (so the "logger" column shows e.g. ChannelPage). */
	private static Logger logger() {
		StackTraceElement[] st = Thread.currentThread().getStackTrace();
		String caller = st.length > 3 ? st[3].getClassName() : "Log";
		return LoggerFactory.getLogger(caller);
	}

	/** Sets the test name shown in every log line (called by BaseTest / TestLogListener). */
	public static void setTest(String testName) {
		if (testName == null)
			MDC.remove("test");
		else
			MDC.put("test", testName);
	}

	/** A test STEP: log file + console + Extent report. */
	public static void step(String message, Object... args) {
		String text = format(message, args);
		logger().info(text);
		ExtentTest t = ExtentReportsManager.current();
		if (t != null)
			t.info(text);
	}

	/** Detail for the log file / console only (not shown in the Extent report). */
	public static void info(String message, Object... args) {
		logger().info(format(message, args));
	}

	/** Debug detail (hidden unless the logback level is DEBUG). */
	public static void debug(String message, Object... args) {
		logger().debug(format(message, args));
	}

	/** Warning: log + Extent "warning" (does NOT fail the test). */
	public static void warn(String message, Object... args) {
		String text = format(message, args);
		logger().warn(text);
		ExtentTest t = ExtentReportsManager.current();
		if (t != null)
			t.warning(text);
	}

	/** Error with exception: log (with stack trace) + Extent "warning". Assertions still decide pass / fail. */
	public static void error(String message, Throwable e) {
		logger().error(message, e);
		ExtentTest t = ExtentReportsManager.current();
		if (t != null)
			t.warning(message + ": " + e.getMessage());
	}

	/** "{}" placeholders like SLF4J, so the Extent text matches the log line. */
	private static String format(String message, Object... args) {
		if (args == null || args.length == 0)
			return message;
		StringBuilder sb = new StringBuilder();
		int argIndex = 0, from = 0, at;
		while ((at = message.indexOf("{}", from)) >= 0 && argIndex < args.length) {
			sb.append(message, from, at).append(args[argIndex++]);
			from = at + 2;
		}
		return sb.append(message.substring(from)).toString();
	}
}
