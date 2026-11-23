package utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads src/test/resources/config.properties.
 * (REUSED UNCHANGED from the AVAIL Stream framework - only the comments were adapted to AVAIL Desktop.)
 * A -Dkey=value on the mvn command line overrides the file value.
 *
 * HOW TO USE (in tests / BaseTest):
 *   ConfigReader.get("desktop.window.title")         -> value; STOPS the test if the key is missing
 *   ConfigReader.get("video.mode", "fail")           -> value, or "fail" if the key is missing
 *
 * WHERE ARE THE INPUT VALUES?
 *   In src/test/resources/config.properties - NOT in this class.
 *   If that file does not exist, copy config.properties.example to config.properties and fill it in.
 *   config.properties is git-ignored, so your password is never pushed to GitHub.
 *
 * OVERRIDE FOR ONE RUN (without editing the file):
 *   mvn test -Dtest=ChannelValidationTest -Dvideo.mode=all -Dscreenshot.mode=all
 *   Priority: -D command-line value  >  config.properties value  >  default value in the code.
 *
 * NOTE: the file is read ONCE (on the first get) and then kept in memory for the whole run.
 *       Changes to config.properties are used from the NEXT run.
 */
public class ConfigReader {

	private static Properties properties;

	/**
	 * Loads config.properties from the classpath (src/test/resources).
	 * CALLED FROM: ConfigReader.get(key, defaultValue) only - the FIRST time any value is read in a run.
	 */
	private static void load()
	{
		properties = new Properties();
		try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream("config.properties")) {
			if (input == null) {
				// Fix: copy src/test/resources/config.properties.example to config.properties
				throw new RuntimeException("config.properties not found in src/test/resources");
			}
			properties.load(input);
		} catch (IOException e) {
			throw new RuntimeException("Unable to read config.properties", e);
		}
	}

	/**
	 * Returns the value of a REQUIRED key.
	 * Fails with "Key '...' not found in config.properties" if it is missing - add the key to the file.
	 * CALLED FROM: BaseTest (desktop.window.title) and AvailDesktopApp.launch() (desktop.app.path).
	 */
	public static String get(String key)
	{
		String value = get(key, null);
		if (value == null) {
			throw new RuntimeException("Key '" + key + "' not found in config.properties");
		}
		return value;
	}

	/**
	 * Returns the value of an OPTIONAL key, or defaultValue if it is missing.
	 * Spaces at the start/end of the value are removed.
	 * CALLED FROM: ConfigReader.get(key) (with null default), BaseTest (screenshot.mode, video.mode, desktop.*),
	 *   WaitUtil (timeout.*), RetryAnalyzer (retry.count) and the tests (desktop.channel, desktop.sync.*).
	 */
	public static String get(String key, String defaultValue)
	{
		if (properties == null) {
			load();
		}
		// -Dkey=value (System property) wins over the file
		String value = System.getProperty(key, properties.getProperty(key));
		return value == null ? defaultValue : value.trim();
	}
}
