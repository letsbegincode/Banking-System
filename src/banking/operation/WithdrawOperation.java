package banking.operation;

import banking.account.Account;

public class WithdrawOperation implements AccountOperation {
    private final Account account;
    private final double amount;

    public WithdrawOperation(Account account, double amount) {
        this.account = account;
        this.amount = amount;
    }

    @Override
    public OperationResult execute() {
        try {
            boolean withdrawn = account.withdraw(amount);
            if (withdrawn) {
                return OperationResult.success("Withdrawal of " + amount + " completed for account "
                    + account.getAccountNumber());
            }
            return OperationResult.failure("Withdrawal failed due to insufficient balance or account rules.");
        } catch (IllegalArgumentException e) {
            return OperationResult.failure("Withdrawal failed: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Withdrawal of " + amount + " from account " + account.getAccountNumber();
    }
}
