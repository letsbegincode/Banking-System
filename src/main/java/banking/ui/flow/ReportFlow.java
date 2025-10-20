package banking.ui.flow;

import banking.account.Account;
import banking.report.AccountAnalyticsService;
import banking.report.AccountStatement;
import banking.report.AnalyticsReport;
import banking.report.AnalyticsReportRequest;
import banking.report.StatementGenerator;
import banking.service.Bank;
import banking.transaction.BaseTransaction;
import banking.ui.console.ConsoleIO;
import banking.ui.presenter.AccountPresenter;
import banking.ui.presenter.AnalyticsPresenter;
import banking.ui.presenter.StatementPresenter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles the collection of reporting and analytics workflows surfaced via the console.
 */
public class ReportFlow {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Bank bank;
    private final ConsoleIO io;
    private final AccountPresenter accountPresenter;
    private final StatementGenerator statementGenerator;
    private final StatementPresenter statementPresenter;
    private final AccountAnalyticsService analyticsService;
    private final AnalyticsPresenter analyticsPresenter;

    public ReportFlow(Bank bank,
                      ConsoleIO io,
                      AccountPresenter accountPresenter,
                      StatementGenerator statementGenerator,
                      StatementPresenter statementPresenter,
                      AccountAnalyticsService analyticsService,
                      AnalyticsPresenter analyticsPresenter) {
        this.bank = bank;
        this.io = io;
        this.accountPresenter = accountPresenter;
        this.statementGenerator = statementGenerator;
        this.statementPresenter = statementPresenter;
        this.analyticsService = analyticsService;
        this.analyticsPresenter = analyticsPresenter;
    }

    public void showReportsMenu() {
        io.heading("Reports");
        io.info("1. Account Summary Report");
        io.info("2. High-Value Accounts Report");
        io.info("3. Transaction Volume Report");
        io.info("4. Generate Account Statement");
        io.info("5. Portfolio Analytics Summary");
        io.info("6. Export Portfolio Analytics (CSV)");
        io.info("7. Export Portfolio Analytics (JSON)");
        io.info("8. Back to Main Menu");

        int choice = io.promptInt("Select a report to generate: ");
        switch (choice) {
            case 1 -> accountPresenter.showAccountSummary(bank.getAllAccounts());
            case 2 -> generateHighValueReport();
            case 3 -> generateTransactionVolumeReport();
            case 4 -> generateAccountStatement();
            case 5 -> generatePortfolioAnalyticsSummary();
            case 6 -> exportPortfolioAnalyticsCsv();
            case 7 -> exportPortfolioAnalyticsJson();
            case 8 -> io.info("Returning to main menu...");
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
        Map<String, Integer> counts = new HashMap<>();
        allTransactions.forEach(transaction -> counts.merge(transaction.getType(), 1, Integer::sum));
        io.subHeading("Transaction Volume Report");
        counts.entrySet().stream()
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

    private void generatePortfolioAnalyticsSummary() {
        AnalyticsReport report = runAnalyticsWorkflow();
        if (report != null) {
            analyticsPresenter.showSummary(report);
        }
    }

    private void exportPortfolioAnalyticsCsv() {
        AnalyticsReport report = runAnalyticsWorkflow();
        if (report != null) {
            io.subHeading("CSV Export");
            io.println(analyticsPresenter.toCsv(report));
        }
    }

    private void exportPortfolioAnalyticsJson() {
        AnalyticsReport report = runAnalyticsWorkflow();
        if (report != null) {
            io.subHeading("JSON Export");
            io.println(analyticsPresenter.toJson(report));
        }
    }

    private AnalyticsReport runAnalyticsWorkflow() {
        try {
            AnalyticsReportRequest request = promptAnalyticsRequest();
            io.info("Queuing analytics report... this may take a few moments.");
            return bank.generateAnalyticsReport(request, analyticsService).join();
        } catch (Exception ex) {
            io.error("Failed to generate analytics report: " + ex.getMessage());
            return null;
        }
    }

    private AnalyticsReportRequest promptAnalyticsRequest() {
        LocalDate defaultStart = LocalDate.now().minusDays(30);
        LocalDate defaultEnd = LocalDate.now();
        io.info("Press ENTER to accept defaults.");
        LocalDate startDate = promptOptionalDate("Enter analytics start date (yyyy-MM-dd)", defaultStart);
        LocalDate endDate = promptOptionalDate("Enter analytics end date (yyyy-MM-dd)", defaultEnd);

        String thresholdInput = io.prompt("Enter high-value threshold (default 5000): ");
        double threshold;
        if (thresholdInput == null || thresholdInput.isBlank()) {
            threshold = 5000.0;
        } else {
            try {
                threshold = Double.parseDouble(thresholdInput.trim());
            } catch (NumberFormatException e) {
                io.warning("Invalid threshold provided. Using default (5000).");
                threshold = 5000.0;
            }
        }

        String windowInput = io.prompt("Enter rolling window (days, default 7): ");
        int window;
        if (windowInput == null || windowInput.isBlank()) {
            window = 7;
        } else {
            try {
                window = Integer.parseInt(windowInput.trim());
                if (window <= 0) {
                    io.warning("Rolling window must be positive. Using default (7).");
                    window = 7;
                }
            } catch (NumberFormatException e) {
                io.warning("Invalid window provided. Using default (7).");
                window = 7;
            }
        }

        return AnalyticsReportRequest.builder()
                .withStartDate(startDate)
                .withEndDate(endDate)
                .withLargeTransactionThreshold(threshold)
                .withRollingWindowDays(window)
                .build();
    }

    private LocalDate promptForDate(String prompt) {
        while (true) {
            try {
                return LocalDate.parse(io.prompt(prompt), DATE_FORMAT);
            } catch (DateTimeParseException ex) {
                io.error("Invalid date format. Please use yyyy-MM-dd.");
            }
        }
    }

    private LocalDate promptOptionalDate(String prompt, LocalDate defaultValue) {
        while (true) {
            String input = io.prompt(prompt + " [" + defaultValue + "]: ");
            if (input == null || input.isBlank()) {
                return defaultValue;
            }
            try {
                return LocalDate.parse(input.trim(), DATE_FORMAT);
            } catch (DateTimeParseException ex) {
                io.error("Invalid date format. Please use yyyy-MM-dd.");
            }
        }
    }
}
