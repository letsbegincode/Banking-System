package banking.operation;

import banking.account.Account;

public class DepositOperation implements AccountOperation {
    private final Account account;
    private final double amount;

    public DepositOperation(Account account, double amount) {
        this.account = account;
        this.amount = amount;
    }

    @Override
    public boolean execute() {
        try {
            account.deposit(amount);
            return true;
        } catch (Exception e) {
            System.out.println("Deposit failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Deposit of " + amount + " to account " + account.getAccountNumber();
    }
}
