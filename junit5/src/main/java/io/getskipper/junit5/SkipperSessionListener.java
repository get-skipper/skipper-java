package io.getskipper.junit5;

import io.getskipper.core.SkipperConfig;
import io.getskipper.core.SkipperLogger;
import io.getskipper.core.SkipperMode;
import io.getskipper.core.SkipperResolver;
import io.getskipper.core.SheetsWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * JUnit Platform {@link LauncherSessionListener} that manages the Skipper lifecycle
 * for an entire test session.
 *
 * <ul>
 *   <li>{@link #launcherSessionOpened}: fetches the spreadsheet once and stores the resolver
 *       in {@link SkipperState}. Also serializes the resolver cache to a temp file and
 *       creates a temp file for collecting discovered test IDs across class loaders.</li>
 *   <li>{@link #launcherSessionClosed}: if {@code SKIPPER_MODE=sync}, reconciles the
 *       spreadsheet with all discovered test IDs collected via the temp file.</li>
 * </ul>
 *
 * <p>Registered automatically via ServiceLoader. No explicit configuration required — reads
 * from environment variables:
 * <ul>
 *   <li>{@code SKIPPER_SPREADSHEET_ID} — required to activate Skipper</li>
 *   <li>{@code GOOGLE_CREDS_B64} — Base64-encoded service account JSON (CI)</li>
 *   <li>{@code SKIPPER_CREDENTIALS_FILE} — path to service account JSON (alternative)</li>
 *   <li>{@code SKIPPER_SHEET_NAME} — target sheet name (optional, defaults to first sheet)</li>
 *   <li>{@code SKIPPER_MODE} — set to {@code sync} to enable reconciliation</li>
 * </ul>
 */
public class SkipperSessionListener implements LauncherSessionListener {

    /** System property key for the serialized resolver cache file path. */
    static final String PROP_CACHE_FILE = "skipper.cache.file";

    /** System property key for the discovered test IDs file path. */
    static final String PROP_DISCOVERED_FILE = "skipper.discovered.file";

    // Store resolver and config as instance fields so that SkipperState.reset() calls
    // in test @AfterEach methods (e.g. in SkipperExtensionTest) cannot clear them.
    private SkipperResolver sessionResolver;
    private SkipperConfig sessionConfig;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        // Allow programmatic override via SkipperState.setConfig(config)
        SkipperConfig config = SkipperState.getConfig();
        if (config == null) {
            config = SkipperConfig.fromEnvironment();
        }

        if (config == null) {
            SkipperLogger.log("SKIPPER_SPREADSHEET_ID not set — Skipper is inactive.");
            return;
        }

        SkipperState.setConfig(config);
        sessionConfig = config;
        try {
            SkipperResolver resolver = new SkipperResolver(config);
            resolver.initialize();
            SkipperState.setResolver(resolver);
            sessionResolver = resolver;
            SkipperLogger.log("Skipper initialized successfully.");

            // Cross-classloader: serialize cache to a temp file so SkipperExtension can
            // restore it even when loaded by a different classloader (Gradle test worker).
            Path cacheFile = Files.createTempFile("skipper-cache-", ".json");
            Files.write(cacheFile, resolver.marshalCache());
            System.setProperty(PROP_CACHE_FILE, cacheFile.toString());
            SkipperLogger.logf("Cache written to %s", cacheFile);

        } catch (Exception e) {
            System.err.println("[skipper] Failed to initialize: " + e.getMessage());
        }

        // Create the discovered IDs temp file for cross-classloader collection.
        try {
            Path discoveredFile = Files.createTempFile("skipper-discovered-", ".txt");
            System.setProperty(PROP_DISCOVERED_FILE, discoveredFile.toString());
            SkipperLogger.logf("Discovered file: %s", discoveredFile);
        } catch (IOException e) {
            System.err.println("[skipper] Could not create discovered-IDs temp file: " + e.getMessage());
        }
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        if (SkipperMode.fromEnvironment() != SkipperMode.SYNC) {
            cleanUpTempFiles();
            return;
        }

        // Use instance fields — not SkipperState — so that tests calling SkipperState.reset()
        // in @AfterEach cannot accidentally clear the session-level resolver.
        SkipperResolver resolver = sessionResolver;
        SkipperConfig config = sessionConfig;
        if (resolver == null || config == null) {
            cleanUpTempFiles();
            return;
        }

        // Read discovered IDs from temp file (cross-classloader) or fall back to SkipperState.
        List<String> discoveredIds = readDiscoveredIds();
        SkipperLogger.logf("Syncing %d discovered test ID(s).", discoveredIds.size());

        cleanUpTempFiles();

        try {
            SheetsWriter writer = new SheetsWriter(config, resolver.getSheetsService());
            writer.sync(discoveredIds);
            SkipperLogger.log("Skipper sync completed.");
        } catch (Exception e) {
            System.err.println("[skipper] Sync failed: " + e.getMessage());
        }
    }

    private List<String> readDiscoveredIds() {
        String filePath = System.getProperty(PROP_DISCOVERED_FILE);
        if (filePath != null) {
            try {
                Path file = Path.of(filePath);
                if (Files.exists(file)) {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    return lines.stream().filter(l -> !l.isBlank()).toList();
                }
            } catch (IOException e) {
                System.err.println("[skipper] Could not read discovered-IDs file: " + e.getMessage());
            }
        }
        // Fallback: same-classloader case
        return SkipperState.getDiscoveredIds();
    }

    private void cleanUpTempFiles() {
        deleteIfSet(PROP_CACHE_FILE);
        deleteIfSet(PROP_DISCOVERED_FILE);
    }

    private static void deleteIfSet(String prop) {
        String path = System.getProperty(prop);
        if (path != null) {
            try { Files.deleteIfExists(Path.of(path)); } catch (IOException ignored) {}
            System.clearProperty(prop);
        }
    }
}
