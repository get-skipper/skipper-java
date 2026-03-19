package io.getskipper.playwright;

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

class SkipperPlaywrightExtensionTest {

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
    void noResolver_enabled() {
        assertThat(SkipperState.getResolver()).isNull();
    }

    @Test
    void resolver_futureDate_testDisabled() {
        Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
        TestEntry entry = new TestEntry(
                "io/getskipper/playwright/SkipperPlaywrightExtensionTest.java > SkipperPlaywrightExtensionTest > someTest",
                future);
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));
        SkipperState.setResolver(resolver);

        assertThat(resolver.isTestEnabled(
                "io/getskipper/playwright/SkipperPlaywrightExtensionTest.java > SkipperPlaywrightExtensionTest > someTest"))
                .isFalse();
    }

    @Test
    void resolver_pastDate_testEnabled() {
        Instant past = Instant.now().minus(7, ChronoUnit.DAYS);
        TestEntry entry = new TestEntry(
                "io/getskipper/playwright/SkipperPlaywrightExtensionTest.java > SkipperPlaywrightExtensionTest > someTest",
                past);
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));
        SkipperState.setResolver(resolver);

        assertThat(resolver.isTestEnabled(
                "io/getskipper/playwright/SkipperPlaywrightExtensionTest.java > SkipperPlaywrightExtensionTest > someTest"))
                .isTrue();
    }
}
