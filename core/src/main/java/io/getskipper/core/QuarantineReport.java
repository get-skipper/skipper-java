package io.getskipper.core;

import java.util.List;

/**
 * Snapshot of the quarantine state at the end of a test run.
 *
 * @param suppressedCount    number of tests currently suppressed (disabledUntil in the future)
 * @param expiringThisWeek   test IDs whose suppression expires within 7 days
 * @param reenabledThisRun   test IDs that were in the spreadsheet but whose suppression has expired
 * @param quarantineDaysDebt sum of (disabledUntil − today) in days across all active suppressions
 */
public record QuarantineReport(
        int suppressedCount,
        List<String> expiringThisWeek,
        List<String> reenabledThisRun,
        long quarantineDaysDebt) {}
