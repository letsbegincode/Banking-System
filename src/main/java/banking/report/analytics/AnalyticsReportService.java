package banking.report.analytics;

import banking.service.Bank;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Facade that queues analytics workloads on the bank's asynchronous infrastructure.
 */
public class AnalyticsReportService {
    private final Bank bank;
    private final TrendAnalyticsService trendAnalyticsService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final RangeAnalyticsService rangeAnalyticsService;

    public AnalyticsReportService(Bank bank,
            TrendAnalyticsService trendAnalyticsService,
            AnomalyDetectionService anomalyDetectionService,
            RangeAnalyticsService rangeAnalyticsService) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.trendAnalyticsService = Objects.requireNonNull(trendAnalyticsService, "trendAnalyticsService");
        this.anomalyDetectionService = Objects.requireNonNull(anomalyDetectionService, "anomalyDetectionService");
        this.rangeAnalyticsService = Objects.requireNonNull(rangeAnalyticsService, "rangeAnalyticsService");
    }

    public CompletableFuture<TrendReport> queueTrendReport(AnalyticsRange range) {
        Objects.requireNonNull(range, "range");
        return bank.submitAnalyticsTask(() -> trendAnalyticsService.generate(bank.getAllAccounts(), range),
                describe("Transaction trend", range));
    }

    public CompletableFuture<AnomalyReport> queueAnomalyReport(AnalyticsRange range,
            double absoluteThreshold,
            double deviationMultiplier) {
        Objects.requireNonNull(range, "range");
        return bank.submitAnalyticsTask(
                () -> anomalyDetectionService.detect(bank.getAllAccounts(), range, absoluteThreshold, deviationMultiplier),
                describe("Transaction anomaly", range));
    }

    public CompletableFuture<RangeSummary> queueRangeSummary(AnalyticsRange range) {
        Objects.requireNonNull(range, "range");
        return bank.submitAnalyticsTask(() -> rangeAnalyticsService.summarize(bank.getAllAccounts(), range),
                describe("Range KPI", range));
    }

    private String describe(String reportName, AnalyticsRange range) {
        return String.format(Locale.US,
                "%s report [%s - %s]",
                reportName,
                range.getStartDate(),
                range.getEndDate());
    }
}
