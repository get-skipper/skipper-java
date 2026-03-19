package io.getskipper.core;

import java.time.Instant;

/**
 * A single test entry from the Google Spreadsheet.
 *
 * @param testId        the unique test identifier (normalized when used for lookups)
 * @param disabledUntil the date/time until which the test is disabled, or {@code null} if no date is set
 * @param notes         free-text notes column, ignored by Skipper
 */
public record TestEntry(String testId, Instant disabledUntil, String notes) {

    public TestEntry(String testId, Instant disabledUntil) {
        this(testId, disabledUntil, "");
    }
}
