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
    public boolean execute() {
        try {
            return account.withdraw(amount);
        } catch (Exception e) {
            System.out.println("Withdrawal failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Withdrawal of " + amount + " from account " + account.getAccountNumber();
    }
}
