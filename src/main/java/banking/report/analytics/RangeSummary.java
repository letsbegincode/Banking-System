package banking.report.analytics;

import java.time.LocalDate;

public final class RangeSummary {
    private final AnalyticsRange range;
    private final int activeAccounts;
    private final int accountsOpened;
    private final int totalTransactions;
    private final double totalCredits;
    private final double totalDebits;
    private final double netCashFlow;
    private final double averageTransactionValue;
    private final LocalDate peakDay;
    private final int peakDayTransactions;

    RangeSummary(AnalyticsRange range,
            int activeAccounts,
            int accountsOpened,
            int totalTransactions,
            double totalCredits,
            double totalDebits,
            double netCashFlow,
            double averageTransactionValue,
            LocalDate peakDay,
            int peakDayTransactions) {
        this.range = range;
        this.activeAccounts = activeAccounts;
        this.accountsOpened = accountsOpened;
        this.totalTransactions = totalTransactions;
        this.totalCredits = totalCredits;
        this.totalDebits = totalDebits;
        this.netCashFlow = netCashFlow;
        this.averageTransactionValue = averageTransactionValue;
        this.peakDay = peakDay;
        this.peakDayTransactions = peakDayTransactions;
    }

    public AnalyticsRange getRange() {
        return range;
    }

    public int getActiveAccounts() {
        return activeAccounts;
    }

    public int getAccountsOpened() {
        return accountsOpened;
    }

    public int getTotalTransactions() {
        return totalTransactions;
    }

    public double getTotalCredits() {
        return totalCredits;
    }

    public double getTotalDebits() {
        return totalDebits;
    }

    public double getNetCashFlow() {
        return netCashFlow;
    }

    public double getAverageTransactionValue() {
        return averageTransactionValue;
    }

    public LocalDate getPeakDay() {
        return peakDay;
    }

    public int getPeakDayTransactions() {
        return peakDayTransactions;
    }
}
