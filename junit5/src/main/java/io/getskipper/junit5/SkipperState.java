package io.getskipper.junit5;

import io.getskipper.core.SkipperConfig;
import io.getskipper.core.SkipperResolver;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe singleton holding the Skipper session state shared across a JUnit 5 test run.
 *
 * <p>Populated by {@link SkipperSessionListener} during global setup and consumed by
 * {@link SkipperExtension} during per-test evaluation.
 */
public final class SkipperState {

    private static final AtomicReference<SkipperConfig> CONFIG = new AtomicReference<>();
    private static final AtomicReference<SkipperResolver> RESOLVER = new AtomicReference<>();
    private static final CopyOnWriteArrayList<String> DISCOVERED = new CopyOnWriteArrayList<>();

    private SkipperState() {}

    public static void setConfig(SkipperConfig config) {
        CONFIG.set(config);
    }

    public static SkipperConfig getConfig() {
        return CONFIG.get();
    }

    public static void setResolver(SkipperResolver resolver) {
        RESOLVER.set(resolver);
    }

    public static SkipperResolver getResolver() {
        return RESOLVER.get();
    }

    public static void recordDiscoveredId(String testId) {
        DISCOVERED.add(testId);
    }

    public static List<String> getDiscoveredIds() {
        return List.copyOf(DISCOVERED);
    }

    /** Resets all state (used between test runs and in unit tests). */
    public static void reset() {
        CONFIG.set(null);
        RESOLVER.set(null);
        DISCOVERED.clear();
    }
}
