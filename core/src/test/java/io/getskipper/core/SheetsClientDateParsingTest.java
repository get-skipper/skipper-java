package io.getskipper.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SheetsClientDateParsingTest {

    @Test
    void parseDate_nullReturnsNull() {
        assertThat(SheetsClient.parseDate(null, 2, "Sheet1")).isNull();
    }

    @Test
    void parseDate_blankReturnsNull() {
        assertThat(SheetsClient.parseDate("   ", 2, "Sheet1")).isNull();
    }

    @Test
    void parseDate_validDate_returnsStartOfNextDayUtc() {
        Instant result = SheetsClient.parseDate("2026-04-01", 2, "Sheet1");
        Instant expected = Instant.parse("2026-04-02T00:00:00Z");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void parseDate_validDate_testDisabledOnThatDay() {
        // A test disabled until 2026-04-01 should still be disabled at 23:59 UTC that day.
        Instant result = SheetsClient.parseDate("2026-04-01", 2, "Sheet1");
        Instant justBeforeMidnight = Instant.parse("2026-04-01T23:59:59Z");
        // disabled = result.isAfter(now), i.e. expiry has not passed yet
        assertThat(result).isAfter(justBeforeMidnight);
    }

    @Test
    void parseDate_validDate_testEnabledNextDay() {
        // A test disabled until 2026-04-01 should be enabled at 00:00:01 on 2026-04-02 UTC.
        Instant result = SheetsClient.parseDate("2026-04-01", 2, "Sheet1");
        Instant startOfNextDay = Instant.parse("2026-04-02T00:00:01Z");
        // enabled = !result.isAfter(now), i.e. expiry has passed
        assertThat(result).isBefore(startOfNextDay);
    }

    @Test
    void parseDate_timezoneConsistency() {
        // The returned instant must be the same regardless of the JVM default timezone.
        // We verify by checking it equals the known UTC value directly.
        Instant result = SheetsClient.parseDate("2026-06-15", 3, "Sheet1");
        assertThat(result).isEqualTo(
                java.time.LocalDate.of(2026, 6, 16).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void parseDate_malformedDate_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> SheetsClient.parseDate("2026-4-1", 5, "Sheet1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Row 5")
                .hasMessageContaining("Sheet1")
                .hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    void parseDate_dateTimeFormat_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> SheetsClient.parseDate("2026-04-01T12:00:00Z", 3, "MySheet"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Row 3")
                .hasMessageContaining("MySheet");
    }

    @Test
    void parseDate_spreadsheetValue_throwsIllegalArgumentException() {
        // Spreadsheet-formatted dates like "April 1, 2026" must be rejected
        assertThatThrownBy(() -> SheetsClient.parseDate("April 1, 2026", 7, "Sheet1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }
}
