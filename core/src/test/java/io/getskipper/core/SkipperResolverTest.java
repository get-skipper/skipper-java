package io.getskipper.core;

import io.getskipper.core.credentials.FileCredentials;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkipperResolverTest {

    private static final SkipperConfig DUMMY_CONFIG = SkipperConfig.builder(
            "dummy-spreadsheet-id",
            new FileCredentials("./service-account-skipper-bot.json")).build();

    @Test
    void isTestEnabled_unknownTest_returnsTrue() {
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of());
        assertThat(resolver.isTestEnabled("com/example/AuthTest.java > AuthTest > someMethod")).isTrue();
    }

    @Test
    void isTestEnabled_testWithNoDate_returnsTrue() {
        TestEntry entry = new TestEntry("com/example/AuthTest.java > AuthTest > someMethod", null);
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));
        assertThat(resolver.isTestEnabled("com/example/AuthTest.java > AuthTest > someMethod")).isTrue();
    }

    @Test
    void isTestEnabled_testWithPastDate_returnsTrue() {
        Instant pastDate = Instant.now().minus(1, ChronoUnit.DAYS);
        TestEntry entry = new TestEntry("com/example/AuthTest.java > AuthTest > someMethod", pastDate);
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));
        assertThat(resolver.isTestEnabled("com/example/AuthTest.java > AuthTest > someMethod")).isTrue();
    }

    @Test
    void isTestEnabled_testWithFutureDate_returnsFalse() {
        Instant futureDate = Instant.now().plus(1, ChronoUnit.DAYS);
        TestEntry entry = new TestEntry("com/example/AuthTest.java > AuthTest > someMethod", futureDate);
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));
        assertThat(resolver.isTestEnabled("com/example/AuthTest.java > AuthTest > someMethod")).isFalse();
    }

    @Test
    void isTestEnabled_caseInsensitiveMatch() {
        Instant futureDate = Instant.now().plus(1, ChronoUnit.DAYS);
        TestEntry entry = new TestEntry("com/example/AuthTest.java > AuthTest > someMethod", futureDate);
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));
        // Lookup with different casing — normalization should match
        assertThat(resolver.isTestEnabled("COM/EXAMPLE/AuthTest.java > AuthTest > someMethod")).isFalse();
    }

    @Test
    void marshalAndRestoreCache() throws IOException {
        Instant futureDate = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        TestEntry entry = new TestEntry("com/example/AuthTest.java > AuthTest > login", futureDate);
        SkipperResolver original = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));

        byte[] marshaled = original.marshalCache();
        SkipperResolver restored = SkipperResolver.fromMarshaledCache(marshaled, DUMMY_CONFIG);

        assertThat(restored.isTestEnabled("com/example/AuthTest.java > AuthTest > login")).isFalse();
        assertThat(restored.getDisabledUntil("com/example/AuthTest.java > AuthTest > login"))
                .isEqualTo(futureDate);
    }

    @Test
    void marshalAndRestoreCache_nullDate() throws IOException {
        TestEntry entry = new TestEntry("com/example/AuthTest.java > AuthTest > login", null);
        SkipperResolver original = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));

        byte[] marshaled = original.marshalCache();
        SkipperResolver restored = SkipperResolver.fromMarshaledCache(marshaled, DUMMY_CONFIG);

        assertThat(restored.isTestEnabled("com/example/AuthTest.java > AuthTest > login")).isTrue();
    }
}
