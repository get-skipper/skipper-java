package io.getskipper.testng;

import io.getskipper.core.SkipperConfig;
import io.getskipper.core.SkipperResolver;
import io.getskipper.core.TestEntry;
import io.getskipper.core.credentials.FileCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testng.SkipException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkipperListenerTest {

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
    void onTestStart_noResolver_doesNotThrow() {
        // No resolver set — should be a no-op
        var result = mock(org.testng.ITestResult.class);
        var method = mock(org.testng.ITestNGMethod.class);
        var testClass = mock(org.testng.IClass.class);

        when(result.getTestClass()).thenReturn(testClass);
        when(testClass.getRealClass()).thenAnswer(inv -> String.class);
        when(result.getMethod()).thenReturn(method);
        when(method.getMethodName()).thenReturn("someMethod");

        new SkipperListener().onTestStart(result);
    }

    @Test
    void onTestStart_withFutureDate_throwsSkipException() {
        Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
        TestEntry entry = new TestEntry(
                "io/getskipper/testng/SkipperListenerTest.java > SkipperListenerTest > someMethod",
                future);
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));
        SkipperState.setResolver(resolver);

        var result = mock(org.testng.ITestResult.class);
        var method = mock(org.testng.ITestNGMethod.class);
        var testClass = mock(org.testng.IClass.class);

        when(result.getTestClass()).thenReturn(testClass);
        when(testClass.getRealClass()).thenAnswer(inv -> SkipperListenerTest.class);
        when(result.getMethod()).thenReturn(method);
        when(method.getMethodName()).thenReturn("someMethod");

        assertThatThrownBy(() -> new SkipperListener().onTestStart(result))
                .isInstanceOf(SkipException.class)
                .hasMessageContaining("[skipper] Disabled until");
    }

    @Test
    void recordsDiscoveredIds() {
        TestEntry entry = new TestEntry(
                "io/getskipper/testng/SkipperListenerTest.java > SkipperListenerTest > myTest",
                null);
        SkipperResolver resolver = SkipperResolver.forTesting(DUMMY_CONFIG, List.of(entry));
        SkipperState.setResolver(resolver);

        var result = mock(org.testng.ITestResult.class);
        var method = mock(org.testng.ITestNGMethod.class);
        var testClass = mock(org.testng.IClass.class);

        when(result.getTestClass()).thenReturn(testClass);
        when(testClass.getRealClass()).thenAnswer(inv -> SkipperListenerTest.class);
        when(result.getMethod()).thenReturn(method);
        when(method.getMethodName()).thenReturn("myTest");

        new SkipperListener().onTestStart(result);

        assertThat(SkipperState.getDiscoveredIds())
                .anyMatch(id -> id.contains("SkipperListenerTest") && id.contains("myTest"));
    }
}
