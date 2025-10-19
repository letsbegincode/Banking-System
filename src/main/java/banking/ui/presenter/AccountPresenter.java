package banking.ui.presenter;

import banking.account.Account;
import banking.account.CurrentAccount;
import banking.account.FixedDepositAccount;
import banking.account.SavingsAccount;
import banking.ui.console.ConsoleIO;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AccountPresenter {
    private final ConsoleIO io;

    public AccountPresenter(ConsoleIO io) {
        this.io = io;
    }

    public void showAccountDetails(Account account) {
        io.subHeading("Account Details");
        io.printlnBold("Account Number: ", account.getAccountNumber());
        io.printlnBold("Account Holder: ", account.getUserName());
        io.printlnBold("Account Type: ", account.getAccountType());
        io.printlnBold("Balance: ", String.format("%.2f", account.getBalance()));
        io.printlnBold("Creation Date: ", account.getCreationDate());

        if (account instanceof SavingsAccount savingsAccount) {
            io.printlnBold("Minimum Balance: ", String.format("%.2f", savingsAccount.getMinimumBalance()));
        } else if (account instanceof CurrentAccount currentAccount) {
            io.printlnBold("Overdraft Limit: ", String.format("%.2f", currentAccount.getOverdraftLimit()));
        } else if (account instanceof FixedDepositAccount fixedDepositAccount) {
            io.printlnBold("Maturity Date: ", fixedDepositAccount.getFormattedMaturityDate());
        }
    }

    public void showAccountsTable(List<Account> accounts) {
        if (accounts.isEmpty()) {
            io.warning("No accounts available in the system.");
            return;
        }

        io.subHeading("Account Directory");
        io.printTableHeader("%-10s %-20s %-18s %-15s", "ACCOUNT#", "NAME", "TYPE", "BALANCE");
        accounts.stream()
            .sorted(Comparator.comparing(Account::getAccountNumber))
            .forEach(account -> io.println(String.format("%-10d %-20s %-18s %-15.2f",
                account.getAccountNumber(),
                account.getUserName(),
                account.getAccountType(),
                account.getBalance())));
    }

    public void showSearchResults(String keyword, List<Account> accounts) {
        if (accounts.isEmpty()) {
            io.warning("No accounts matched the search keyword '" + keyword + "'.");
            return;
        }

        io.subHeading("Search Results for '" + keyword + "'");
        accounts.forEach(this::showAccountDetails);
    }

    public void showAccountSummary(List<Account> accounts) {
        if (accounts.isEmpty()) {
            io.warning("No accounts found in the system.");
            return;
        }

        long savingsAccounts = accounts.stream()
            .filter(a -> a.getAccountType().toLowerCase().contains("savings"))
            .count();
        long currentAccounts = accounts.stream()
            .filter(a -> a.getAccountType().toLowerCase().contains("current"))
            .count();
        long fixedDepositAccounts = accounts.stream()
            .filter(a -> a.getAccountType().toLowerCase().contains("fixed"))
            .count();
        double totalBalance = accounts.stream().mapToDouble(Account::getBalance).sum();

        io.subHeading("Account Summary Report");
        io.printlnBold("Total Accounts: ", accounts.size());
        io.printlnBold("Savings Accounts: ", savingsAccounts);
        io.printlnBold("Current Accounts: ", currentAccounts);
        io.printlnBold("Fixed Deposit Accounts: ", fixedDepositAccounts);
        io.printlnBold("Total Balance: ", String.format("%.2f", totalBalance));
        io.printlnBold("Average Balance: ", String.format("%.2f", totalBalance / accounts.size()));
    }

    public void showHighValueAccounts(List<Account> accounts, double threshold) {
        List<Account> highValueAccounts = accounts.stream()
            .filter(a -> a.getBalance() >= threshold)
            .sorted((a1, a2) -> Double.compare(a2.getBalance(), a1.getBalance()))
            .collect(Collectors.toList());

        if (highValueAccounts.isEmpty()) {
            io.warning("No accounts found with balance >= " + threshold);
            return;
        }

        io.subHeading("High-Value Accounts (>= " + threshold + ")");
        io.printTableHeader("%-10s %-20s %-18s %-15s %-15s", "ACCOUNT#", "NAME", "TYPE", "BALANCE", "CREATED");
        highValueAccounts.forEach(account -> io.println(String.format("%-10d %-20s %-18s %-15.2f %-15s",
            account.getAccountNumber(),
            account.getUserName(),
            account.getAccountType(),
            account.getBalance(),
            account.getCreationDate())));
    }
}
