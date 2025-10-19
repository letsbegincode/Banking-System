package banking.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record AnalyticsReport(LocalDate startDate,
                              LocalDate endDate,
                              double totalBalance,
                              double averageBalance,
                              double medianBalance,
                              double totalInflow,
                              double totalOutflow,
                              List<BalanceSnapshot> balanceSnapshots,
                              List<TrendPoint> trendPoints,
                              List<AnomalyInsight> anomalies) {

    public AnalyticsReport {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        balanceSnapshots = List.copyOf(balanceSnapshots);
        trendPoints = List.copyOf(trendPoints);
        anomalies = List.copyOf(anomalies);
    }
}
