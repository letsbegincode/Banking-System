package banking.transaction;

public class DepositTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;

    public DepositTransaction(double amount) {
        super(amount);
    }

    public DepositTransaction(double amount, java.time.LocalDateTime timestamp, String transactionId) {
        super(amount, timestamp, transactionId);
    }

    @Override
    public String getType() {
        return "Deposit";
    }
}
