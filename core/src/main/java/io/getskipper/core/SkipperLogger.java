package io.getskipper.core;

/**
 * Minimal logger that writes to stderr with a {@code [skipper]} prefix.
 * Debug output is controlled by the {@code SKIPPER_DEBUG} environment variable.
 */
public final class SkipperLogger {

    private static final boolean DEBUG = "true".equalsIgnoreCase(System.getenv("SKIPPER_DEBUG"));

    private SkipperLogger() {}

    public static void log(String message) {
        if (DEBUG) {
            System.err.println("[skipper] " + message);
        }
    }

    public static void logf(String format, Object... args) {
        if (DEBUG) {
            System.err.printf("[skipper] " + format + "%n", args);
        }
    }

    public static void warn(String message) {
        System.err.println("[skipper] WARN: " + message);
    }
}
