package banking.transaction;

public class TransferReceiveTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;

    private final int sourceAccountNumber;

    public TransferReceiveTransaction(double amount, int sourceAccountNumber) {
        super(amount);
        this.sourceAccountNumber = sourceAccountNumber;
    }

    @Override
    public String getType() {
        return "Received from Acc#" + sourceAccountNumber;
    }
}
