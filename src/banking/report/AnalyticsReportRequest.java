package banking.report;

import java.time.LocalDate;
import java.util.Objects;

public final class AnalyticsReportRequest {
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final double largeTransactionThreshold;
    private final int rollingWindowDays;

    private AnalyticsReportRequest(LocalDate startDate,
            LocalDate endDate,
            double largeTransactionThreshold,
            int rollingWindowDays) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.largeTransactionThreshold = largeTransactionThreshold;
        this.rollingWindowDays = rollingWindowDays;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public double getLargeTransactionThreshold() {
        return largeTransactionThreshold;
    }

    public int getRollingWindowDays() {
        return rollingWindowDays;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AnalyticsReportRequest standard(LocalDate startDate, LocalDate endDate) {
        return builder()
                .withStartDate(startDate)
                .withEndDate(endDate)
                .build();
    }

    public static final class Builder {
        private LocalDate startDate = LocalDate.now().minusDays(30);
        private LocalDate endDate = LocalDate.now();
        private double largeTransactionThreshold = 5000.0;
        private int rollingWindowDays = 7;

        private Builder() {
        }

        public Builder withStartDate(LocalDate startDate) {
            if (startDate != null) {
                this.startDate = startDate;
            }
            return this;
        }

        public Builder withEndDate(LocalDate endDate) {
            if (endDate != null) {
                this.endDate = endDate;
            }
            return this;
        }

        public Builder withLargeTransactionThreshold(double threshold) {
            if (threshold > 0) {
                this.largeTransactionThreshold = threshold;
            }
            return this;
        }

        public Builder withRollingWindowDays(int days) {
            if (days > 0) {
                this.rollingWindowDays = days;
            }
            return this;
        }

        public AnalyticsReportRequest build() {
            Objects.requireNonNull(startDate, "startDate");
            Objects.requireNonNull(endDate, "endDate");
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("End date must not be before start date");
            }
            return new AnalyticsReportRequest(startDate, endDate, largeTransactionThreshold, rollingWindowDays);
        }
    }
}
