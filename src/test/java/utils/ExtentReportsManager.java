package utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;

/**
 * Creates the Extent HTML report (one report for the whole run) and keeps the CURRENT test of each thread.
 *
 * ADAPTED FROM AVAIL Stream utils.ExtentManager:
 *   - same report / same getInstance() idea
 *   - texts are for AVAIL Desktop, engineer + environment come from config.properties
 *   - NEW: the current ExtentTest is kept per thread (ThreadLocal), so utils.Log and the listeners can write
 *     into the right test, and tests can later run in parallel (TestNG parallel="classes").
 *
 * REPORT LOCATION: test-output/ExtentReport.html (overwritten every run - copy it with test-output/videos to keep it).
 */
public final class ExtentReportsManager {

	private static ExtentReports extent;
	private static final ThreadLocal<ExtentTest> CURRENT = new ThreadLocal<>();

	private ExtentReportsManager() {
	}

	/**
	 * The single report of this run (created on the first call).
	 * CALLED FROM: BaseTest.setUp() (before every test) and BaseTest.teardown() (flush).
	 */
	public static synchronized ExtentReports getInstance() {
		if (extent == null) {
			ExtentSparkReporter reporter = new ExtentSparkReporter("test-output/ExtentReport.html");
			reporter.config().setDocumentTitle("AVAIL Desktop Automation Report");
			reporter.config().setReportName("AVAIL Desktop Automation Execution Report");

			extent = new ExtentReports();
			extent.attachReporter(reporter);
			// INPUT (config.properties): report.engineer / report.environment
			extent.setSystemInfo("Automation Engineer", ConfigReader.get("report.engineer", "QA"));
			extent.setSystemInfo("Framework", "Java + TestNG + Windows UI Automation (DesktopDriver)");
			extent.setSystemInfo("Application", "AVAIL Desktop");
			extent.setSystemInfo("Environment", ConfigReader.get("report.environment", "Dev"));
			extent.setSystemInfo("OS", System.getProperty("os.name") + " " + System.getProperty("os.version"));
		}
		return extent;
	}

	/** Creates the report entry of a test and makes it the current one of this thread. */
	public static ExtentTest startTest(String name, String description) {
		ExtentTest test = getInstance().createTest(name, description);
		CURRENT.set(test);
		return test;
	}

	/** Current test of this thread, or null outside a test (e.g. in @BeforeSuite). */
	public static ExtentTest current() {
		return CURRENT.get();
	}

	/** Forgets the current test of this thread (called at the end of BaseTest.teardown()). */
	public static void endTest() {
		CURRENT.remove();
	}

	/** Writes everything to test-output/ExtentReport.html. */
	public static synchronized void flush() {
		if (extent != null)
			extent.flush();
	}
}
