package io.getskipper.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import io.getskipper.core.SkipperConfig;
import io.getskipper.core.SkipperLogger;
import io.getskipper.core.SkipperMode;
import io.getskipper.core.SkipperResolver;
import io.getskipper.core.SheetsWriter;
import io.getskipper.core.TestIdHelper;
import java.time.Instant;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

/**
 * JUnit 5 extension that combines Playwright browser lifecycle management with
 * Skipper test gating.
 *
 * <p>Register with {@code @ExtendWith(SkipperPlaywrightExtension.class)} on a test class,
 * or extend {@link SkipperPlaywrightTest} for a ready-to-use base class with {@code page}
 * and {@code context} fields.
 *
 * <p>Reads Skipper configuration from environment variables (see
 * {@link SkipperConfig#fromEnvironment()}). Playwright is always started;
 * Skipper gating is optional (no-op if {@code SKIPPER_SPREADSHEET_ID} is not set).
 *
 * <p>Playwright browser instances ({@link Playwright}, {@link Browser}, {@link BrowserContext})
 * are stored in the JUnit 5 {@link ExtensionContext.Store} and are accessible via
 * {@link #getPlaywright(ExtensionContext)}, {@link #getBrowser(ExtensionContext)}, and
 * {@link #getBrowserContext(ExtensionContext)}.
 */
public class SkipperPlaywrightExtension
        implements BeforeAllCallback, AfterAllCallback, ExecutionCondition {

    private static final Namespace NS = Namespace.create(SkipperPlaywrightExtension.class);
    private static final String KEY_PLAYWRIGHT = "playwright";
    private static final String KEY_BROWSER = "browser";
    private static final String KEY_CONTEXT = "context";

    private static final ConditionEvaluationResult ENABLED =
            ConditionEvaluationResult.enabled("Skipper not configured or test not in spreadsheet");

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Store store = context.getStore(NS);

        // Initialize Playwright browser
        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch();
        BrowserContext browserContext = browser.newContext();

        store.put(KEY_PLAYWRIGHT, playwright);
        store.put(KEY_BROWSER, browser);
        store.put(KEY_CONTEXT, browserContext);

        // Initialize Skipper
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
            SkipperLogger.log("Skipper initialized for Playwright tests.");
        } catch (Exception e) {
            System.err.println("[skipper] Failed to initialize: " + e.getMessage());
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        Store store = context.getStore(NS);

        // Sync if in sync mode
        if (SkipperMode.fromEnvironment() == SkipperMode.SYNC) {
            SkipperResolver resolver = SkipperState.getResolver();
            SkipperConfig config = SkipperState.getConfig();
            if (resolver != null && config != null) {
                try {
                    SheetsWriter writer = new SheetsWriter(config, resolver.getSheetsService());
                    writer.sync(SkipperState.getDiscoveredIds());
                    SkipperLogger.log("Skipper sync completed.");
                } catch (Exception e) {
                    System.err.println("[skipper] Sync failed: " + e.getMessage());
                }
            }
        }

        // Close Playwright resources
        BrowserContext browserContext = store.get(KEY_CONTEXT, BrowserContext.class);
        if (browserContext != null) browserContext.close();

        Browser browser = store.get(KEY_BROWSER, Browser.class);
        if (browser != null) browser.close();

        Playwright playwright = store.get(KEY_PLAYWRIGHT, Playwright.class);
        if (playwright != null) playwright.close();
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (context.getTestMethod().isEmpty()) {
            return ENABLED;
        }

        SkipperResolver resolver = SkipperState.getResolver();
        if (resolver == null) {
            return ENABLED;
        }

        String fqClassName = context.getRequiredTestClass().getName();
        String methodName = TestIdHelper.stripParamSuffix(
                context.getTestMethod().map(m -> m.getName()).orElse("unknown"));
        String testId = TestIdHelper.buildFromClass(fqClassName, java.util.List.of(methodName));
        SkipperState.recordDiscoveredId(testId);

        if (!resolver.isTestEnabled(testId)) {
            Instant until = resolver.getDisabledUntil(testId);
            String reason = until != null
                    ? "[skipper] Disabled until " + until.toString().substring(0, 10)
                    : "[skipper] Disabled";
            return ConditionEvaluationResult.disabled(reason);
        }

        return ENABLED;
    }

    /** Returns the {@link Playwright} instance stored in the given extension context. */
    public static Playwright getPlaywright(ExtensionContext context) {
        return context.getStore(NS).get(KEY_PLAYWRIGHT, Playwright.class);
    }

    /** Returns the {@link Browser} instance stored in the given extension context. */
    public static Browser getBrowser(ExtensionContext context) {
        return context.getStore(NS).get(KEY_BROWSER, Browser.class);
    }

    /** Returns the {@link BrowserContext} instance stored in the given extension context. */
    public static BrowserContext getBrowserContext(ExtensionContext context) {
        return context.getStore(NS).get(KEY_CONTEXT, BrowserContext.class);
    }
}
