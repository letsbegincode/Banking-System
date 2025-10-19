package banking.report.analytics;

import java.util.Collections;
import java.util.List;

public final class TrendReport {
    private final AnalyticsRange range;
    private final List<TrendPoint> points;
    private final double totalCredits;
    private final double totalDebits;

    TrendReport(AnalyticsRange range, List<TrendPoint> points, double totalCredits, double totalDebits) {
        this.range = range;
        this.points = List.copyOf(points);
        this.totalCredits = totalCredits;
        this.totalDebits = totalDebits;
    }

    public AnalyticsRange getRange() {
        return range;
    }

    public List<TrendPoint> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public double getTotalCredits() {
        return totalCredits;
    }

    public double getTotalDebits() {
        return totalDebits;
    }

    public double getNetFlow() {
        return totalCredits - totalDebits;
    }
}
