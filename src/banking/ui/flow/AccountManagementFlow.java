package banking.ui.flow;

import banking.account.Account;
import banking.service.Bank;
import banking.ui.console.ConsoleIO;
import banking.ui.presenter.AccountPresenter;

public class AccountManagementFlow {
    private final Bank bank;
    private final ConsoleIO io;
    private final AccountPresenter accountPresenter;

    public AccountManagementFlow(Bank bank, ConsoleIO io, AccountPresenter accountPresenter) {
        this.bank = bank;
        this.io = io;
        this.accountPresenter = accountPresenter;
    }

    public void manageAccounts() {
        io.heading("Account Management");
        io.info("1. Update Account Holder Name");
        io.info("2. Close Account");
        io.info("3. Process Monthly Interest");
        io.info("4. Back to Main Menu");

        int choice = io.promptInt("Enter your choice: ");
        switch (choice) {
            case 1 -> updateAccountName();
            case 2 -> closeAccount();
            case 3 -> processMonthlyInterest();
            case 4 -> io.info("Returning to main menu...");
            default -> io.error("Invalid choice!");
        }
    }

    private void updateAccountName() {
        int accountNumber = io.promptInt("Enter account number: ");
        Account account = bank.getAccount(accountNumber);

        if (account == null) {
            io.error("Account not found!");
            return;
        }

        io.info("Current account holder: " + account.getUserName());
        String newName = io.prompt("Enter new account holder name: ");
        try {
            boolean updated = bank.updateAccountHolderName(accountNumber, newName);
            if (updated) {
                io.success("Account holder name updated successfully!");
                Account updatedAccount = bank.getAccount(accountNumber);
                if (updatedAccount != null) {
                    accountPresenter.showAccountDetails(updatedAccount);
                }
            } else {
                io.error("Failed to update account holder name. Account may have been removed.");
            }
        } catch (IllegalArgumentException e) {
            io.error(e.getMessage());
        }
    }

    private void closeAccount() {
        int accountNumber = io.promptInt("Enter account number to close: ");
        Account account = bank.getAccount(accountNumber);

        if (account == null) {
            io.error("Account not found!");
            return;
        }

        accountPresenter.showAccountDetails(account);
        String confirm = io.prompt("Are you sure you want to close this account? (yes/no): ");
        if (confirm.equalsIgnoreCase("yes")) {
            boolean success = bank.closeAccount(accountNumber);
            if (success) {
                io.success("Account closed successfully!");
            } else {
                io.error("Failed to close account!");
            }
        } else {
            io.warning("Account closure canceled.");
        }
    }

    private void processMonthlyInterest() {
        String confirm = io.prompt("Process monthly interest for all eligible accounts? (yes/no): ");
        if (confirm.equalsIgnoreCase("yes")) {
            int processed = bank.addInterestToAllSavingsAccounts();
            if (processed > 0) {
                io.success("Monthly interest processed for " + processed + " accounts.");
            } else {
                io.warning("No accounts were eligible for monthly interest this cycle.");
            }
        } else {
            io.warning("Interest processing canceled.");
        }
    }
}
