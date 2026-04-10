package io.getskipper.core;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads test entries from a Google Sheets spreadsheet using a service account.
 */
public final class SheetsClient {

    private static final String APPLICATION_NAME = "skipper-java";
    private static final Pattern DATE_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final SkipperConfig config;

    public SheetsClient(SkipperConfig config) {
        this.config = config;
    }

    /**
     * Fetches the primary sheet and all reference sheets, then merges the results.
     * The most restrictive (latest) {@code disabledUntil} date wins when the same test ID
     * appears in multiple sheets.
     */
    public FetchAllResult fetchAll() throws IOException {
        Sheets service = buildService();
        String spreadsheetId = config.spreadsheetId();

        SkipperLogger.logf("Fetching spreadsheet metadata for %s", spreadsheetId);
        Spreadsheet spreadsheet = service.spreadsheets().get(spreadsheetId).execute();

        Map<String, Integer> sheetIdByName = new HashMap<>();
        for (var sheet : spreadsheet.getSheets()) {
            var props = sheet.getProperties();
            if (props != null && props.getTitle() != null) {
                sheetIdByName.put(props.getTitle(), props.getSheetId() != null
                        ? props.getSheetId().intValue() : 0);
            }
        }

        String primaryName = config.sheetName();
        if (primaryName == null || primaryName.isBlank()) {
            primaryName = spreadsheet.getSheets().isEmpty() ? "Sheet1"
                    : spreadsheet.getSheets().get(0).getProperties().getTitle();
        }

        if (!sheetIdByName.containsKey(primaryName)) {
            SkipperLogger.logf("Sheet \"%s\" not found — creating it.", primaryName);
            int newSheetId = createSheet(service, spreadsheetId, primaryName);
            sheetIdByName.put(primaryName, newSheetId);
        }

        SkipperLogger.logf("Fetching primary sheet \"%s\"", primaryName);
        SheetFetchResult primary = fetchSheet(service, primaryName, sheetIdByName.get(primaryName));

        List<TestEntry> merged = new ArrayList<>(primary.entries());
        Map<String, Integer> mergedIdx = new HashMap<>();
        for (int i = 0; i < merged.size(); i++) {
            mergedIdx.put(TestIdHelper.normalize(merged.get(i).testId()), i);
        }

        for (String refName : config.referenceSheets()) {
            if (!sheetIdByName.containsKey(refName)) {
                SkipperLogger.warn("Reference sheet \"" + refName + "\" not found — skipping.");
                continue;
            }
            SkipperLogger.logf("Fetching reference sheet \"%s\"", refName);
            try {
                SheetFetchResult ref = fetchSheet(service, refName, sheetIdByName.get(refName));
                mergeInto(merged, mergedIdx, ref.entries());
            } catch (Exception e) {
                SkipperLogger.warn("Cannot fetch reference sheet \"" + refName + "\": " + e.getMessage());
            }
        }

        SkipperLogger.logf("Loaded %d test entries total.", merged.size());
        return new FetchAllResult(primary, List.copyOf(merged), service);
    }

    private void mergeInto(List<TestEntry> target, Map<String, Integer> idx,
            List<TestEntry> incoming) {
        for (TestEntry entry : incoming) {
            String key = TestIdHelper.normalize(entry.testId());
            if (idx.containsKey(key)) {
                TestEntry existing = target.get(idx.get(key));
                if (isMoreRestrictive(entry.disabledUntil(), existing.disabledUntil())) {
                    target.set(idx.get(key),
                            new TestEntry(existing.testId(), entry.disabledUntil(), existing.notes()));
                }
            } else {
                idx.put(key, target.size());
                target.add(entry);
            }
        }
    }

    private boolean isMoreRestrictive(Instant candidate, Instant current) {
        if (candidate == null) return false;
        if (current == null) return true;
        return candidate.isAfter(current);
    }

