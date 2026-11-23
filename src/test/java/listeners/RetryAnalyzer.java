package listeners;

import java.util.concurrent.atomic.AtomicInteger;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import utils.ConfigReader;
import utils.Log;

/**
 * Decides whether a FAILED test is run again.
 *
 * NEW in AVAIL Desktop (AVAIL Stream has no retry). Desktop UI tests can fail for reasons outside the app
 * (another window popped up, the app was still loading a channel), so one retry can help on a slow PC.
 *
 * INPUT (config.properties or -D): retry.count = how many extra attempts (default 0 = never retry).
 *   mvn test -Dretry.count=1
 *
 * Attached to EVERY test automatically by RetryListener (registered in testng.xml) - no change in tests needed.
 * NOTE: the Extent report shows each attempt; only the last attempt's result counts in the TestNG summary.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

	private final AtomicInteger attempts = new AtomicInteger();

	@Override
	public boolean retry(ITestResult result) {
		int max = Integer.parseInt(ConfigReader.get("retry.count", "0"));
		if (attempts.get() < max) {
			int attempt = attempts.incrementAndGet();
			Log.warn("RETRY {} of {} for {} - failed with: {}", attempt, max, result.getMethod().getMethodName(),
					result.getThrowable() == null ? "?" : result.getThrowable().getMessage());
			return true;
		}
		return false;
	}
}
