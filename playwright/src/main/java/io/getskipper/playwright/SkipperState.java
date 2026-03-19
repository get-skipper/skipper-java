package io.getskipper.playwright;

import io.getskipper.core.SkipperConfig;
import io.getskipper.core.SkipperResolver;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe singleton holding Skipper session state for a Playwright test run.
 */
public final class SkipperState {

    private static final AtomicReference<SkipperConfig> CONFIG = new AtomicReference<>();
    private static final AtomicReference<SkipperResolver> RESOLVER = new AtomicReference<>();
    private static final CopyOnWriteArrayList<String> DISCOVERED = new CopyOnWriteArrayList<>();

    private SkipperState() {}

    public static void setConfig(SkipperConfig config) { CONFIG.set(config); }
    public static SkipperConfig getConfig() { return CONFIG.get(); }
    public static void setResolver(SkipperResolver resolver) { RESOLVER.set(resolver); }
    public static SkipperResolver getResolver() { return RESOLVER.get(); }
    public static void recordDiscoveredId(String id) { DISCOVERED.add(id); }
    public static List<String> getDiscoveredIds() { return List.copyOf(DISCOVERED); }

    public static void reset() {
        CONFIG.set(null);
        RESOLVER.set(null);
        DISCOVERED.clear();
    }
}
