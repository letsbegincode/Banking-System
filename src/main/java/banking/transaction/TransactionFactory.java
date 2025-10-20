package banking.transaction;

import banking.snapshot.TransactionSnapshot;
import banking.snapshot.TransactionType;

import java.time.LocalDateTime;
import java.util.Objects;

import banking.util.DateTimeParsers;

/**
 * Reconstructs concrete transaction instances from durable snapshots.
 */
public final class TransactionFactory {
    private TransactionFactory() {
    }

    public static BaseTransaction fromSnapshot(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        TransactionType type = snapshot.type();
        LocalDateTime timestamp = DateTimeParsers.parseTransactionTimestamp(snapshot.timestamp());
        String transactionId = snapshot.transactionId();
        double amount = snapshot.amount();
        return switch (type) {
            case DEPOSIT -> new DepositTransaction(amount, transactionId, timestamp);
            case WITHDRAWAL -> new WithdrawalTransaction(amount, transactionId, timestamp);
            case INTEREST -> new InterestTransaction(amount, transactionId, timestamp);
            case TRANSFER_OUT -> new TransferTransaction(amount,
                    Objects.requireNonNull(snapshot.targetAccount(), "targetAccount"), transactionId, timestamp);
            case TRANSFER_IN -> new TransferReceiveTransaction(amount,
                    Objects.requireNonNull(snapshot.sourceAccount(), "sourceAccount"), transactionId, timestamp);
        };
    }
}
