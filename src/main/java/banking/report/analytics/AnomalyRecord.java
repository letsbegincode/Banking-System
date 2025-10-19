package banking.report.analytics;

import java.time.LocalDateTime;

public final class AnomalyRecord {
    private final int accountNumber;
    private final String accountHolder;
    private final String transactionType;
    private final double amount;
    private final LocalDateTime timestamp;
    private final double zScore;
    private final String reason;

    AnomalyRecord(int accountNumber,
            String accountHolder,
            String transactionType,
            double amount,
            LocalDateTime timestamp,
            double zScore,
            String reason) {
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.transactionType = transactionType;
        this.amount = amount;
        this.timestamp = timestamp;
        this.zScore = zScore;
        this.reason = reason;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public double getZScore() {
        return zScore;
    }

    public String getReason() {
        return reason;
    }
}
