package utils;

/**
 * When to attach screenshots / videos to the Extent report.
 * Set in config.properties (screenshot.mode, video.mode) or override from the command line,
 * e.g. mvn test -Dvideo.mode=all
 *
 * INPUT (config.properties) - allowed values (upper/lower case does not matter):
 *   screenshot.mode=off | fail | all
 *   video.mode=off | fail | all
 *
 *   value | passed test | failed test
 *   ------+-------------+------------
 *   off   |     no      |     no
 *   fail  |     no      |     yes      (default)
 *   all   |     yes     |     yes
 *
 * Any other value (e.g. "yes", "true") stops the test with "Invalid media mode".
 * No code change is needed here - just change the values in config.properties.
 */
public enum MediaMode {

	/** never capture */
	OFF,
	/** capture only for failed tests */
	FAIL,
	/** capture for passed and failed tests */
	ALL;

	/**
	 * Converts the text from config.properties ("off" / "fail" / "all") to a MediaMode.
	 * CALLED FROM: BaseTest.setUp() - reads screenshot.mode and video.mode before every test.
	 */
	public static MediaMode from(String value) {
		switch (value.trim().toLowerCase()) {
		case "off":
			return OFF;
		case "fail":
			return FAIL;
		case "all":
			return ALL;
		default:
			throw new IllegalArgumentException("Invalid media mode '" + value + "'. Use: off, fail or all");
		}
	}

	/**
	 * Should media be kept for a test with this result?
	 * CALLED FROM: BaseTest.teardown() (AVAIL Desktop window screenshot / video).
	 * (REUSED UNCHANGED from the AVAIL Stream framework.)
	 */
	public boolean shouldCapture(boolean testPassed) {
		return this == ALL || (this == FAIL && !testPassed);
	}
}
