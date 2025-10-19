package banking.operation;

import banking.account.Account;

import java.util.Collection;
import java.util.List;

public class DepositOperation implements AccountOperation {
    private final Account account;
    private final double amount;

    public DepositOperation(Account account, double amount) {
        this.account = account;
        this.amount = amount;
    }

    @Override
    public OperationResult execute() {
        try {
            account.deposit(amount);
            return OperationResult.success("Deposit of " + amount + " completed for account "
                    + account.getAccountNumber());
        } catch (IllegalArgumentException e) {
            return OperationResult.failure("Deposit failed: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Deposit of " + amount + " to account " + account.getAccountNumber();
    }

    @Override
    public Collection<Account> affectedAccounts() {
        return List.of(account);
    }
}