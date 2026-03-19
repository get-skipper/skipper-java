package io.getskipper.core;

import io.getskipper.core.credentials.Base64Credentials;
import io.getskipper.core.credentials.FileCredentials;
import io.getskipper.core.credentials.SkipperCredentials;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for a Skipper instance.
 *
 * <p>Build using {@link #builder(String, SkipperCredentials)}.
 *
 * <p>Credentials are resolved in this order by {@link #fromEnvironment()}:
 * <ol>
 *   <li>{@code GOOGLE_CREDS_B64} environment variable (Base64-encoded JSON)</li>
 *   <li>{@code SKIPPER_CREDENTIALS_FILE} environment variable (file path)</li>
 *   <li>Default file {@code ./service-account-skipper-bot.json}</li>
 * </ol>
 */
public record SkipperConfig(
        String spreadsheetId,
        SkipperCredentials credentials,
        String sheetName,
        List<String> referenceSheets,
        String testIdColumn,
        String disabledUntilColumn) {

    public SkipperConfig {
        Objects.requireNonNull(spreadsheetId, "spreadsheetId must not be null");
        Objects.requireNonNull(credentials, "credentials must not be null");
        referenceSheets = referenceSheets != null ? List.copyOf(referenceSheets) : List.of();
        testIdColumn = testIdColumn != null ? testIdColumn : "testId";
        disabledUntilColumn = disabledUntilColumn != null ? disabledUntilColumn : "disabledUntil";
    }

    /** Creates a config from environment variables. Returns {@code null} if {@code SKIPPER_SPREADSHEET_ID} is not set. */
    public static SkipperConfig fromEnvironment() {
        String spreadsheetId = System.getenv("SKIPPER_SPREADSHEET_ID");
        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            return null;
        }

        SkipperCredentials credentials;
        String credsB64 = System.getenv("GOOGLE_CREDS_B64");
        String credsFile = System.getenv("SKIPPER_CREDENTIALS_FILE");
        if (credsB64 != null && !credsB64.isBlank()) {
            credentials = new Base64Credentials(credsB64);
        } else if (credsFile != null && !credsFile.isBlank()) {
            credentials = new FileCredentials(credsFile);
        } else {
            credentials = new FileCredentials("./service-account-skipper-bot.json");
        }

        String sheetName = System.getenv("SKIPPER_SHEET_NAME");
        return new Builder(spreadsheetId, credentials).sheetName(sheetName).build();
    }

    public static Builder builder(String spreadsheetId, SkipperCredentials credentials) {
        return new Builder(spreadsheetId, credentials);
    }

    public static final class Builder {
        private final String spreadsheetId;
        private final SkipperCredentials credentials;
        private String sheetName;
        private List<String> referenceSheets = List.of();
        private String testIdColumn = "testId";
        private String disabledUntilColumn = "disabledUntil";

        private Builder(String spreadsheetId, SkipperCredentials credentials) {
            this.spreadsheetId = Objects.requireNonNull(spreadsheetId);
            this.credentials = Objects.requireNonNull(credentials);
        }

        public Builder sheetName(String sheetName) {
            this.sheetName = sheetName;
            return this;
        }

        public Builder referenceSheets(List<String> referenceSheets) {
            this.referenceSheets = referenceSheets;
            return this;
        }

        public Builder testIdColumn(String testIdColumn) {
            this.testIdColumn = testIdColumn;
            return this;
        }

        public Builder disabledUntilColumn(String disabledUntilColumn) {
            this.disabledUntilColumn = disabledUntilColumn;
            return this;
        }

        public SkipperConfig build() {
            return new SkipperConfig(spreadsheetId, credentials, sheetName,
                    referenceSheets, testIdColumn, disabledUntilColumn);
        }
    }
}
