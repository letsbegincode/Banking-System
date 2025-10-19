package banking.report.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object describing the inclusive range used for analytics reports.
 */
public final class AnalyticsRange {
    private final LocalDate startDate;
    private final LocalDate endDate;

    public AnalyticsRange(LocalDate startDate, LocalDate endDate) {
        LocalDate start = Objects.requireNonNull(startDate, "startDate");
        LocalDate end = Objects.requireNonNull(endDate, "endDate");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date must be on or after the start date");
        }
        this.startDate = start;
        this.endDate = end;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDateTime getStartTimestamp() {
        return startDate.atStartOfDay();
    }

    public LocalDateTime getEndTimestamp() {
        return endDate.plusDays(1).atStartOfDay().minusNanos(1);
    }

    public boolean contains(LocalDate date) {
        return (date.isAfter(startDate) || date.isEqual(startDate))
                && (date.isBefore(endDate) || date.isEqual(endDate));
    }
}