    private SheetFetchResult fetchSheet(Sheets service, String sheetName, int sheetId)
            throws IOException {
        ValueRange response = service.spreadsheets().values()
                .get(config.spreadsheetId(), sheetName)
                .execute();

        @SuppressWarnings("unchecked")
        List<List<Object>> rawRows = (List<List<Object>>) (List<?>) (
                response.getValues() != null ? response.getValues() : List.of());

        if (rawRows.isEmpty()) {
            return new SheetFetchResult(sheetName, sheetId, rawRows, List.of(), List.of());
        }

        List<String> header = rawRows.get(0).stream()
                .map(h -> h != null ? h.toString().strip() : "")
                .toList();

        int testIdIdx = indexOf(header, config.testIdColumn());
        if (testIdIdx < 0) {
            throw new IllegalStateException(
                    "[skipper] Column \"" + config.testIdColumn() + "\" not found in sheet \""
                            + sheetName + "\". Found: " + String.join(", ", header));
        }

        int disabledUntilIdx = indexOf(header, config.disabledUntilColumn());
        int notesIdx = indexOf(header, "notes");

        List<TestEntry> entries = new ArrayList<>();
        for (int i = 1; i < rawRows.size(); i++) {
            List<Object> row = rawRows.get(i);

            String testId = testIdIdx < row.size() && row.get(testIdIdx) != null
                    ? row.get(testIdIdx).toString().strip()
                    : null;
            if (testId == null || testId.isBlank()) continue;

            Instant disabledUntil = null;
            if (disabledUntilIdx >= 0 && disabledUntilIdx < row.size()
                    && row.get(disabledUntilIdx) != null) {
                String raw = row.get(disabledUntilIdx).toString().strip();
                if (!raw.isBlank()) {
                    disabledUntil = parseDate(raw, i + 1, sheetName);
                }
            }

            String notes = notesIdx >= 0 && notesIdx < row.size() && row.get(notesIdx) != null
                    ? row.get(notesIdx).toString()
                    : "";

            entries.add(new TestEntry(testId, disabledUntil, notes));
        }

        SkipperLogger.logf("Sheet \"%s\": %d entries parsed.", sheetName, entries.size());
        return new SheetFetchResult(sheetName, sheetId, rawRows, header, List.copyOf(entries));
    }

    private int createSheet(Sheets service, String spreadsheetId, String sheetName)
            throws IOException {
        BatchUpdateSpreadsheetRequest req = new BatchUpdateSpreadsheetRequest()
                .setRequests(List.of(new Request().setAddSheet(
                        new AddSheetRequest().setProperties(
                                new SheetProperties().setTitle(sheetName)))));
        var response = service.spreadsheets().batchUpdate(spreadsheetId, req).execute();
        int sheetId = response.getReplies().get(0).getAddSheet()
                .getProperties().getSheetId().intValue();
        SkipperLogger.logf("Created sheet \"%s\" (id=%d).", sheetName, sheetId);

        // Write header row
        ValueRange header = new ValueRange().setValues(List.of(
                List.of(config.testIdColumn(), config.disabledUntilColumn(), "notes")));
        service.spreadsheets().values()
                .append(spreadsheetId, sheetName, header)
                .setValueInputOption("RAW")
                .execute();
        return sheetId;
    }

    Sheets buildService() throws IOException {
        byte[] credBytes = config.credentials().resolve();
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(credBytes))
                .createScoped(SheetsScopes.SPREADSHEETS);

        try {
            return new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (Exception e) {
            throw new IOException("[skipper] Failed to build Sheets service", e);
        }
    }

    private static int indexOf(List<String> header, String column) {
        for (int i = 0; i < header.size(); i++) {
            if (column.equals(header.get(i))) return i;
        }
        return -1;
    }

    /**
     * Parses a {@code disabledUntil} date string.
     *
     * <p>Only {@code YYYY-MM-DD} is accepted. Malformed values throw immediately so bad
     * spreadsheet data is caught at startup, not silently mid-run.
     *
     * <p>The returned instant is the start of the <em>following</em> UTC day, so a test
     * marked disabled until {@code 2026-04-01} remains disabled through the end of that
     * calendar day (UTC) and re-enables at {@code 2026-04-02T00:00:00Z}. This comparison
     * is timezone-independent: {@code Instant.now().isBefore(result)} yields the same
     * answer on every CI runner regardless of JVM default timezone.
     *
     * @param raw      raw cell value
     * @param rowNum   1-based row number, used in the error message
     * @param sheetName sheet name, used in the error message
     * @return the expiry instant, or {@code null} if {@code raw} is null or blank
     * @throws IllegalArgumentException if {@code raw} does not match {@code YYYY-MM-DD}
     */
    static Instant parseDate(String raw, int rowNum, String sheetName) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.strip();
        if (!DATE_RE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "[skipper] Row " + rowNum + " in \"" + sheetName
                            + "\": invalid disabledUntil \"" + raw + "\". Use YYYY-MM-DD.");
        }
        LocalDate date = LocalDate.parse(trimmed);
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
