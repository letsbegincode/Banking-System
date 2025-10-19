package banking.ui.presenter;

import banking.report.AnalyticsReport;
import banking.report.AnomalyInsight;
import banking.report.BalanceSnapshot;
import banking.report.TrendPoint;
import banking.ui.console.ConsoleIO;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

public class AnalyticsPresenter {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ConsoleIO io;

    public AnalyticsPresenter(ConsoleIO io) {
        this.io = Objects.requireNonNull(io, "io");
    }

    public AnalyticsPresenter() {
        this.io = null;
    }

    public void showSummary(AnalyticsReport report) {
        if (io == null) {
            throw new IllegalStateException("Console presenter not configured");
        }
        io.subHeading("Portfolio Analytics Summary");
        io.printlnBold("Reporting Window: ", report.startDate() + " to " + report.endDate());
        io.printlnBold("Total Balance: ", String.format(Locale.US, "%.2f", report.totalBalance()));
        io.printlnBold("Average Balance: ", String.format(Locale.US, "%.2f", report.averageBalance()));
        io.printlnBold("Median Balance: ", String.format(Locale.US, "%.2f", report.medianBalance()));
        io.printlnBold("Total Inflow: ", String.format(Locale.US, "%.2f", report.totalInflow()));
        io.printlnBold("Total Outflow: ", String.format(Locale.US, "%.2f", report.totalOutflow()));

        io.subHeading("Balances");
        io.printTableHeader("%-10s %-20s %-15s %-15s %-15s", "ACCOUNT#", "NAME", "TYPE", "BALANCE", "NET CHANGE");
        for (BalanceSnapshot snapshot : report.balanceSnapshots()) {
            io.println(String.format(Locale.US, "%-10d %-20s %-15s %-15.2f %-15.2f",
                    snapshot.accountNumber(),
                    snapshot.accountHolder(),
                    snapshot.accountType(),
                    snapshot.closingBalance(),
                    snapshot.netChange()));
        }

        io.subHeading("Daily Trend (" + report.trendPoints().size() + " days)");
        for (TrendPoint point : report.trendPoints()) {
            io.println(String.format(Locale.US, "%s -> Net: %.2f, Rolling Avg: %.2f",
                    DATE_FORMATTER.format(point.date()),
                    point.dailyNetChange(),
                    point.rollingAverageChange()));
        }

        if (report.anomalies().isEmpty()) {
            io.success("No anomalies detected in the selected window.");
        } else {
            io.warning("Detected Anomalies:");
            for (AnomalyInsight anomaly : report.anomalies()) {
                io.println(String.format(Locale.US, "#%d %s :: %s (%.2f)",
                        anomaly.accountNumber(),
                        anomaly.accountHolder(),
                        anomaly.description(),
                        anomaly.amount()));
            }
        }
    }

    public String toCsv(AnalyticsReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("metric,value\n");
        builder.append("startDate,").append(report.startDate()).append('\n');
        builder.append("endDate,").append(report.endDate()).append('\n');
        builder.append("totalBalance,").append(formatNumber(report.totalBalance())).append('\n');
        builder.append("averageBalance,").append(formatNumber(report.averageBalance())).append('\n');
        builder.append("medianBalance,").append(formatNumber(report.medianBalance())).append('\n');
        builder.append("totalInflow,").append(formatNumber(report.totalInflow())).append('\n');
        builder.append("totalOutflow,").append(formatNumber(report.totalOutflow())).append('\n');
        builder.append('\n');

        builder.append("accountNumber,accountHolder,accountType,closingBalance,netChange\n");
        for (BalanceSnapshot snapshot : report.balanceSnapshots()) {
            builder.append(snapshot.accountNumber()).append(',')
                    .append(escape(snapshot.accountHolder())).append(',')
                    .append(escape(snapshot.accountType())).append(',')
                    .append(formatNumber(snapshot.closingBalance())).append(',')
                    .append(formatNumber(snapshot.netChange())).append('\n');
        }
        builder.append('\n');

        builder.append("date,dailyNetChange,rollingAverageChange\n");
        for (TrendPoint point : report.trendPoints()) {
            builder.append(DATE_FORMATTER.format(point.date())).append(',')
                    .append(formatNumber(point.dailyNetChange())).append(',')
                    .append(formatNumber(point.rollingAverageChange())).append('\n');
        }
        builder.append('\n');

        builder.append("accountNumber,accountHolder,description,amount\n");
        for (AnomalyInsight anomaly : report.anomalies()) {
            builder.append(anomaly.accountNumber()).append(',')
                    .append(escape(anomaly.accountHolder())).append(',')
                    .append(escape(anomaly.description())).append(',')
                    .append(formatNumber(anomaly.amount())).append('\n');
        }
        return builder.toString();
    }

    public String toJson(AnalyticsReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"startDate\":\"").append(report.startDate()).append("\",");
        builder.append("\"endDate\":\"").append(report.endDate()).append("\",");
        builder.append("\"totalBalance\":").append(formatNumber(report.totalBalance())).append(',');
        builder.append("\"averageBalance\":").append(formatNumber(report.averageBalance())).append(',');
        builder.append("\"medianBalance\":").append(formatNumber(report.medianBalance())).append(',');
        builder.append("\"totalInflow\":").append(formatNumber(report.totalInflow())).append(',');
        builder.append("\"totalOutflow\":").append(formatNumber(report.totalOutflow())).append(',');

        builder.append("\"balances\":[");
        StringJoiner balanceJoiner = new StringJoiner(",");
        for (BalanceSnapshot snapshot : report.balanceSnapshots()) {
            balanceJoiner.add('{'
                    + "\"accountNumber\":" + snapshot.accountNumber()
                    + ",\"accountHolder\":\"" + escapeJson(snapshot.accountHolder()) + "\""
                    + ",\"accountType\":\"" + escapeJson(snapshot.accountType()) + "\""
                    + ",\"closingBalance\":" + formatNumber(snapshot.closingBalance())
                    + ",\"netChange\":" + formatNumber(snapshot.netChange())
                    + '}');
        }
        builder.append(balanceJoiner).append(']');
        builder.append(',');

        builder.append("\"trends\":[");
        StringJoiner trendJoiner = new StringJoiner(",");
        for (TrendPoint point : report.trendPoints()) {
            trendJoiner.add('{'
                    + "\"date\":\"" + DATE_FORMATTER.format(point.date()) + "\""
                    + ",\"dailyNetChange\":" + formatNumber(point.dailyNetChange())
                    + ",\"rollingAverageChange\":" + formatNumber(point.rollingAverageChange())
                    + '}');
        }
        builder.append(trendJoiner).append(']');
        builder.append(',');

        builder.append("\"anomalies\":[");
        StringJoiner anomalyJoiner = new StringJoiner(",");
        for (AnomalyInsight anomaly : report.anomalies()) {
            anomalyJoiner.add('{'
                    + "\"accountNumber\":" + anomaly.accountNumber()
                    + ",\"accountHolder\":\"" + escapeJson(anomaly.accountHolder()) + "\""
                    + ",\"description\":\"" + escapeJson(anomaly.description()) + "\""
                    + ",\"amount\":" + formatNumber(anomaly.amount())
                    + '}');
        }
        builder.append(anomalyJoiner).append(']');
        builder.append('}');
        return builder.toString();
    }

    private String formatNumber(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String escape(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
