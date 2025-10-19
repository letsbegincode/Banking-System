package banking.transaction;

public class WithdrawalTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;

    public WithdrawalTransaction(double amount) {
        super(amount);
    }

    @Override
    public String getType() {
        return "Withdrawal";
    }
}
