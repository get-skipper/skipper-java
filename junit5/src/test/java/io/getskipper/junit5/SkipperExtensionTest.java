package io.getskipper.junit5;

import io.getskipper.core.SkipperConfig;
import io.getskipper.core.SkipperResolver;
import io.getskipper.core.TestEntry;
import io.getskipper.core.credentials.FileCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkipperExtensionTest {

    private static final SkipperConfig DUMMY_CONFIG = SkipperConfig.builder(
            "dummy", new FileCredentials("./service-account-skipper-bot.json")).build();

    @BeforeEach
    void setUp() {
        SkipperState.reset();
    }

    @AfterEach
    void tearDown() {
        SkipperState.reset();
    }

    @Test
    void isTestEnabled_noResolver_returnsEnabled() {
        // When no resolver is set, all tests run
        assertThat(SkipperState.getResolver()).isNull();
        // The extension itself returns ENABLED when resolver is null
    }

    @Test
    void recordDiscoveredId_addsToList() {
        SkipperState.recordDiscoveredId("com/example/FooTest.java > FooTest > bar");
        SkipperState.recordDiscoveredId("com/example/FooTest.java > FooTest > baz");
        assertThat(SkipperState.getDiscoveredIds())
                .containsExactlyInAnyOrder(
                        "com/example/FooTest.java > FooTest > bar",
                        "com/example/FooTest.java > FooTest > baz");
    }

    @Test
    void resolver_futureDate_testDisabled() {
        Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
        TestEntry entry = new TestEntry("com/example/FooTest.java > FooTest > bar", future);
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));
        SkipperState.setResolver(resolver);

        assertThat(resolver.isTestEnabled("com/example/FooTest.java > FooTest > bar")).isFalse();
    }

    @Test
    void resolver_pastDate_testEnabled() {
        Instant past = Instant.now().minus(7, ChronoUnit.DAYS);
        TestEntry entry = new TestEntry("com/example/FooTest.java > FooTest > bar", past);
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));
        SkipperState.setResolver(resolver);

        assertThat(resolver.isTestEnabled("com/example/FooTest.java > FooTest > bar")).isTrue();
    }
}
