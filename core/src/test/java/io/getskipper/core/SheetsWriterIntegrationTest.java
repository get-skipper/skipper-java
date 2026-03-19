package io.getskipper.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SheetsWriter} that hit the real Google Sheets API.
 * Requires {@code SKIPPER_SPREADSHEET_ID}, {@code SKIPPER_MODE=sync}, and credentials.
 *
 * <p>The actual sync of all discovered test IDs is handled by {@code SkipperSessionListener}
 * at session close via the temp-file mechanism. This test only verifies the writer API.
 */
@EnabledIfEnvironmentVariable(named = "SKIPPER_SPREADSHEET_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SKIPPER_MODE", matches = "sync")
class SheetsWriterIntegrationTest {

    @Test
    void sync_doesNotThrow() throws Exception {
        SkipperConfig config = SkipperConfig.fromEnvironment();
        assertThat(config).isNotNull();

        SkipperResolver resolver = new SkipperResolver(config);
        resolver.initialize();

        SheetsWriter writer = new SheetsWriter(config, resolver.getSheetsService());
        // Syncing an empty list is a no-op (sheet is up to date check passes early).
        // The real sync of discovered IDs happens in SkipperSessionListener.launcherSessionClosed.
        writer.sync(List.of());
    }
}
