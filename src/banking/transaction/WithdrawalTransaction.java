package banking.transaction;

public class WithdrawalTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;

    public WithdrawalTransaction(double amount) {
        super(amount);
    }

    public WithdrawalTransaction(double amount, java.time.LocalDateTime timestamp, String transactionId) {
        super(amount, timestamp, transactionId);
    }

    @Override
    public String getType() {
        return "Withdrawal";
    }
}
