package io.getskipper.core;

import java.util.List;

/**
 * The result of fetching a single sheet tab from the spreadsheet.
 *
 * @param sheetName the name of the sheet
 * @param sheetId   the numeric sheet ID (used for batch update operations)
 * @param rawRows   all rows including the header row (used by {@link SheetsWriter})
 * @param header    the header row (column names)
 * @param entries   parsed test entries (rows after the header)
 */
public record SheetFetchResult(
        String sheetName,
        int sheetId,
        List<List<Object>> rawRows,
        List<String> header,
        List<TestEntry> entries) {}
