package io.getskipper.testng;

import io.getskipper.core.SkipperConfig;
import io.getskipper.core.SkipperLogger;
import io.getskipper.core.SkipperMode;
import io.getskipper.core.SkipperResolver;
import io.getskipper.core.SheetsWriter;
import io.getskipper.core.TestIdHelper;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.SkipException;

/**
 * TestNG listener that gates tests using Skipper's Google Sheets integration.
 *
 * <p>Register in {@code testng.xml}:
 * <pre>{@code
 * <listeners>
 *   <listener class-name="io.getskipper.testng.SkipperListener"/>
 * </listeners>
 * }</pre>
 *
 * <p>Or annotate test classes: {@code @Listeners(SkipperListener.class)}.
 *
 * <p>Reads configuration from environment variables (see {@link SkipperConfig#fromEnvironment()}).
 */
public class SkipperListener implements ISuiteListener, ITestListener {

    // ISuiteListener — global lifecycle

    @Override
    public void onStart(ISuite suite) {
        SkipperConfig config = SkipperState.getConfig();
        if (config == null) {
            config = SkipperConfig.fromEnvironment();
        }
        if (config == null) {
            SkipperLogger.log("SKIPPER_SPREADSHEET_ID not set — Skipper is inactive.");
            return;
        }

        SkipperState.setConfig(config);
        try {
            SkipperResolver resolver = new SkipperResolver(config);
            resolver.initialize();
            SkipperState.setResolver(resolver);
            SkipperLogger.log("Skipper initialized for TestNG suite.");
        } catch (Exception e) {
            System.err.println("[skipper] Failed to initialize: " + e.getMessage());
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        if (SkipperMode.fromEnvironment() != SkipperMode.SYNC) {
            return;
        }
        SkipperResolver resolver = SkipperState.getResolver();
        SkipperConfig config = SkipperState.getConfig();
        if (resolver == null || config == null) return;

        try {
            SheetsWriter writer = new SheetsWriter(config, resolver.getSheetsService());
            writer.sync(SkipperState.getDiscoveredIds());
            SkipperLogger.log("Skipper sync completed.");
        } catch (Exception e) {
            System.err.println("[skipper] Sync failed: " + e.getMessage());
        }
    }

    // ITestListener — per-test skip

    @Override
    public void onTestStart(ITestResult result) {
        SkipperResolver resolver = SkipperState.getResolver();
        if (resolver == null) return;

        String fqClassName = result.getTestClass().getRealClass().getName();
        String methodName = result.getMethod().getMethodName();
        String testId = TestIdHelper.buildFromClass(fqClassName, java.util.List.of(methodName));
        SkipperState.recordDiscoveredId(testId);

        if (!resolver.isTestEnabled(testId)) {
            java.time.Instant until = resolver.getDisabledUntil(testId);
            String reason = until != null
                    ? "[skipper] Disabled until " + until.toString().substring(0, 10)
                    : "[skipper] Disabled";
            throw new SkipException(reason);
        }
    }
}
