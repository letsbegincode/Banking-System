package banking.account;

import banking.transaction.BaseTransaction;

import java.util.List;

public class CurrentAccount extends Account {
    private static final long serialVersionUID = 1L;

    private double overdraftLimit = 10000;

    public CurrentAccount(String userName, int accountNumber) {
        super(userName, accountNumber);
    }

    CurrentAccount(String userName, int accountNumber, String creationDate, double balance, double overdraftLimit,
            List<BaseTransaction> transactions) {
        super(userName, accountNumber, creationDate, balance, transactions);
        this.overdraftLimit = overdraftLimit;
    }

    @Override
    protected boolean canWithdraw(double amount) {
        return getBalance() - amount >= -overdraftLimit;
    }

    @Override
    public void addInterest() {
        // Current accounts typically do not earn interest.
    }

    @Override
    public String getAccountType() {
        return "Current";
    }

    public double getOverdraftLimit() {
        return overdraftLimit;
    }

    public void setOverdraftLimit(double overdraftLimit) {
        this.overdraftLimit = overdraftLimit;
    }
}
