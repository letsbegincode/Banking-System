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
<<<<<<< HEAD
<<<<<<< HEAD
    public boolean execute() {
        try {
            account.deposit(amount);
            return true;
        } catch (Exception e) {
            System.out.println("Deposit failed: " + e.getMessage());
            return false;
=======
=======
>>>>>>> origin/pr/11
    public OperationResult execute() {
        try {
            account.deposit(amount);
            return OperationResult.success("Deposit of " + amount + " completed for account "
                + account.getAccountNumber());
        } catch (IllegalArgumentException e) {
            return OperationResult.failure("Deposit failed: " + e.getMessage());
<<<<<<< HEAD
>>>>>>> origin/pr/10
=======
>>>>>>> origin/pr/11
        }
    }

    @Override
    public String getDescription() {
        return "Deposit of " + amount + " to account " + account.getAccountNumber();
    }
}
