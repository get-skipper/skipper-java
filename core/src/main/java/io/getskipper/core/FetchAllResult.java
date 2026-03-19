package io.getskipper.core;

import com.google.api.services.sheets.v4.Sheets;
import java.util.List;

/**
 * The combined result of fetching the primary sheet and all reference sheets.
 *
 * <p>The {@link #entries()} collection is deduplicated: when the same test ID appears in multiple
 * sheets, the most restrictive (latest) {@code disabledUntil} date wins.
 *
 * @param primary the primary sheet fetch result
 * @param entries merged, deduplicated test entries from all sheets
 * @param service the authenticated Sheets API service, reused by {@link SheetsWriter} to avoid
 *                re-authenticating
 */
public record FetchAllResult(SheetFetchResult primary, List<TestEntry> entries, Sheets service) {}
