package banking.ui.flow;

import banking.account.Account;
import banking.operation.OperationResult;
import banking.service.Bank;
import banking.transaction.BaseTransaction;
import banking.ui.console.ConsoleIO;
import banking.ui.presenter.AccountPresenter;
import banking.ui.presenter.TransactionPresenter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class AccountOperationsFlow {
    private final Bank bank;
    private final ConsoleIO io;
    private final AccountPresenter accountPresenter;
    private final TransactionPresenter transactionPresenter;

    public AccountOperationsFlow(Bank bank, ConsoleIO io, AccountPresenter accountPresenter,
                                 TransactionPresenter transactionPresenter) {
        this.bank = bank;
        this.io = io;
        this.accountPresenter = accountPresenter;
        this.transactionPresenter = transactionPresenter;
    }

    public void handleOperations() {
        int accountNumber = io.promptInt("Enter account number: ");
        Account account = bank.getAccount(accountNumber);

        if (account == null) {
            io.error("Account not found!");
            return;
        }

        boolean back = false;
        while (!back) {
            accountPresenter.showAccountDetails(account);
            io.heading("Account Operations");
            io.info("1. Deposit");
            io.info("2. Withdraw");
            io.info("3. Transfer");
            io.info("4. View Transactions");
            io.info("5. Account Statement");
            io.info("6. Back to Main Menu");

            int choice = io.promptInt("Select operation: ");
            switch (choice) {
                case 1 -> account = performOperation(account,
                    bank.deposit(account.getAccountNumber(), io.promptDouble("Enter deposit amount: ")));
                case 2 -> account = performOperation(account,
                    bank.withdraw(account.getAccountNumber(), io.promptDouble("Enter withdrawal amount: ")));
                case 3 -> account = performTransfer(account);
                case 4 -> transactionPresenter.showTransactions(account.getTransactions());
                case 5 -> generateStatement(account);
                case 6 -> back = true;
                default -> io.error("Invalid option!");
            }
        }
    }

    private Account performOperation(Account account, CompletableFuture<OperationResult> future) {
        try {
            OperationResult result = future.join();
            if (result.isSuccess()) {
                io.success(result.getMessage());
                Account updatedAccount = bank.getAccount(account.getAccountNumber());
                if (updatedAccount != null) {
                    accountPresenter.showAccountDetails(updatedAccount);
                    return updatedAccount;
                }
                io.warning("Account not found after operation. It may have been closed.");
                return account;
            }
            io.error(result.getMessage());
        } catch (CompletionException e) {
            io.error("Operation interrupted: " + describeError(e.getCause()));
        }
        return account;
    }

    private Account performTransfer(Account sourceAccount) {
        int targetAccountNumber = io.promptInt("Enter target account number: ");
        Account targetAccount = bank.getAccount(targetAccountNumber);

        if (targetAccount == null) {
            io.error("Target account not found!");
            return sourceAccount;
        }

        if (sourceAccount.getAccountNumber() == targetAccountNumber) {
            io.error("Cannot transfer to the same account!");
            return sourceAccount;
        }

        double amount = io.promptDouble("Enter transfer amount: ");
        CompletableFuture<OperationResult> future = bank.transfer(sourceAccount.getAccountNumber(),
            targetAccountNumber, amount);
        try {
            OperationResult result = future.join();
            if (result.isSuccess()) {
                io.success(result.getMessage());
                Account updatedSource = bank.getAccount(sourceAccount.getAccountNumber());
                Account updatedTarget = bank.getAccount(targetAccountNumber);
                if (updatedSource != null) {
                    accountPresenter.showAccountDetails(updatedSource);
                    sourceAccount = updatedSource;
                }
                if (updatedTarget != null) {
                    io.info("Updated target account details:");
                    accountPresenter.showAccountDetails(updatedTarget);
                }
            } else {
                io.error(result.getMessage());
            }
        } catch (CompletionException e) {
            io.error("Transfer interrupted: " + describeError(e.getCause()));
        }
        return sourceAccount;
    }

    private void generateStatement(Account account) {
        List<BaseTransaction> transactions = account.getTransactions();

        io.heading("Statement Period");
        io.info("1. Last Month");
        io.info("2. Last 3 Months");
        io.info("3. Last 6 Months");
        io.info("4. Custom Period");

        int choice = io.promptInt("Enter your choice: ");
        switch (choice) {
            case 1 -> transactionPresenter.showStatement(transactions, 1);
            case 2 -> transactionPresenter.showStatement(transactions, 3);
            case 3 -> transactionPresenter.showStatement(transactions, 6);
            case 4 -> {
                String startDate = io.prompt("Enter start date (yyyy-MM-dd): ");
                String endDate = io.prompt("Enter end date (yyyy-MM-dd): ");
                transactionPresenter.showStatement(transactions, startDate, endDate);
            }
            default -> io.error("Invalid choice!");
        }
    }

    private String describeError(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        String message = throwable.getMessage();
        return message != null ? message : throwable.toString();
    }
}
