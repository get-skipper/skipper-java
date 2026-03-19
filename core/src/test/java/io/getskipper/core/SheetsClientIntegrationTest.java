package io.getskipper.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that hit the real Google Sheets API.
 * Requires {@code SKIPPER_SPREADSHEET_ID} and either {@code GOOGLE_CREDS_B64} or
 * {@code service-account-skipper-bot.json} to be present.
 */
@EnabledIfEnvironmentVariable(named = "SKIPPER_SPREADSHEET_ID", matches = ".+")
class SheetsClientIntegrationTest {

    @Test
    void fetchAll_returnsEntriesFromSheet() throws Exception {
        SkipperConfig config = SkipperConfig.fromEnvironment();
        assertThat(config).isNotNull();

        SheetsClient client = new SheetsClient(config);
        FetchAllResult result = client.fetchAll();

        assertThat(result).isNotNull();
        assertThat(result.primary()).isNotNull();
        assertThat(result.primary().sheetName()).isNotBlank();
        assertThat(result.service()).isNotNull();
    }

    @Test
    void resolver_initialize_succeeds() throws Exception {
        SkipperConfig config = SkipperConfig.fromEnvironment();
        assertThat(config).isNotNull();

        SkipperResolver resolver = new SkipperResolver(config);
        resolver.initialize();

        // Tests not in the sheet are enabled by default
        assertThat(resolver.isTestEnabled("nonexistent/test.java > NonExistent > noSuchMethod"))
                .isTrue();
    }
}
