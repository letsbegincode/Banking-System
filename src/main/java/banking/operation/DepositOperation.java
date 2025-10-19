package banking.operation;

import banking.account.Account;

public class DepositOperation implements AccountOperation {
    private final Account account;
    private final double amount;

    public DepositOperation(Account account, double amount) {
        this.account = account;
import banking.persistence.AccountRepository;
import banking.persistence.PersistenceException;

import java.util.List;
import java.util.Objects;

public class DepositOperation implements AccountOperation {
    private final AccountRepository repository;
    private final int accountNumber;
    private final double amount;
    private Account affectedAccount;

    public DepositOperation(AccountRepository repository, int accountNumber, double amount) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accountNumber = accountNumber;
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
        Account account = repository.findAccount(accountNumber);
        if (account == null) {
            return OperationResult.failure("Account not found: " + accountNumber);
        }
        try {
            account.deposit(amount);
            repository.saveAccount(account);
            affectedAccount = account;
            return OperationResult.success("Deposit of " + amount + " completed for account " + accountNumber);
        } catch (IllegalArgumentException e) {
            return OperationResult.failure("Deposit failed: " + e.getMessage());
        } catch (PersistenceException e) {
            return OperationResult.failure("Deposit persistence failed: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Deposit of " + amount + " to account " + account.getAccountNumber();
        return "Deposit of " + amount + " to account " + accountNumber;
    }

    @Override
    public List<Integer> getInvolvedAccountNumbers() {
        return List.of(accountNumber);
    }

    @Override
    public List<Account> getAffectedAccounts() {
        return affectedAccount == null ? List.of() : List.of(affectedAccount);
    }
}