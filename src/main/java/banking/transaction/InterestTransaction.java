package banking.transaction;

public class InterestTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;

    public InterestTransaction(double amount) {
        super(amount);
    }

    @Override
    public String getType() {
        return "Interest Added";
    }
}
