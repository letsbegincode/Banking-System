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
    public boolean execute() {
        try {
            return sourceAccount.transfer(amount, targetAccount);
        } catch (Exception e) {
            System.out.println("Transfer failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Transfer of " + amount + " from account " + sourceAccount.getAccountNumber()
            + " to account " + targetAccount.getAccountNumber();
    }
}
