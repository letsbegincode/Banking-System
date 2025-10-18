package banking.transaction;

public class DepositTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;

    public DepositTransaction(double amount) {
        super(amount);
    }

    @Override
    public String getType() {
        return "Deposit";
    }
}
