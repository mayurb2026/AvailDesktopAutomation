package listeners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

/**
 * Gives EVERY @Test the RetryAnalyzer, so a failed test is re-run retry.count times (config.properties).
 *
 * NEW in AVAIL Desktop. Registered in testng.xml:
 *   <listeners><listener class-name="listeners.RetryListener" /></listeners>
 *
 * A test that sets its own retryAnalyzer in @Test(...) keeps it (it is not overwritten).
 */
public class RetryListener implements IAnnotationTransformer {

	@Override
	@SuppressWarnings("rawtypes")
	public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
		if (annotation.getRetryAnalyzerClass() == null
				|| annotation.getRetryAnalyzerClass() == org.testng.internal.annotations.DisabledRetryAnalyzer.class) {
			annotation.setRetryAnalyzer(RetryAnalyzer.class);
		}
	}
}
