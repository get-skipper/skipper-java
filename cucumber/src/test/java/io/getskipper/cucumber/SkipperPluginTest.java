package io.getskipper.cucumber;

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

class SkipperPluginTest {

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
    void noResolver_testEnabled() {
        assertThat(SkipperState.getResolver()).isNull();
    }

    @Test
    void resolver_unknownScenario_enabled() {
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of());
        SkipperState.setResolver(resolver);

        assertThat(resolver.isTestEnabled("features/auth.feature > User can log in")).isTrue();
    }

    @Test
    void resolver_futureDate_disabled() {
        Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
        TestEntry entry = new TestEntry("features/auth.feature > user can log in", future);
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));
        SkipperState.setResolver(resolver);

        // Normalized match (case-insensitive)
        assertThat(resolver.isTestEnabled("features/auth.feature > User Can Log In")).isFalse();
    }

    @Test
    void recordDiscoveredId() {
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of());
        SkipperState.setResolver(resolver);
        SkipperState.recordDiscoveredId("features/auth.feature > User can log in");
        assertThat(SkipperState.getDiscoveredIds()).contains("features/auth.feature > User can log in");
    }
}
