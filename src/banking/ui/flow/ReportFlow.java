package banking.ui.flow;

import banking.account.Account;
import banking.report.AccountStatement;
import banking.report.StatementGenerator;
import banking.report.analytics.AnalyticsRange;
import banking.report.analytics.AnalyticsReportService;
import banking.report.analytics.AnomalyReport;
import banking.report.analytics.RangeSummary;
import banking.report.analytics.TrendReport;
import banking.report.format.ReportFormatter;
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
    private final AnalyticsReportService analyticsReportService;
    private final ReportFormatter reportFormatter;

    public ReportFlow(Bank bank,
            ConsoleIO io,
            AccountPresenter accountPresenter,
            StatementGenerator statementGenerator,
            StatementPresenter statementPresenter,
            AnalyticsReportService analyticsReportService,
            ReportFormatter reportFormatter) {
        this.bank = bank;
        this.io = io;
        this.accountPresenter = accountPresenter;
        this.statementGenerator = statementGenerator;
        this.statementPresenter = statementPresenter;
        this.analyticsReportService = analyticsReportService;
        this.reportFormatter = reportFormatter;
    }

    public void showReportsMenu() {
        io.heading("Reports");
        io.info("1. Account Summary Report");
        io.info("2. High-Value Accounts Report");
        io.info("3. Transaction Volume Report");
        io.info("4. Generate Account Statement");
        io.info("5. Transaction Trend Report");
        io.info("6. Anomaly Scan");
        io.info("7. Range KPI Summary");
        io.info("8. Back to Main Menu");

        int choice = io.promptInt("Select a report to generate: ");
        switch (choice) {
            case 1 -> accountPresenter.showAccountSummary(bank.getAllAccounts());
            case 2 -> generateHighValueReport();
            case 3 -> generateTransactionVolumeReport();
            case 4 -> generateAccountStatement();
            case 5 -> generateTrendReport();
            case 6 -> generateAnomalyReport();
            case 7 -> generateRangeSummary();
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

    private void generateTrendReport() {
        AnalyticsRange range = promptAnalyticsRange();
        String format = promptFormat();
        io.info("Queuing transaction trend report...");
        try {
            TrendReport report = analyticsReportService.queueTrendReport(range).join();
            printFormattedReport(format, reportFormatter.toJson(report), reportFormatter.toCsv(report));
        } catch (RuntimeException ex) {
            io.error("Failed to generate trend report: " + ex.getMessage());
        }
    }

    private void generateAnomalyReport() {
        AnalyticsRange range = promptAnalyticsRange();
        double threshold = io.promptDouble("Enter absolute amount threshold (0 for none): ");
        double deviationMultiplier = io.promptDouble("Enter deviation multiplier (e.g., 2 for 2σ, 0 for none): ");
        String format = promptFormat();
        io.info("Queuing anomaly scan...");
        try {
            AnomalyReport report = analyticsReportService.queueAnomalyReport(range, threshold, deviationMultiplier).join();
            if (report.getAnomalies().isEmpty()) {
                io.success("No anomalies detected for the requested range.");
            }
            printFormattedReport(format, reportFormatter.toJson(report), reportFormatter.toCsv(report));
        } catch (RuntimeException ex) {
            io.error("Failed to generate anomaly report: " + ex.getMessage());
        }
    }

    private void generateRangeSummary() {
        AnalyticsRange range = promptAnalyticsRange();
        String format = promptFormat();
        io.info("Queuing range KPI summary...");
        try {
            RangeSummary summary = analyticsReportService.queueRangeSummary(range).join();
            printFormattedReport(format, reportFormatter.toJson(summary), reportFormatter.toCsv(summary));
        } catch (RuntimeException ex) {
            io.error("Failed to generate range summary: " + ex.getMessage());
        }
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

    private AnalyticsRange promptAnalyticsRange() {
        LocalDate startDate = promptForDate("Enter start date (yyyy-MM-dd): ");
        LocalDate endDate = promptForDate("Enter end date (yyyy-MM-dd): ");
        if (endDate.isBefore(startDate)) {
            io.error("End date must not be before start date.");
            return promptAnalyticsRange();
        }
        return new AnalyticsRange(startDate, endDate);
    }

    private String promptFormat() {
        while (true) {
            String format = io.prompt("Select output format (json/csv): ").trim().toLowerCase();
            if ("json".equals(format) || "csv".equals(format)) {
                return format;
            }
            io.error("Unsupported format. Please enter 'json' or 'csv'.");
        }
    }

    private void printFormattedReport(String format, String json, String csv) {
        if ("csv".equals(format)) {
            io.subHeading("CSV Output");
            io.println(csv);
        } else {
            io.subHeading("JSON Output");
            io.println(json);
        }
    }
}