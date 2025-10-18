package banking.account;

import banking.transaction.InterestTransaction;

public class SavingsAccount extends Account {
    private static final long serialVersionUID = 1L;
    private static final double INTEREST_RATE = 0.04;

    private double minimumBalance = 1000;

    public SavingsAccount(String userName, int accountNumber) {
        super(userName, accountNumber);
    }

    @Override
    protected boolean canWithdraw(double amount) {
        return getBalance() - amount >= minimumBalance;
    }

    @Override
    public synchronized void addInterest() {
        double interest = getBalance() * INTEREST_RATE / 12;
        if (interest > 0) {
            setBalance(getBalance() + interest);
            recordTransaction(new InterestTransaction(interest));
        }
    }

    @Override
    public String getAccountType() {
        return "Savings";
    }

    public double getMinimumBalance() {
        return minimumBalance;
    }

    public void setMinimumBalance(double minimumBalance) {
        this.minimumBalance = minimumBalance;
    }
}
