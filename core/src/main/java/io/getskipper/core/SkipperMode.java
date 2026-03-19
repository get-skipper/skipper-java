package io.getskipper.core;

/**
 * Operating mode for Skipper.
 *
 * <ul>
 *   <li>{@link #READ_ONLY} — fetch the spreadsheet and skip disabled tests, but do not modify the sheet.</li>
 *   <li>{@link #SYNC} — after the test run, reconcile the sheet: add new test IDs and remove stale ones.</li>
 * </ul>
 *
 * <p>Controlled by the {@code SKIPPER_MODE} environment variable. Set to {@code sync} to enable sync mode.
 */
public enum SkipperMode {
    READ_ONLY,
    SYNC;

    public static SkipperMode fromEnvironment() {
        return "sync".equalsIgnoreCase(System.getenv("SKIPPER_MODE")) ? SYNC : READ_ONLY;
    }
}
