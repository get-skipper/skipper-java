package io.getskipper.junit5;

import io.getskipper.core.SkipperConfig;
import io.getskipper.core.SkipperLogger;
import io.getskipper.core.SkipperResolver;
import io.getskipper.core.TestIdHelper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 {@link ExecutionCondition} that skips tests disabled in the Skipper spreadsheet.
 *
 * <p>Auto-registered via ServiceLoader when
 * {@code junit.extensions.autodetection.enabled=true} is set in
 * {@code src/test/resources/junit-platform.properties}.
 *
 * <p>Alternatively, annotate test classes or methods with
 * {@code @ExtendWith(SkipperExtension.class)}.
 */
public class SkipperExtension implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED =
            ConditionEvaluationResult.enabled("Skipper not configured or test not in spreadsheet");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        // Only evaluate for test methods, not containers (classes, etc.)
        if (context.getTestMethod().isEmpty()) {
            return ENABLED;
        }

        SkipperResolver resolver = SkipperState.getResolver();
        if (resolver == null) {
            // Gradle test workers may load LauncherSessionListener and ExecutionCondition in
            // different classloaders, so SkipperState.RESOLVER set by the listener may not be
            // visible here. Recover by restoring the resolver from the serialized cache file.
            resolver = tryRestoreResolver();
            if (resolver != null) {
                SkipperState.setResolver(resolver);
            }
        }

        String testId = buildTestId(context);

        // Always record the discovered ID — even when the test is enabled.
        // Write to both SkipperState (same-classloader case) and the temp file
        // (cross-classloader case, read by SkipperSessionListener at session close).
        SkipperState.recordDiscoveredId(testId);
        appendToDiscoveredFile(testId);

        if (resolver == null) {
            return ENABLED;
        }

        if (!resolver.isTestEnabled(testId)) {
            Instant until = resolver.getDisabledUntil(testId);
            String reason = until != null
                    ? "[skipper] Disabled until " + until.toString().substring(0, 10)
                    : "[skipper] Disabled";
            return ConditionEvaluationResult.disabled(reason);
        }

        return ENABLED;
    }

    private static SkipperResolver tryRestoreResolver() {
        String cacheFilePath = System.getProperty(SkipperSessionListener.PROP_CACHE_FILE);
        if (cacheFilePath == null) {
            return null;
        }
        try {
            byte[] cacheData = Files.readAllBytes(Path.of(cacheFilePath));
            SkipperConfig config = SkipperConfig.fromEnvironment();
            if (config == null) return null;
            SkipperState.setConfig(config);
            SkipperLogger.log("SkipperExtension: restored resolver from cache file.");
            return SkipperResolver.fromMarshaledCache(cacheData, config);
        } catch (Exception e) {
            SkipperLogger.log("SkipperExtension: could not restore resolver — " + e.getMessage());
            return null;
        }
    }

    private static void appendToDiscoveredFile(String testId) {
        String filePath = System.getProperty(SkipperSessionListener.PROP_DISCOVERED_FILE);
        if (filePath == null) return;
        try {
            Files.write(
                    Path.of(filePath),
                    (testId + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            System.err.println("[skipper-ext] FAILED to write discovered file: " + e.getMessage());
        }
    }

    private static String buildTestId(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        String fqClassName = testClass.getName();

        // Build title parts: for nested tests, walk up the parent contexts to collect names
        List<String> parts = new ArrayList<>();
        collectNestedNames(context, parts);

        // Method name (strip param suffix for parameterized tests)
        String methodName = TestIdHelper.stripParamSuffix(
                context.getTestMethod().map(m -> m.getName()).orElse("unknown"));
        parts.add(methodName);

        return TestIdHelper.buildFromClass(fqClassName, parts);
    }

    /**
     * For nested test classes, collect the intermediate display names between the root class
     * and the current method context.
     */
    private static void collectNestedNames(ExtensionContext context, List<String> parts) {
        ExtensionContext parent = context.getParent().orElse(null);
        if (parent == null || parent.getTestClass().isEmpty()) {
            return;
        }
        // If the parent has a test class that differs from the current one, it's a nested class
        if (!parent.getTestClass().equals(context.getTestClass())) {
            collectNestedNames(parent, parts);
            parent.getTestClass().ifPresent(cls ->
                    parts.add(cls.getSimpleName()));
        }
    }
}
