package banking.transaction;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public abstract class BaseTransaction implements Serializable {
    private static final long serialVersionUID = 1L;
<<<<<<< HEAD

    private final double amount;
    private final String dateTime;
=======
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final double amount;
    private final LocalDateTime timestamp;
>>>>>>> origin/pr/11
    private final String transactionId;

    protected BaseTransaction(double amount) {
        this.amount = amount;
<<<<<<< HEAD
        this.dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
=======
        this.timestamp = LocalDateTime.now();
>>>>>>> origin/pr/11
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

<<<<<<< HEAD
    public String getDateTime() {
        return dateTime;
=======
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDateTime() {
        return timestamp.format(FORMATTER);
>>>>>>> origin/pr/11
    }

    public abstract String getType();

    @Override
    public String toString() {
        return String.format("ID: %s, Amount: %.2f, Type: %s, Date and Time: %s",
<<<<<<< HEAD
            transactionId, amount, getType(), dateTime);
=======
            transactionId, amount, getType(), getDateTime());
>>>>>>> origin/pr/11
    }
}
