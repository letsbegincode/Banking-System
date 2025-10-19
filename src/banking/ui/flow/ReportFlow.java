package banking.ui.flow;

import banking.account.Account;
import banking.report.AccountStatement;
import banking.report.StatementGenerator;
import banking.service.Bank;
import banking.transaction.BaseTransaction;
import banking.ui.console.ConsoleIO;
import banking.ui.presenter.AccountPresenter;
import banking.ui.presenter.StatementPresenter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportFlow {
    private final Bank bank;
    private final ConsoleIO io;
    private final AccountPresenter accountPresenter;
    private final StatementGenerator statementGenerator;
    private final StatementPresenter statementPresenter;

    public ReportFlow(Bank bank,
            ConsoleIO io,
            AccountPresenter accountPresenter,
            StatementGenerator statementGenerator,
            StatementPresenter statementPresenter) {
        this.bank = bank;
        this.io = io;
        this.accountPresenter = accountPresenter;
        this.statementGenerator = statementGenerator;
        this.statementPresenter = statementPresenter;
    }

    public void showReportsMenu() {
        io.heading("Reports");
        io.info("1. Account Summary Report");
        io.info("2. High-Value Accounts Report");
        io.info("3. Transaction Volume Report");
        io.info("4. Generate Account Statement");
        io.info("5. Back to Main Menu");

        int choice = io.promptInt("Select a report to generate: ");
        switch (choice) {
            case 1 -> accountPresenter.showAccountSummary(bank.getAllAccounts());
            case 2 -> generateHighValueReport();
            case 3 -> generateTransactionVolumeReport();
            case 4 -> generateAccountStatement();
            case 5 -> io.info("Returning to main menu...");
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

    private void generateAccountStatement() {
        int accountNumber = io.promptInt("Enter account number: ");
        Account account = bank.getAccount(accountNumber);
        if (account == null) {
            io.error("Account not found.");
            return;
        }

        LocalDate startDate = promptForDate("Enter start date (yyyy-MM-dd): ");
        LocalDate endDate = promptForDate("Enter end date (yyyy-MM-dd): ");
        if (endDate.isBefore(startDate)) {
            io.error("End date must not be before start date.");
            return;
        }

        AccountStatement statement = statementGenerator.generate(account, startDate, endDate);
        statementPresenter.show(statement);
    }

    private LocalDate promptForDate(String prompt) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        while (true) {
            try {
                return LocalDate.parse(io.prompt(prompt), formatter);
            } catch (DateTimeParseException ex) {
                io.error("Invalid date format. Please use yyyy-MM-dd.");
            }
        }
    }
}