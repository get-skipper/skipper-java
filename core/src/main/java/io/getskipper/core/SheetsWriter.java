package io.getskipper.core;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Request;
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
     * Reconciles the spreadsheet with the test IDs discovered during a test run.
     *
     * <ul>
     *   <li>Appends rows for test IDs not yet in the sheet.</li>
     *   <li>Detects orphaned rows (IDs in the sheet that were not discovered). By default
     *       these are logged but <em>not</em> deleted, making sync safe for multi-module
     *       projects where different Gradle tasks each contribute a subset of the full suite.
     *       Set {@code SKIPPER_SYNC_ALLOW_DELETE=true} to actually prune them.</li>
     * </ul>
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

        Set<String> discoveredNormalized = new HashSet<>();
        for (String id : discoveredTestIds) {
            discoveredNormalized.add(TestIdHelper.normalize(id));
        }

        Set<String> existingIds = new HashSet<>();
        for (TestEntry entry : primary.entries()) {
            existingIds.add(TestIdHelper.normalize(entry.testId()));
        }

        // Detect orphaned rows (in sheet but not in discoveredTestIds).
        // rawRows[0] is the header; data rows start at index 1.
        List<Integer> orphanedRowIndices = new ArrayList<>();
        List<List<Object>> rawRows = primary.rawRows();
        for (int i = 1; i < rawRows.size(); i++) {
            List<Object> row = rawRows.get(i);
            if (testIdColIdx >= row.size()) continue;
            Object cell = row.get(testIdColIdx);
            if (cell == null) continue;
            String id = cell.toString().strip();
            if (id.isBlank()) continue;
            if (!discoveredNormalized.contains(TestIdHelper.normalize(id))) {
                orphanedRowIndices.add(i);
            }
        }

        // Handle orphaned rows.
        boolean allowDeletes = "true".equalsIgnoreCase(System.getenv("SKIPPER_SYNC_ALLOW_DELETE"));
        if (!orphanedRowIndices.isEmpty()) {
            if (!allowDeletes) {
                SkipperLogger.logf("[skipper] %d orphaned row(s) found in \"%s\".",
                        orphanedRowIndices.size(), sheetName);
                SkipperLogger.log("[skipper] Set SKIPPER_SYNC_ALLOW_DELETE=true to prune them.");
            } else {
                deleteRows(spreadsheetId, primary.sheetId(), orphanedRowIndices);
                SkipperLogger.logf("Sync: deleted %d orphaned row(s).", orphanedRowIndices.size());
            }
        }

        // Append new test IDs.
        Set<String> toAdd = new LinkedHashSet<>();
        for (String id : discoveredTestIds) {
            if (!existingIds.contains(TestIdHelper.normalize(id))) {
                toAdd.add(id);
            }
        }

        if (toAdd.isEmpty()) {
            SkipperLogger.log("Sync: no new test IDs to append.");
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

    /**
     * Deletes the specified sheet rows (0-based indices) in a single batch update.
     * Rows are deleted in reverse order so earlier indices remain valid after each deletion.
     */
    private void deleteRows(String spreadsheetId, int sheetId, List<Integer> rowIndices)
            throws IOException {
        List<Request> requests = new ArrayList<>();
        // Iterate in reverse to avoid index shifting
        for (int i = rowIndices.size() - 1; i >= 0; i--) {
            int idx = rowIndices.get(i);
            requests.add(new Request().setDeleteDimension(
                    new DeleteDimensionRequest().setRange(
                            new DimensionRange()
                                    .setSheetId(sheetId)
                                    .setDimension("ROWS")
                                    .setStartIndex(idx)
                                    .setEndIndex(idx + 1))));
        }
        service.spreadsheets()
                .batchUpdate(spreadsheetId, new BatchUpdateSpreadsheetRequest().setRequests(requests))
                .execute();
    }

    private static int indexOf(List<String> header, String column) {
        for (int i = 0; i < header.size(); i++) {
            if (column.equals(header.get(i))) return i;
        }
        return 0;
    }
}
