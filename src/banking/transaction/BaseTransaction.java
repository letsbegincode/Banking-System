package banking.transaction;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public abstract class BaseTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double amount;
    private final String dateTime;
    private final String transactionId;

    protected BaseTransaction(double amount) {
        this.amount = amount;
        this.dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.transactionId = generateTransactionId();
    }

    private String generateTransactionId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public double getAmount() {
        return amount;
    }

    public String getDateTime() {
        return dateTime;
    }

    public abstract String getType();

    @Override
    public String toString() {
        return String.format("ID: %s, Amount: %.2f, Type: %s, Date and Time: %s",
            transactionId, amount, getType(), dateTime);
    }
}
