package listeners;

import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import utils.Log;

/**
 * One structured log line per test START / RESULT, and a summary per <test> of testng.xml.
 *
 * NEW in AVAIL Desktop (structured logging). Registered in testng.xml. Output: console +
 * test-output/logs/automation.log, e.g.
 *   10:42:01 | INFO | main | channelsTabShown | TestLogListener | START TC_D010 - Verify Channels tab ...
 *   10:42:09 | INFO | main | channelsTabShown | TestLogListener | PASS  TC_D010 (8.1 s)
 */
public class TestLogListener implements ITestListener {

	@Override
	public void onTestStart(ITestResult result) {
		Log.setTest(result.getMethod().getMethodName());
		Log.info("START {}", label(result));
	}

	@Override
	public void onTestSuccess(ITestResult result) {
		Log.info("PASS  {} ({} s)", label(result), seconds(result));
	}

	@Override
	public void onTestFailure(ITestResult result) {
		Log.info("FAIL  {} ({} s) - {}", label(result), seconds(result),
				result.getThrowable() == null ? "" : result.getThrowable().getMessage());
	}

	@Override
	public void onTestSkipped(ITestResult result) {
		Log.info("SKIP  {} - {}", label(result), result.getThrowable() == null ? "" : result.getThrowable().getMessage());
	}

	@Override
	public void onFinish(ITestContext context) {
		Log.setTest(null);
		Log.info("== {}: passed {}, failed {}, skipped {}", context.getName(), context.getPassedTests().size(),
				context.getFailedTests().size(), context.getSkippedTests().size());
	}

	private static String label(ITestResult r) {
		String d = r.getMethod().getDescription();
		return d == null || d.isEmpty() ? r.getMethod().getMethodName() : d;
	}

	private static String seconds(ITestResult r) {
		return String.format("%.1f", (r.getEndMillis() - r.getStartMillis()) / 1000.0);
	}
}
