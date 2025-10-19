package banking.report.analytics;

import java.time.LocalDate;

public final class TrendPoint {
    private final LocalDate date;
    private final double totalCredits;
    private final double totalDebits;
    private final int transactionCount;

    TrendPoint(LocalDate date, double totalCredits, double totalDebits, int transactionCount) {
        this.date = date;
        this.totalCredits = totalCredits;
        this.totalDebits = totalDebits;
        this.transactionCount = transactionCount;
    }

    public LocalDate getDate() {
        return date;
    }

    public double getTotalCredits() {
        return totalCredits;
    }

    public double getTotalDebits() {
        return totalDebits;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public double getNetFlow() {
        return totalCredits - totalDebits;
    }
}
