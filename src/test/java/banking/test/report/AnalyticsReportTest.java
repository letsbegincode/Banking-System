package banking.test.report;

import banking.account.Account;
import banking.report.analytics.AnalyticsRange;
import banking.report.analytics.AnomalyReport;
import banking.report.analytics.AnomalyDetectionService;
import banking.report.analytics.RangeAnalyticsService;
import banking.report.analytics.RangeSummary;
import banking.report.analytics.TrendAnalyticsService;
import banking.report.analytics.TrendReport;
import banking.report.format.ReportFormatter;
import banking.service.Bank;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

public final class AnalyticsReportTest {
    private int passed;
    private int failed;

    public static void main(String[] args) {
        AnalyticsReportTest test = new AnalyticsReportTest();
        test.run();
        test.report();
        if (test.failed > 0) {
            System.exit(1);
        }
    }

    private void run() {
        execute("trend aggregation calculates daily totals", this::shouldAggregateTrends);
        execute("anomaly detection isolates outliers and formats", this::shouldDetectAnomalies);
        execute("range summary surfaces KPI metrics", this::shouldSummarizeRange);
    }

    private void execute(String name, TestCase testCase) {
        try {
            testCase.run();
            passed++;
            System.out.println("[PASS] " + name);
        } catch (AssertionError error) {
            failed++;
            System.err.println("[FAIL] " + name + " -> " + error.getMessage());
        } catch (Exception exception) {
            failed++;
            System.err.println("[ERROR] " + name + " -> " + exception.getMessage());
        }
    }

    private void report() {
        System.out.println();
        System.out.println("Tests run: " + (passed + failed));
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }

    private void shouldAggregateTrends() {
        Bank bank = new Bank();
        try {
            Account account = bank.createAccount("Trend Tester", "current", 0);
            bank.deposit(account.getAccountNumber(), 500.0).join();
            bank.withdraw(account.getAccountNumber(), 125.0).join();

            AnalyticsRange range = new AnalyticsRange(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
            TrendAnalyticsService trendService = new TrendAnalyticsService();
            TrendReport report = trendService.generate(bank.getAllAccounts(), range);

            assertEquals(500.0, report.getTotalCredits(), 0.001, "Total credits should include deposit");
            assertEquals(125.0, report.getTotalDebits(), 0.001, "Total debits should include withdrawal");
            assertEquals(3, report.getPoints().size(), 0.0, "Range should include three days of points");

            ReportFormatter formatter = new ReportFormatter();
            String json = formatter.toJson(report);
            String csv = formatter.toCsv(report);
            assertTrue(json.contains("\"totals\""), "JSON should contain totals section");
            assertTrue(csv.startsWith("date,totalCredits"), "CSV should start with header");
        } finally {
            bank.shutdown();
        }
    }

    private void shouldDetectAnomalies() {
        Bank bank = new Bank();
        try {
            Account account = bank.createAccount("Anomaly Analyzer", "current", 0);
            bank.deposit(account.getAccountNumber(), 50.0).join();
            bank.deposit(account.getAccountNumber(), 60.0).join();
            CompletableFuture<?> spike = bank.deposit(account.getAccountNumber(), 1500.0);
            spike.join();

            AnalyticsRange range = new AnalyticsRange(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
            AnomalyDetectionService anomalyDetectionService = new AnomalyDetectionService();
            AnomalyReport report = anomalyDetectionService.detect(bank.getAllAccounts(), range, 500.0, 2.0);

            assertTrue(report.getAnomalies().size() >= 1, "Spike deposit should be flagged as anomaly");
            assertEquals(1500.0, report.getAnomalies().get(0).getAmount(), 0.001, "Anomaly amount should match spike");

            ReportFormatter formatter = new ReportFormatter();
            String json = formatter.toJson(report);
            String csv = formatter.toCsv(report);
            assertTrue(json.contains("\"anomalies\""), "JSON should include anomalies array");
            assertTrue(csv.contains("accountNumber"), "CSV should contain column headers");
        } finally {
            bank.shutdown();
        }
    }

    private void shouldSummarizeRange() {
        Bank bank = new Bank();
        try {
            Account first = bank.createAccount("Summary Owner", "current", 0);
            bank.deposit(first.getAccountNumber(), 200.0).join();
            Account second = bank.createAccount("Summary Saver", "savings", 0);
            bank.deposit(second.getAccountNumber(), 1200.0).join();
            bank.withdraw(second.getAccountNumber(), 100.0).join();

            AnalyticsRange range = new AnalyticsRange(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
            RangeAnalyticsService rangeAnalyticsService = new RangeAnalyticsService();
            RangeSummary summary = rangeAnalyticsService.summarize(bank.getAllAccounts(), range);

            assertEquals(2, summary.getActiveAccounts(), 0.0, "Both accounts should be active");
            assertEquals(2, summary.getAccountsOpened(), 0.0, "Two accounts opened within range");
            assertEquals(3, summary.getTotalTransactions(), 0.0, "Three transactions should be counted");
            assertEquals(1400.0, summary.getTotalCredits(), 0.001, "Credits should total deposits");
            assertEquals(100.0, summary.getTotalDebits(), 0.001, "Debits should total withdrawals");

            ReportFormatter formatter = new ReportFormatter();
            String json = formatter.toJson(summary);
            String csv = formatter.toCsv(summary);
            assertTrue(json.contains("\"range\""), "JSON should include range metadata");
            assertTrue(csv.contains("metric,value"), "CSV should expose key metrics");
        } finally {
            bank.shutdown();
        }
    }

    private void assertEquals(double expected, double actual, double delta, String message) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface TestCase {
        void run();
    }
}
