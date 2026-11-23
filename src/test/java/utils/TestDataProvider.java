package utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.testng.annotations.DataProvider;

/**
 * TestNG DATA PROVIDERS - test data in CSV files (src/test/resources/testdata/*.csv), not in the test code.
 *
 * NEW in AVAIL Desktop (AVAIL Stream kept its test data in config.properties / the test classes).
 *
 * CSV FORMAT: first line = column names, then one row per test run. Columns are passed IN ORDER as String
 * parameters. Lines starting with # are ignored. No quotes / commas inside values (keep it simple).
 *
 *   testdata/channels.csv:
 *     channelName,expectAtLeastOneItem
 *     Automation Testing Channel,true
 *
 * USE IN A TEST:
 *   @Test(dataProvider = "channels", dataProviderClass = TestDataProvider.class)
 *   public void openChannel(String channelName, String expectAtLeastOneItem) { ... }
 *
 * ADD A NEW DATA SET: add testdata/<name>.csv and a @DataProvider method below that calls csv("<name>.csv").
 */
public final class TestDataProvider {

	private TestDataProvider() {
	}

	/** Channels to open - testdata/channels.csv (channelName, expectAtLeastOneItem). */
	@DataProvider(name = "channels")
	public static Object[][] channels() {
		return csv("channels.csv");
	}

	/**
	 * Reads testdata/<fileName> from the classpath into Object[][] (one row per data line, header skipped).
	 * Fails with a clear message if the file is missing or a row has the wrong number of columns.
	 */
	public static Object[][] csv(String fileName) {
		String resource = "testdata/" + fileName;
		try (InputStream in = TestDataProvider.class.getClassLoader().getResourceAsStream(resource)) {
			if (in == null)
				throw new RuntimeException("Test data file not found: src/test/resources/" + resource);
			List<String> lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
					.map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).collect(Collectors.toList());
			if (lines.isEmpty())
				throw new RuntimeException(resource + " is empty (needs a header line)");
			int columns = lines.get(0).split(",", -1).length;
			List<Object[]> rows = new ArrayList<>();
			for (int i = 1; i < lines.size(); i++) {
				String[] values = lines.get(i).split(",", -1);
				if (values.length != columns)
					throw new RuntimeException(resource + " line " + (i + 1) + " has " + values.length + " values, header has " + columns);
				for (int v = 0; v < values.length; v++)
					values[v] = values[v].trim();
				rows.add(values);
			}
			return rows.toArray(new Object[0][]);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Could not read " + resource + ": " + e.getMessage(), e);
		}
	}
}
