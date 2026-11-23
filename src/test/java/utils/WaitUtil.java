package utils;

import java.util.function.Supplier;

/**
 * ONE PLACE FOR ALL WAIT TIMES + a polling wait for the desktop app.
 *
 * ADAPTED FROM AVAIL Stream utils.Timeouts (same "timeout.multiplier" idea) + NEW polling helpers,
 * because UI Automation has no auto-wait like Playwright's locators.
 *
 * INPUT (config.properties, or -D on the mvn command line):
 *   timeout.multiplier   -> multiplies EVERY wait (default 1). Slow PC / VM? Use 2 or 3. Decimals allowed.
 *   timeout.default.ms   -> wait used by DesktopLocator.waitFor() / click() when the page gives none (default 15000)
 *
 * HOW TO USE (in page classes / tests):
 *   WaitUtil.until(() -> channelPage.getItemCount() > 0, 30000, "items in the channel");
 *   locator.waitFor(WaitUtil.scale(10000));
 */
public final class WaitUtil {

	/** How often a condition is checked again while waiting (ms). */
	private static final long POLL_MS = 500;

	private WaitUtil() {
	}

	/**
	 * timeout.multiplier from config (default 1). Fails clearly on a wrong value such as "two".
	 * CALLED FROM: WaitUtil.scale() and BaseTest.setUp() (to log the value in the report).
	 */
	public static double multiplier() {
		String value = ConfigReader.get("timeout.multiplier", "1");
		try {
			double m = Double.parseDouble(value);
			if (m <= 0)
				throw new NumberFormatException();
			return m;
		} catch (NumberFormatException e) {
			throw new RuntimeException("timeout.multiplier must be a positive number (e.g. 1, 1.5, 2), but was '" + value + "'");
		}
	}

	/** Returns baseMillis * timeout.multiplier (as long). */
	public static long scale(long baseMillis) {
		return Math.round(baseMillis * multiplier());
	}

	/** timeout.default.ms (already multiplied) - used when a page does not give its own timeout. */
	public static long defaultTimeout() {
		return scale(Long.parseLong(ConfigReader.get("timeout.default.ms", "15000")));
	}

	/**
	 * Waits until the condition is true; checks every 500 ms. Exceptions inside the condition count as "not yet".
	 * Fails with "Timed out after N ms waiting for <what>" (the last exception is attached).
	 * @param timeoutMs already-scaled time, e.g. WaitUtil.scale(30000)
	 */
	public static void until(Supplier<Boolean> condition, long timeoutMs, String what) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		RuntimeException last = null;
		while (true) {
			try {
				if (Boolean.TRUE.equals(condition.get()))
					return;
				last = null;
			} catch (RuntimeException e) {
				last = e;
			}
			if (System.currentTimeMillis() >= deadline)
				throw new RuntimeException("Timed out after " + timeoutMs + " ms waiting for " + what
						+ (last != null ? " - last error: " + last.getMessage() : ""), last);
			sleep(POLL_MS);
		}
	}

	/**
	 * Same as until() but returns false instead of failing - for optional checks ("is it shown within 5 s?").
	 */
	public static boolean within(Supplier<Boolean> condition, long timeoutMs) {
		try {
			until(condition, timeoutMs, "condition");
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Thread.sleep without the checked exception. Prefer until() - use this only for fixed UI settle times. */
	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting", e);
		}
	}
}
