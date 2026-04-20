package io.getskipper.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and emits the quarantine debt report at the end of every Skipper run.
 *
 * <p>The report is written to {@code GITHUB_STEP_SUMMARY} (appended) when that env var is set;
 * otherwise it is printed to stdout. A {@code skipper-report.json} artifact is always written
 * to the working directory.
 */
public final class SkipperReporter {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private SkipperReporter() {}

    /**
     * Derives the quarantine report from the resolver's in-memory cache.
     *
     * <ul>
     *   <li><b>suppressedCount</b>: entries with {@code disabledUntil} in the future.</li>
     *   <li><b>expiringThisWeek</b>: suppressed entries expiring within 7 days.</li>
     *   <li><b>reenabledThisRun</b>: entries that have a past {@code disabledUntil} (expired).</li>
     *   <li><b>quarantineDaysDebt</b>: Σ (disabledUntil − today) in days for active suppressions.</li>
     * </ul>
     */
    public static QuarantineReport buildReport(SkipperResolver resolver) {
        Map<String, Instant> entries = resolver.getCacheEntries();
        Instant now = Instant.now();
        Instant weekFromNow = now.plus(7, ChronoUnit.DAYS);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        int suppressedCount = 0;
        long quarantineDaysDebt = 0;
        List<String> expiringThisWeek = new ArrayList<>();
        List<String> reenabledThisRun = new ArrayList<>();

        for (Map.Entry<String, Instant> e : entries.entrySet()) {
            Instant until = e.getValue();
            if (until == null) continue;

            if (until.isAfter(now)) {
                suppressedCount++;
                LocalDate expiryDate = until.atZone(ZoneOffset.UTC).toLocalDate();
                quarantineDaysDebt += ChronoUnit.DAYS.between(today, expiryDate);
                if (!until.isAfter(weekFromNow)) {
                    expiringThisWeek.add(e.getKey());
                }
            } else {
                reenabledThisRun.add(e.getKey());
            }
        }

        Collections.sort(expiringThisWeek);
        Collections.sort(reenabledThisRun);
        return new QuarantineReport(suppressedCount, expiringThisWeek, reenabledThisRun, quarantineDaysDebt);
    }

    /**
     * Emits the report as markdown (to {@code GITHUB_STEP_SUMMARY} or stdout) and writes
     * {@code skipper-report.json} in the current working directory.
     */
    public static void emitSummary(QuarantineReport report) throws IOException {
        String md = buildMarkdownSummary(report);
        String summaryFile = System.getenv("GITHUB_STEP_SUMMARY");
        if (summaryFile != null) {
            Files.writeString(Path.of(summaryFile), md, StandardOpenOption.APPEND);
        } else {
            System.out.println(md);
        }
        Files.writeString(Path.of("skipper-report.json"), toJson(report));
    }

    private static String buildMarkdownSummary(QuarantineReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Skipper Quarantine Report\n\n");
        sb.append("| Metric | Value |\n");
        sb.append("|--------|-------|\n");
        sb.append("| Suppressed tests | ").append(report.suppressedCount()).append(" |\n");
        sb.append("| Expiring this week | ").append(report.expiringThisWeek().size()).append(" |\n");
        sb.append("| Re-enabled this run | ").append(report.reenabledThisRun().size()).append(" |\n");
        sb.append("| Quarantine-days debt | ").append(report.quarantineDaysDebt()).append(" |\n");

        if (!report.expiringThisWeek().isEmpty()) {
            sb.append("\n### Expiring this week\n\n");
            for (String id : report.expiringThisWeek()) {
                sb.append("- `").append(id).append("`\n");
            }
        }

        if (!report.reenabledThisRun().isEmpty()) {
            sb.append("\n### Re-enabled this run\n\n");
            for (String id : report.reenabledThisRun()) {
                sb.append("- `").append(id).append("`\n");
            }
        }

        return sb.toString();
    }

    private static String toJson(QuarantineReport report) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("suppressedCount", report.suppressedCount());
        map.put("expiringThisWeek", report.expiringThisWeek());
        map.put("reenabledThisRun", report.reenabledThisRun());
        map.put("quarantineDaysDebt", report.quarantineDaysDebt());
        return MAPPER.writeValueAsString(map);
    }
}
