package io.getskipper.core;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reconciles the primary sheet with the set of test IDs discovered during a test run.
 *
 * <ul>
 *   <li>Appends rows for test IDs that are not yet in the sheet (with empty {@code disabledUntil}).</li>
 *   <li>Never deletes existing rows — this makes sync safe for multi-module projects where
 *       different Gradle test tasks each contribute a subset of the full test suite.</li>
 *   <li>Never modifies reference sheets.</li>
 * </ul>
 *
 * <p>Should be called once, after all tests have run, only when {@link SkipperMode#SYNC} is active.
 */
public final class SheetsWriter {

    private final SkipperConfig config;
    private final Sheets service;

    /**
     * @param config  the Skipper configuration
     * @param service an authenticated Sheets service, typically from {@link SkipperResolver#getSheetsService()}
     */
    public SheetsWriter(SkipperConfig config, Sheets service) {
        this.config = config;
        this.service = service;
    }

    /**
     * Appends any discovered test IDs that are not yet in the sheet.
     * Existing rows are never deleted, making this operation safe to call from multiple
     * Gradle test tasks in a multi-module build.
     *
     * @param discoveredTestIds all test IDs discovered during the test run
     */
    public void sync(Collection<String> discoveredTestIds) throws IOException {
        String spreadsheetId = config.spreadsheetId();

        // Re-fetch the primary sheet to get current state
        SheetsClient client = new SheetsClient(config);
        FetchAllResult fetchResult = client.fetchAll();
        SheetFetchResult primary = fetchResult.primary();
        String sheetName = primary.sheetName();

        List<String> header = primary.header();
        int testIdColIdx = indexOf(header, config.testIdColumn());

        Set<String> existingIds = new HashSet<>();
        for (TestEntry entry : primary.entries()) {
            existingIds.add(TestIdHelper.normalize(entry.testId()));
        }

        Set<String> toAdd = new LinkedHashSet<>();
        for (String id : discoveredTestIds) {
            if (!existingIds.contains(TestIdHelper.normalize(id))) {
                toAdd.add(id);
            }
        }

        if (toAdd.isEmpty()) {
            SkipperLogger.log("Sync: spreadsheet is already up to date.");
            return;
        }

        List<List<Object>> values = new ArrayList<>();
        for (String id : toAdd) {
            List<Object> row = new ArrayList<>();
            for (int i = 0; i < testIdColIdx; i++) row.add("");
            row.add(id);
            values.add(row);
        }

        service.spreadsheets().values()
                .append(spreadsheetId, sheetName, new ValueRange().setValues(values))
                .setValueInputOption("USER_ENTERED")
                .execute();
        SkipperLogger.logf("Sync: appended %d new test ID(s).", toAdd.size());
    }

    private static int indexOf(List<String> header, String column) {
        for (int i = 0; i < header.size(); i++) {
            if (column.equals(header.get(i))) return i;
        }
        return 0;
    }
}
