package banking.report.format;

import banking.report.analytics.AnalyticsRange;
import banking.report.analytics.AnomalyRecord;
import banking.report.analytics.AnomalyReport;
import banking.report.analytics.RangeSummary;
import banking.report.analytics.TrendPoint;
import banking.report.analytics.TrendReport;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ReportFormatter {
    private static final Locale LOCALE = Locale.US;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String toJson(TrendReport report) {
        StringBuilder builder = new StringBuilder();
        appendRange(builder, report.getRange());
        builder.insert(0, "{");
        builder.append(",\"totals\":{")
                .append(String.format(LOCALE, "\"credits\":%.2f,", report.getTotalCredits()))
                .append(String.format(LOCALE, "\"debits\":%.2f,", report.getTotalDebits()))
                .append(String.format(LOCALE, "\"net\":%.2f", report.getNetFlow()))
                .append("},\"points\":[");
        boolean first = true;
        for (TrendPoint point : report.getPoints()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                    .append("\"date\":\"").append(point.getDate()).append("\",")
                    .append(String.format(LOCALE, "\"credits\":%.2f,", point.getTotalCredits()))
                    .append(String.format(LOCALE, "\"debits\":%.2f,", point.getTotalDebits()))
                    .append(String.format(LOCALE, "\"net\":%.2f,", point.getNetFlow()))
                    .append("\"transactions\":").append(point.getTransactionCount())
                    .append('}');
        }
        builder.append("]}");
        return builder.toString();
    }

    public String toCsv(TrendReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("date,totalCredits,totalDebits,netFlow,transactionCount\n");
        for (TrendPoint point : report.getPoints()) {
            builder.append(point.getDate()).append(',')
                    .append(String.format(LOCALE, "%.2f", point.getTotalCredits())).append(',')
                    .append(String.format(LOCALE, "%.2f", point.getTotalDebits())).append(',')
                    .append(String.format(LOCALE, "%.2f", point.getNetFlow())).append(',')
                    .append(point.getTransactionCount()).append('\n');
        }
        return builder.toString();
    }

    public String toJson(AnomalyReport report) {
        StringBuilder builder = new StringBuilder();
        appendRange(builder, report.getRange());
        builder.insert(0, "{");
        builder.append(String.format(LOCALE, ",\"absoluteThreshold\":%.2f", report.getAbsoluteThreshold()))
                .append(String.format(LOCALE, ",\"deviationMultiplier\":%.2f", report.getDeviationMultiplier()))
                .append(",\"anomalies\":[");
        boolean first = true;
        for (AnomalyRecord anomaly : report.getAnomalies()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                    .append("\"accountNumber\":").append(anomaly.getAccountNumber()).append(',')
                    .append("\"holder\":\"").append(escapeJson(anomaly.getAccountHolder())).append("\",")
                    .append("\"type\":\"").append(escapeJson(anomaly.getTransactionType())).append("\",")
                    .append(String.format(LOCALE, "\"amount\":%.2f,", anomaly.getAmount()))
                    .append("\"timestamp\":\"")
                    .append(anomaly.getTimestamp().format(TIMESTAMP_FORMAT))
                    .append("\",")
                    .append(String.format(LOCALE, "\"zScore\":%.2f,", anomaly.getZScore()))
                    .append("\"reason\":\"").append(escapeJson(anomaly.getReason())).append("\"}");
        }
        builder.append("]}");
        return builder.toString();
    }

    public String toCsv(AnomalyReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("accountNumber,holder,type,amount,timestamp,zScore,reason\n");
        for (AnomalyRecord anomaly : report.getAnomalies()) {
            builder.append(anomaly.getAccountNumber()).append(',')
                    .append('"').append(escapeCsv(anomaly.getAccountHolder())).append('"').append(',')
                    .append('"').append(escapeCsv(anomaly.getTransactionType())).append('"').append(',')
                    .append(String.format(LOCALE, "%.2f", anomaly.getAmount())).append(',')
                    .append('"').append(anomaly.getTimestamp().format(TIMESTAMP_FORMAT)).append('"').append(',')
                    .append(String.format(LOCALE, "%.2f", anomaly.getZScore())).append(',')
                    .append('"').append(escapeCsv(anomaly.getReason())).append('"').append('\n');
        }
        return builder.toString();
    }

    public String toJson(RangeSummary summary) {
        StringBuilder builder = new StringBuilder();
        appendRange(builder, summary.getRange());
        builder.insert(0, "{");
        builder.append(",\"activeAccounts\":").append(summary.getActiveAccounts())
                .append(",\"accountsOpened\":").append(summary.getAccountsOpened())
                .append(",\"totalTransactions\":").append(summary.getTotalTransactions())
                .append(String.format(LOCALE, ",\"totalCredits\":%.2f", summary.getTotalCredits()))
                .append(String.format(LOCALE, ",\"totalDebits\":%.2f", summary.getTotalDebits()))
                .append(String.format(LOCALE, ",\"netCashFlow\":%.2f", summary.getNetCashFlow()))
                .append(String.format(LOCALE, ",\"averageTransactionValue\":%.2f", summary.getAverageTransactionValue()));
        if (summary.getPeakDay() != null) {
            builder.append(",\"peakDay\":\"").append(summary.getPeakDay()).append("\",")
                    .append("\"peakDayTransactions\":").append(summary.getPeakDayTransactions());
        } else {
            builder.append(",\"peakDay\":null,\"peakDayTransactions\":0");
        }
        builder.append('}');
        return builder.toString();
    }

    public String toCsv(RangeSummary summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("metric,value\n");
        builder.append("startDate,").append(summary.getRange().getStartDate()).append('\n');
        builder.append("endDate,").append(summary.getRange().getEndDate()).append('\n');
        builder.append("activeAccounts,").append(summary.getActiveAccounts()).append('\n');
        builder.append("accountsOpened,").append(summary.getAccountsOpened()).append('\n');
        builder.append("totalTransactions,").append(summary.getTotalTransactions()).append('\n');
        builder.append("totalCredits,")
                .append(String.format(LOCALE, "%.2f", summary.getTotalCredits())).append('\n');
        builder.append("totalDebits,")
                .append(String.format(LOCALE, "%.2f", summary.getTotalDebits())).append('\n');
        builder.append("netCashFlow,")
                .append(String.format(LOCALE, "%.2f", summary.getNetCashFlow())).append('\n');
        builder.append("averageTransactionValue,")
                .append(String.format(LOCALE, "%.2f", summary.getAverageTransactionValue())).append('\n');
        builder.append("peakDay,")
                .append(summary.getPeakDay() != null ? summary.getPeakDay() : "").append('\n');
        builder.append("peakDayTransactions,").append(summary.getPeakDayTransactions()).append('\n');
        return builder.toString();
    }

    private void appendRange(StringBuilder builder, AnalyticsRange range) {
        builder.append("\"range\":{\"start\":\"")
                .append(range.getStartDate())
                .append("\",\"end\":\"")
                .append(range.getEndDate())
                .append("\"}");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(character);
            }
        }
        return builder.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\"\"");
    }
}
