package banking.operation;

import banking.account.Account;

public class TransferOperation implements AccountOperation {
    private final Account sourceAccount;
    private final Account targetAccount;
    private final double amount;

    public TransferOperation(Account sourceAccount, Account targetAccount, double amount) {
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.amount = amount;
    }

    @Override
<<<<<<< HEAD
<<<<<<< HEAD
    public boolean execute() {
        try {
            return sourceAccount.transfer(amount, targetAccount);
        } catch (Exception e) {
            System.out.println("Transfer failed: " + e.getMessage());
            return false;
=======
=======
>>>>>>> origin/pr/11
    public OperationResult execute() {
        Account firstLock = sourceAccount.getAccountNumber() < targetAccount.getAccountNumber()
            ? sourceAccount : targetAccount;
        Account secondLock = firstLock == sourceAccount ? targetAccount : sourceAccount;

        synchronized (firstLock) {
            synchronized (secondLock) {
                try {
                    boolean transferred = sourceAccount.transfer(amount, targetAccount);
                    if (transferred) {
                        return OperationResult.success("Transfer of " + amount + " completed from account "
                            + sourceAccount.getAccountNumber() + " to account " + targetAccount.getAccountNumber());
                    }
                    return OperationResult.failure("Transfer failed due to insufficient balance or account rules.");
                } catch (IllegalArgumentException e) {
                    return OperationResult.failure("Transfer failed: " + e.getMessage());
                }
            }
<<<<<<< HEAD
>>>>>>> origin/pr/10
=======
>>>>>>> origin/pr/11
        }
    }

    @Override
    public String getDescription() {
        return "Transfer of " + amount + " from account " + sourceAccount.getAccountNumber()
            + " to account " + targetAccount.getAccountNumber();
    }
}
