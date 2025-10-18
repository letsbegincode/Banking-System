package banking.ui.flow;

import banking.account.Account;
import banking.service.Bank;
import banking.ui.console.ConsoleIO;
import banking.ui.presenter.AccountPresenter;

public class AccountCreationFlow {
    private final Bank bank;
    private final ConsoleIO io;
    private final AccountPresenter accountPresenter;

    public AccountCreationFlow(Bank bank, ConsoleIO io, AccountPresenter accountPresenter) {
        this.bank = bank;
        this.io = io;
        this.accountPresenter = accountPresenter;
    }

    public void createAccount() {
        io.heading("Create New Account");

        String userName = io.prompt("Enter customer name: ");
        String accountType = selectAccountType();
        double initialDeposit = io.promptDouble("Enter initial deposit amount: ");

        try {
            Account account = bank.createAccount(userName, accountType, initialDeposit);
            io.success("Account created successfully! Account Number: " + account.getAccountNumber());
            accountPresenter.showAccountDetails(account);
        } catch (IllegalArgumentException ex) {
            io.error("Error creating account: " + ex.getMessage());
        }
    }

    private String selectAccountType() {
        io.info("1. Savings Account");
        io.info("2. Current Account");
        io.info("3. Fixed Deposit Account");

        int choice = io.promptInt("Select account type (1-3): ");
        return switch (choice) {
            case 1 -> "savings";
            case 2 -> "current";
            case 3 -> "fixed";
            default -> {
                io.warning("Invalid choice. Defaulting to Savings Account.");
                yield "savings";
            }
        };
    }
}
