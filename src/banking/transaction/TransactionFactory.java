package banking.transaction;

import banking.snapshot.TransactionSnapshot;
import banking.snapshot.TransactionType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Reconstructs concrete transaction instances from durable snapshots.
 */
public final class TransactionFactory {
    private TransactionFactory() {
    }

    public static BaseTransaction fromSnapshot(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        TransactionType type = snapshot.type();
        LocalDateTime timestamp = LocalDateTime.parse(snapshot.timestamp());
        String transactionId = snapshot.transactionId();
        double amount = snapshot.amount();
        return switch (type) {
            case DEPOSIT -> new DepositTransaction(amount, timestamp, transactionId);
            case WITHDRAWAL -> new WithdrawalTransaction(amount, timestamp, transactionId);
            case INTEREST -> new InterestTransaction(amount, timestamp, transactionId);
            case TRANSFER_OUT -> new TransferTransaction(amount,
                    Objects.requireNonNull(snapshot.targetAccount(), "targetAccount"), timestamp, transactionId);
            case TRANSFER_IN -> new TransferReceiveTransaction(amount,
                    Objects.requireNonNull(snapshot.sourceAccount(), "sourceAccount"), timestamp, transactionId);
        };
    }
}
