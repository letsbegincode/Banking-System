package banking.ui.flow;

import banking.service.Bank;
import banking.transaction.BaseTransaction;
import banking.ui.console.ConsoleIO;
import banking.ui.presenter.AccountPresenter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportFlow {
    private final Bank bank;
    private final ConsoleIO io;
    private final AccountPresenter accountPresenter;

    public ReportFlow(Bank bank, ConsoleIO io, AccountPresenter accountPresenter) {
        this.bank = bank;
        this.io = io;
        this.accountPresenter = accountPresenter;
    }

    public void showReportsMenu() {
        io.heading("Reports");
        io.info("1. Account Summary Report");
        io.info("2. High-Value Accounts Report");
        io.info("3. Transaction Volume Report");
        io.info("4. Back to Main Menu");

        int choice = io.promptInt("Select a report to generate: ");
        switch (choice) {
            case 1 -> accountPresenter.showAccountSummary(bank.getAllAccounts());
            case 2 -> generateHighValueReport();
            case 3 -> generateTransactionVolumeReport();
            case 4 -> io.info("Returning to main menu...");
            default -> io.error("Invalid choice!");
        }
    }

    private void generateHighValueReport() {
        double threshold = io.promptDouble("Enter balance threshold for high-value accounts: ");
        accountPresenter.showHighValueAccounts(bank.getAllAccounts(), threshold);
    }

    private void generateTransactionVolumeReport() {
        List<BaseTransaction> allTransactions = bank.getAllAccounts().stream()
            .flatMap(account -> account.getTransactions().stream())
            .collect(Collectors.toList());

        if (allTransactions.isEmpty()) {
            io.warning("No transactions found in the system.");
            return;
        }

        Map<String, Integer> transactionTypeCount = new HashMap<>();
        allTransactions.forEach(transaction -> transactionTypeCount.merge(transaction.getType(), 1, Integer::sum));

        io.subHeading("Transaction Volume Report");
        transactionTypeCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> io.println(entry.getKey() + ": " + entry.getValue()));
    }
}
