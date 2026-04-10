package io.getskipper.core;

import io.getskipper.core.credentials.FileCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkipperResolverCacheTest {

    private static final SkipperConfig DUMMY_CONFIG = SkipperConfig.builder(
            "dummy-spreadsheet-id",
            new FileCredentials("./service-account-skipper-bot.json")).build();

    @TempDir
    Path tempDir;

    @Test
    void writeThenRead_returnsEntries() throws Exception {
        SkipperResolver resolver = new SkipperResolver(DUMMY_CONFIG);
        String cacheFile = tempDir.resolve("cache.json").toString();

        Instant futureDate = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Map<String, Instant> entries = new HashMap<>();
        entries.put("com/example/authtest.java > authtest > login", futureDate);
        entries.put("com/example/authtest.java > authtest > logout", null);

        resolver.writeCacheFile(cacheFile, entries);

        Map<String, Instant> read = SkipperResolver.readCacheFile(cacheFile, 300);

        assertThat(read).isNotNull();
        assertThat(read).containsKey("com/example/authtest.java > authtest > login");
        assertThat(read.get("com/example/authtest.java > authtest > login")).isEqualTo(futureDate);
        assertThat(read).containsKey("com/example/authtest.java > authtest > logout");
        assertThat(read.get("com/example/authtest.java > authtest > logout")).isNull();
    }

    @Test
    void readCacheFile_missingFile_returnsNull() {
        String cacheFile = tempDir.resolve("nonexistent.json").toString();
        assertThat(SkipperResolver.readCacheFile(cacheFile, 300)).isNull();
    }

    @Test
    void readCacheFile_expiredCache_returnsNull() throws Exception {
        SkipperResolver resolver = new SkipperResolver(DUMMY_CONFIG);
        String cacheFile = tempDir.resolve("cache.json").toString();

        // Write a cache with a very old timestamp by writing raw JSON
        String oldJson = "{\"savedAt\":\"2000-01-01T00:00:00Z\",\"entries\":{}}";
        java.nio.file.Files.writeString(Path.of(cacheFile), oldJson);

        // TTL of 300 seconds — the 25-year-old cache should be rejected
        assertThat(SkipperResolver.readCacheFile(cacheFile, 300)).isNull();
    }

    @Test
    void readCacheFile_withinTtl_returnsEntries() throws Exception {
        SkipperResolver resolver = new SkipperResolver(DUMMY_CONFIG);
        String cacheFile = tempDir.resolve("cache.json").toString();

        Map<String, Instant> entries = new HashMap<>();
        entries.put("com/example/test.java > test > method", null);
        resolver.writeCacheFile(cacheFile, entries);

        // With a generous TTL the cache should be returned
        Map<String, Instant> result = SkipperResolver.readCacheFile(cacheFile, 3600);
        assertThat(result).isNotNull();
        assertThat(result).containsKey("com/example/test.java > test > method");
    }

    @Test
    void isTestEnabled_afterFailOpen_allTestsRun() {
        // Simulate the fail-open state: cache is empty (all tests should run)
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, java.util.List.of());
        assertThat(resolver.isTestEnabled("any/test/AnyTest.java > AnyTest > anyMethod")).isTrue();
    }
}
