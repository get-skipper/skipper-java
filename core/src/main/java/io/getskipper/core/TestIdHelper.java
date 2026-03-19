package io.getskipper.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utilities for building and normalizing Skipper test IDs.
 *
 * <h2>Test ID format</h2>
 * <pre>{@code com/example/AuthTest.java > AuthTest > methodName}</pre>
 *
 * <ul>
 *   <li>The file path segment is derived from the fully-qualified class name by replacing
 *       {@code .} with {@code /} and appending {@code .java}.</li>
 *   <li>The simple class name is repeated as the first title part.</li>
 *   <li>Inner classes ({@code Outer$Inner}) are represented as {@code Outer > Inner}.</li>
 *   <li>Parts are joined with {@code " > "}.</li>
 * </ul>
 *
 * <h2>Normalization</h2>
 * Lowercase, trim, and collapse internal whitespace to a single space.
 * Used for case-insensitive matching against spreadsheet entries.
 */
public final class TestIdHelper {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PARAM_SUFFIX = Pattern.compile("\\[.*]$");

    private TestIdHelper() {}

    /**
     * Builds a Skipper test ID from a fully-qualified class name and a list of title parts
     * (typically the method name, optionally preceded by enclosing describe/nested class names).
     *
     * @param fqClassName fully-qualified class name, e.g. {@code com.example.AuthTest}
     * @param titleParts  ordered list of name segments (method name last), e.g. {@code ["shouldLogin"]}
     * @return a test ID like {@code "com/example/AuthTest.java > AuthTest > shouldLogin"}
     */
    public static String buildFromClass(String fqClassName, List<String> titleParts) {
        int lastDot = fqClassName.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? fqClassName.substring(lastDot + 1) : fqClassName;
        String nsPath = lastDot >= 0 ? fqClassName.substring(0, lastDot).replace('.', '/') : "";

        // Strip inner-class qualifiers from the file path component: "Outer$Inner" → "Outer"
        String outerSimpleName = simpleName.contains("$")
                ? simpleName.substring(0, simpleName.indexOf('$'))
                : simpleName;

        String filePath = nsPath.isEmpty()
                ? outerSimpleName + ".java"
                : nsPath + "/" + outerSimpleName + ".java";

        // Build title: simple class name (with $ → " > ") then the provided parts
        String classTitle = simpleName.replace('$', '>').replace(">", " > ");
        String parts = String.join(" > ", titleParts);
        String title = parts.isEmpty() ? classTitle : classTitle + " > " + parts;

        return filePath + " > " + title;
    }

    /**
     * Normalizes a test ID for case-insensitive, whitespace-insensitive comparison.
     *
     * @param testId raw test ID
     * @return normalized form (lowercase, trimmed, collapsed whitespace)
     */
    public static String normalize(String testId) {
        return WHITESPACE.matcher(testId.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /**
     * Strips parameterized test index suffixes such as {@code [1]}, {@code [param value]}.
     *
     * @param displayName the test display name
     * @return display name with any trailing {@code [...]} suffix removed
     */
    public static String stripParamSuffix(String displayName) {
        return PARAM_SUFFIX.matcher(displayName).replaceAll("").strip();
    }
}
