package tests.search;

import java.util.List;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import base.BaseTest;
import pages.search.SearchPage;
import utils.ConfigReader;
import utils.Log;

/**
 * TC_D020 - TC_D021 - SEARCH in AVAIL Desktop.
 *
 * *** DISABLED (enabled = false) until the search box locator is confirmed - see pages.search.SearchPage. ***
 * On 23-Nov-2026 no search text box was found in the Channels view, so these tests would test a guess.
 *
 * WHAT WILL BE VERIFIED
 *   TC_D020 searching an existing file name lists that file
 *   TC_D021 searching a text that matches nothing lists no file
 *
 * INPUT (config.properties): desktop.search.existingFile (a file name that exists in AVAIL Desktop)
 */
public class DesktopSearchTest extends BaseTest {

	@Test(enabled = false, priority = 1, description = "TC_D020 - Verify search finds an existing file")
	public void searchExistingFile() {
		SearchPage search = new SearchPage(driver);
		String file = ConfigReader.get("desktop.search.existingFile", "");
		if (file.isEmpty())
			throw new SkipException("Set desktop.search.existingFile in config.properties");
		Assert.assertTrue(search.isSearchBoxShown(), "Search box is not shown.");
		search.search(file);
		List<String> results = search.getResultNames();
		Log.step("Results for '{}': {}", file, results);
		Assert.assertTrue(results.stream().anyMatch(r -> r.contains(file)), "'" + file + "' is not in the results: " + results);
	}

	@Test(enabled = false, priority = 2, description = "TC_D021 - Verify search with no match shows no results")
	public void searchNoMatch() {
		SearchPage search = new SearchPage(driver);
		Assert.assertTrue(search.isSearchBoxShown(), "Search box is not shown.");
		search.search("zz_no_such_file_9876");
		Assert.assertTrue(search.getResultNames().isEmpty(), "Results shown for a text that matches nothing.");
	}
}
