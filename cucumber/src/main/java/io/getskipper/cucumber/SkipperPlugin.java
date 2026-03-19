package io.getskipper.cucumber;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.getskipper.core.SkipperConfig;
import io.getskipper.core.SkipperLogger;
import io.getskipper.core.SkipperMode;
import io.getskipper.core.SkipperResolver;
import io.getskipper.core.SheetsWriter;
import io.getskipper.core.TestIdHelper;
import java.net.URI;

/**
 * Cucumber JVM plugin that gates scenarios using Skipper's Google Sheets integration.
 *
 * <p>Register in {@code @CucumberOptions}:
 * <pre>{@code
 * @CucumberOptions(plugin = {"io.getskipper.cucumber.SkipperPlugin"})
 * }</pre>
 *
 * <p>Or in {@code junit-platform.properties}:
 * <pre>{@code
 * cucumber.plugin=io.getskipper.cucumber.SkipperPlugin
 * }</pre>
 *
 * <p>Test ID format: {@code "features/auth/login.feature > User can log in"}.
 */
public class SkipperPlugin implements ConcurrentEventListener {

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, this::onTestRunStarted);
        publisher.registerHandlerFor(TestCaseStarted.class, this::onTestCaseStarted);
        publisher.registerHandlerFor(TestRunFinished.class, this::onTestRunFinished);
    }

    private void onTestRunStarted(TestRunStarted event) {
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
            SkipperLogger.log("Skipper initialized for Cucumber run.");
        } catch (Exception e) {
            System.err.println("[skipper] Failed to initialize: " + e.getMessage());
        }
    }

    private void onTestCaseStarted(TestCaseStarted event) {
        SkipperResolver resolver = SkipperState.getResolver();
        if (resolver == null) return;

        String featurePath = normalizeUri(event.getTestCase().getUri());
        String scenarioName = event.getTestCase().getName();
        String testId = featurePath + " > " + scenarioName;

        SkipperState.recordDiscoveredId(testId);

        if (!resolver.isTestEnabled(testId)) {
            java.time.Instant until = resolver.getDisabledUntil(testId);
            String reason = until != null
                    ? "[skipper] Disabled until " + until.toString().substring(0, 10)
                    : "[skipper] Disabled";
            throw new io.cucumber.java.PendingException(reason);
        }
    }

    private void onTestRunFinished(TestRunFinished event) {
        if (SkipperMode.fromEnvironment() != SkipperMode.SYNC) return;

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

    private static String normalizeUri(URI uri) {
        String path = uri.toString();
        // Strip "classpath:" prefix if present; normalize to relative path
        if (path.startsWith("classpath:")) {
            path = path.substring("classpath:".length());
        }
        // Normalize to forward slashes and strip leading slash
        path = path.replace('\\', '/').replaceFirst("^/", "");
        return TestIdHelper.normalize(path).replace("\\", "/");
    }
}
