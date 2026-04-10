package io.getskipper.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.sheets.v4.Sheets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Determines whether a test should run based on the spreadsheet state.
 *
 * <p>Call {@link #initialize()} once (in a global setup hook) to fetch the spreadsheet
 * and populate the in-memory cache. Then call {@link #isTestEnabled(String)} for each test.
 *
 * <p>The cache can be serialized with {@link #marshalCache()} and restored with
 * {@link #fromMarshaledCache(byte[], SkipperConfig)} for sharing across JVM processes (e.g. Gradle workers).
 */
public final class SkipperResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkipperConfig config;

    /** Normalized test ID → disabledUntil (null = in sheet but no date set) */
    private Map<String, Instant> cache;

    /** Reused by SheetsWriter to avoid re-authenticating */
    private Sheets sheetsService;

    public SkipperResolver(SkipperConfig config) {
        this.config = config;
    }

    /**
     * Fetches the spreadsheet and populates the in-memory cache.
     * Must be called before {@link #isTestEnabled(String)}.
     *
     * <p>Behaviour is controlled by three environment variables:
     * <ul>
     *   <li>{@code SKIPPER_CACHE_FILE} (default: {@code .skipper-cache.json}) — path to the
     *       on-disk cache file that is written after every successful fetch.</li>
     *   <li>{@code SKIPPER_CACHE_TTL} (default: {@code 300}) — maximum age in seconds for the
     *       on-disk cache to be used as a fallback when the API is unreachable.</li>
     *   <li>{@code SKIPPER_FAIL_OPEN} (default: {@code true}) — if the API fails <em>and</em>
     *       no valid cache exists, run all tests instead of throwing. Set to {@code false} to
     *       restore the original crash behaviour.</li>
     * </ul>
     */
    public void initialize() throws IOException {
        String cacheFile = System.getenv().getOrDefault("SKIPPER_CACHE_FILE", ".skipper-cache.json");
        int cacheTtl = Integer.parseInt(
                System.getenv().getOrDefault("SKIPPER_CACHE_TTL", "300"));
        boolean failOpen = !"false".equalsIgnoreCase(
                System.getenv().getOrDefault("SKIPPER_FAIL_OPEN", "true"));

        try {
            SheetsClient client = new SheetsClient(config);
            FetchAllResult result = client.fetchAll();
            this.sheetsService = result.service();

            cache = new HashMap<>();
            for (TestEntry entry : result.entries()) {
                cache.put(TestIdHelper.normalize(entry.testId()), entry.disabledUntil());
            }
            SkipperLogger.logf("Resolver initialized with %d cached entries.", cache.size());
            writeCacheFile(cacheFile, cache);
        } catch (Exception err) {
            Map<String, Instant> fromCache = readCacheFile(cacheFile, cacheTtl);
            if (fromCache != null) {
                cache = fromCache;
                SkipperLogger.logf("[skipper] API failed, using on-disk cache (%s).", cacheFile);
                return;
            }
            if (failOpen) {
                cache = Collections.emptyMap();
                SkipperLogger.log("[skipper] API failed, no valid cache — running all tests (fail-open).");
                return;
            }
            if (err instanceof IOException ioEx) throw ioEx;
            throw new IOException("[skipper] Initialization failed", err);
        }
    }

    /**
     * Writes the current in-memory cache to disk as JSON so it can be used as a fallback
     * on subsequent runs if the Sheets API is unavailable.
     */
    void writeCacheFile(String path, Map<String, Instant> entries) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("savedAt", Instant.now().toString());
            Map<String, String> serialized = new HashMap<>();
            for (Map.Entry<String, Instant> e : entries.entrySet()) {
                serialized.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : null);
            }
            payload.put("entries", serialized);
            Files.writeString(Path.of(path), MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            SkipperLogger.warn("[skipper] Could not write cache file \"" + path + "\": " + e.getMessage());
        }
    }

    /**
     * Reads the on-disk cache file and returns the entries if the file exists and is within
     * the TTL window. Returns {@code null} if the file is missing, unreadable, or expired.
     */
    static Map<String, Instant> readCacheFile(String path, int ttlSeconds) {
        try {
            String json = Files.readString(Path.of(path));
            Map<String, Object> payload = MAPPER.readValue(json, new TypeReference<>() {});
            String savedAtStr = (String) payload.get("savedAt");
            if (savedAtStr == null) return null;
            Instant savedAt = Instant.parse(savedAtStr);
            long ageSeconds = Instant.now().getEpochSecond() - savedAt.getEpochSecond();
            if (ageSeconds > ttlSeconds) {
                SkipperLogger.logf("[skipper] Cache file \"%s\" is %ds old (TTL=%ds) — ignoring.",
                        path, ageSeconds, ttlSeconds);
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, String> raw = (Map<String, String>) payload.get("entries");
            if (raw == null) return null;
            Map<String, Instant> result = new HashMap<>();
            for (Map.Entry<String, String> e : raw.entrySet()) {
                result.put(e.getKey(), e.getValue() != null ? Instant.parse(e.getValue()) : null);
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns {@code true} if the test should run, {@code false} if it should be skipped.
     *
     * <ul>
     *   <li>Not in spreadsheet → {@code true} (opt-out model)</li>
     *   <li>In spreadsheet with no date → {@code true}</li>
     *   <li>In spreadsheet with past date → {@code true}</li>
     *   <li>In spreadsheet with future date → {@code false}</li>
     * </ul>
     */
    public boolean isTestEnabled(String testId) {
        if (cache == null) return true;
        String key = TestIdHelper.normalize(testId);
        if (!cache.containsKey(key)) return true;
        Instant until = cache.get(key);
        if (until == null) return true;
        return !until.isAfter(Instant.now());
    }

    /**
     * Returns the {@code disabledUntil} instant for the given test ID, or {@code null} if
     * the test is not in the spreadsheet or has no date set.
     */
    public Instant getDisabledUntil(String testId) {
        if (cache == null) return null;
        return cache.get(TestIdHelper.normalize(testId));
    }

    /** Returns the authenticated Sheets service, for reuse by {@link SheetsWriter}. */
    public Sheets getSheetsService() {
        return sheetsService;
    }

    /** Returns the config this resolver was created with. */
    public SkipperConfig getConfig() {
        return config;
    }

    /**
     * Serializes the cache to JSON bytes for cross-process sharing.
     * Dates are stored as ISO-8601 strings; absent dates as JSON {@code null}.
     */
    public byte[] marshalCache() throws IOException {
        Map<String, String> serializable = new HashMap<>();
        for (Map.Entry<String, Instant> e : cache.entrySet()) {
            serializable.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : null);
        }
        return MAPPER.writeValueAsBytes(serializable);
    }

    /**
     * Restores a resolver from a previously marshaled cache (no network call needed).
     *
     * @param data JSON bytes produced by {@link #marshalCache()}
     * @param config the original config (needed if the restored resolver is used with SheetsWriter)
     */
    public static SkipperResolver fromMarshaledCache(byte[] data, SkipperConfig config)
            throws IOException {
        Map<String, String> raw = MAPPER.readValue(data, new TypeReference<>() {});
        SkipperResolver resolver = new SkipperResolver(config);
        resolver.cache = new HashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            resolver.cache.put(e.getKey(), e.getValue() != null ? Instant.parse(e.getValue()) : null);
        }
        return resolver;
    }

    /**
     * Creates a resolver pre-populated from a list of entries (for testing only).
     */
    public static SkipperResolver forTesting(SkipperConfig config, List<TestEntry> entries) {
        SkipperResolver resolver = new SkipperResolver(config);
        resolver.cache = new HashMap<>();
        for (TestEntry entry : entries) {
            resolver.cache.put(TestIdHelper.normalize(entry.testId()), entry.disabledUntil());
        }
        return resolver;
    }

    /** Serializes an empty cache (for testing). */
    byte[] marshalEmptyCache() throws IOException {
        if (cache == null) cache = new HashMap<>();
        return marshalCache();
    }

    /** Populates the cache directly from a list of entries (for testing). */
    void injectCacheForTest(List<TestEntry> entries) {
        cache = new HashMap<>();
        for (TestEntry entry : entries) {
            cache.put(TestIdHelper.normalize(entry.testId()), entry.disabledUntil());
        }
    }
}
