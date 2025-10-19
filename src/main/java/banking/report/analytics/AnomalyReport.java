package banking.report.analytics;

import java.util.Collections;
import java.util.List;

public final class AnomalyReport {
    private final AnalyticsRange range;
    private final double absoluteThreshold;
    private final double deviationMultiplier;
    private final List<AnomalyRecord> anomalies;

    AnomalyReport(AnalyticsRange range,
            double absoluteThreshold,
            double deviationMultiplier,
            List<AnomalyRecord> anomalies) {
        this.range = range;
        this.absoluteThreshold = absoluteThreshold;
        this.deviationMultiplier = deviationMultiplier;
        this.anomalies = List.copyOf(anomalies);
    }

    public AnalyticsRange getRange() {
        return range;
    }

    public double getAbsoluteThreshold() {
        return absoluteThreshold;
    }

    public double getDeviationMultiplier() {
        return deviationMultiplier;
    }

    public List<AnomalyRecord> getAnomalies() {
        return Collections.unmodifiableList(anomalies);
    }
}
