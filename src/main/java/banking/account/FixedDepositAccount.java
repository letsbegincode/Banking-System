package banking.account;

import banking.transaction.DepositTransaction;
import banking.transaction.InterestTransaction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FixedDepositAccount extends Account {
    private static final long serialVersionUID = 1L;
    private static final double INTEREST_RATE = 0.08;

    private final LocalDateTime maturityDate;
    private final int termMonths;

    public FixedDepositAccount(String userName, int accountNumber, double initialDeposit, int termMonths) {
        super(userName, accountNumber);
        if (initialDeposit < 5000) {
            throw new IllegalArgumentException("Fixed deposit requires minimum initial deposit of 5000");
        }
        setBalance(initialDeposit);
        this.termMonths = termMonths;
        this.maturityDate = LocalDateTime.now().plusMonths(termMonths);
        recordTransaction(new DepositTransaction(initialDeposit));
    }

    @Override
    protected boolean canWithdraw(double amount) {
        return LocalDateTime.now().isAfter(maturityDate) && amount <= getBalance();
    }

    @Override
    public synchronized void addInterest() {
        double interest = getBalance() * INTEREST_RATE / 12;
        setBalance(getBalance() + interest);
        recordTransaction(new InterestTransaction(interest));
    }

    @Override
    public String getAccountType() {
        return "Fixed Deposit (" + termMonths + " months)";
    }

    public LocalDateTime getMaturityDate() {
        return maturityDate;
    }

    public String getFormattedMaturityDate() {
        return maturityDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
