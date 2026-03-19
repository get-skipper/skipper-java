package io.getskipper.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestIdHelperTest {

    @Test
    void buildFromClass_topLevel() {
        String id = TestIdHelper.buildFromClass("com.example.AuthTest", List.of("shouldLogin"));
        assertThat(id).isEqualTo("com/example/AuthTest.java > AuthTest > shouldLogin");
    }

    @Test
    void buildFromClass_defaultPackage() {
        String id = TestIdHelper.buildFromClass("AuthTest", List.of("shouldLogin"));
        assertThat(id).isEqualTo("AuthTest.java > AuthTest > shouldLogin");
    }

    @Test
    void buildFromClass_innerClass() {
        String id = TestIdHelper.buildFromClass(
                "com.example.AuthTest$LoginTests", List.of("shouldLogin"));
        // File path uses outer class; title uses inner class notation
        assertThat(id).isEqualTo("com/example/AuthTest.java > AuthTest > LoginTests > shouldLogin");
    }

    @Test
    void buildFromClass_multipleTitleParts() {
        String id = TestIdHelper.buildFromClass("com.example.CheckoutTest",
                List.of("with valid card", "should charge"));
        assertThat(id).isEqualTo("com/example/CheckoutTest.java > CheckoutTest > with valid card > should charge");
    }

    @Test
    void normalize_lowercasesTrimsAndCollapsesWhitespace() {
        assertThat(TestIdHelper.normalize("  com/Example/AuthTest.java  >  AUTH  "))
                .isEqualTo("com/example/authtest.java > auth");
    }

    @Test
    void normalize_collapseMultipleSpaces() {
        assertThat(TestIdHelper.normalize("a  >  b   >   c"))
                .isEqualTo("a > b > c");
    }

    @Test
    void stripParamSuffix_removesIndexSuffix() {
        assertThat(TestIdHelper.stripParamSuffix("shouldLogin[1]")).isEqualTo("shouldLogin");
    }

    @Test
    void stripParamSuffix_removesValueSuffix() {
        assertThat(TestIdHelper.stripParamSuffix("shouldLogin[admin, true]")).isEqualTo("shouldLogin");
    }

    @Test
    void stripParamSuffix_noSuffix() {
        assertThat(TestIdHelper.stripParamSuffix("shouldLogin")).isEqualTo("shouldLogin");
    }
}
