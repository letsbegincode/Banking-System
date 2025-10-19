package banking.test;

import banking.account.Account;
import banking.api.BankHttpServer;
import banking.report.AccountAnalyticsService;
import banking.security.AuthenticationService;
import banking.security.AuthenticationToken;
import banking.security.AuthorizationService;
import banking.security.CredentialStore;
import banking.security.OperatorCredential;
import banking.security.PasswordHasher;
import banking.security.Role;
import banking.security.TokenService;
import banking.report.AccountStatement;
import banking.report.AnalyticsReport;
import banking.report.AnalyticsReportRequest;
import banking.report.StatementGenerator;
<<<<<<< HEAD:src/test/java/banking/test/BankTestRunner.java
import banking.service.Bank;<<<<<<<<HEAD:src/test/java/banking/test/BankTestRunner.java
import banking.ui.presenter.AnalyticsPresenter;========
import banking.persistence.memory.InMemoryAccountRepository;>>>>>>>>origin/pr/20:src/main/java/banking/test/BankTestRunner.java
=======
import banking.service.Bank;
import banking.telemetry.TelemetryCollector;
>>>>>>> origin/pr/21:src/banking/test/BankTestRunner.java

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;<<<<<<<HEAD:src/banking/test/BankTestRunner.java
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;=======
import java.net.URL;>>>>>>>origin/pr/19:src/test/java/banking/test/BankTestRunner.java
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class BankTestRunner {
    private int passed;
    private int failed;

    public static void main(String[] args) {
        BankTestRunner runner = new BankTestRunner();
        runner.run();
        runner.report();
        if (runner.failed > 0) {
            System.exit(1);
        }
    }

    private void run() {
        execute("deposit increases balance", this::shouldDepositFunds);
        execute("withdraw reduces balance when allowed", this::shouldWithdrawFunds);
        execute("transfer moves funds between accounts", this::shouldTransferFunds);
        execute("interest applied to savings accounts", this::shouldApplyInterest);
        execute("statement summarizes period balances", this::shouldGenerateStatement);
        execute("http gateway exposes core workflows", this::shouldServeHttpApi);
<<<<<<< HEAD:src/test/java/banking/test/BankTestRunner.java
        execute("analytics aggregates balances and trends", this::shouldGenerateAnalyticsReport);
        execute("analytics exports format csv and json", this::shouldFormatAnalyticsExports);
=======
        execute("gateway enforces validation and rate limits", this::shouldHardenGateway);
        execute("telemetry collector aggregates events", this::shouldEmitTelemetry);
>>>>>>> origin/pr/21:src/banking/test/BankTestRunner.java
    }

    private void execute(String name, TestCase testCase) {
        try {
            TelemetryCollector.getInstance().reset();
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

    private void shouldDepositFunds() {
        Bank bank = new Bank(new InMemoryAccountRepository());
        try {
            Account account = bank.createAccount("Alice", "savings", 0);
            CompletableFuture<?> future = bank.deposit(account.getAccountNumber(), 200.0);
            future.join();

            Account updated = bank.getAccount(account.getAccountNumber());
            assertNotNull(updated, "Account should exist after deposit");
            assertEquals(200.0, updated.getBalance(), 0.0001, "Balance should reflect deposit");
        } finally {
            bank.shutdown();
        }
    }

    private void shouldWithdrawFunds() {
        Bank bank = new Bank(new InMemoryAccountRepository());
        try {
            Account account = bank.createAccount("Bob", "savings", 0);
            bank.deposit(account.getAccountNumber(), 2000.0).join();
            bank.withdraw(account.getAccountNumber(), 500.0).join();

            Account updated = bank.getAccount(account.getAccountNumber());
            assertNotNull(updated, "Account should exist after withdrawal");
            assertEquals(1500.0, updated.getBalance(), 0.0001, "Balance should reflect withdrawal");
        } finally {
            bank.shutdown();
        }
    }

    private void shouldTransferFunds() {
        Bank bank = new Bank(new InMemoryAccountRepository());
        try {
            Account source = bank.createAccount("Charlie", "current", 0);
            Account target = bank.createAccount("Dana", "savings", 0);
            bank.deposit(source.getAccountNumber(), 1000.0).join();

            bank.transfer(source.getAccountNumber(), target.getAccountNumber(), 400.0).join();

            Account updatedSource = bank.getAccount(source.getAccountNumber());
            Account updatedTarget = bank.getAccount(target.getAccountNumber());
            assertNotNull(updatedSource, "Source account should remain available");
            assertNotNull(updatedTarget, "Target account should remain available");
            assertEquals(600.0, updatedSource.getBalance(), 0.0001, "Source balance should decrease");
            assertEquals(400.0, updatedTarget.getBalance(), 0.0001, "Target balance should increase");
        } finally {
            bank.shutdown();
        }
    }

    private void shouldApplyInterest() {
        Bank bank = new Bank(new InMemoryAccountRepository());
        try {
            Account savings = bank.createAccount("Eve", "savings", 0);
            bank.deposit(savings.getAccountNumber(), 1200.0).join();

            int processed = bank.addInterestToAllSavingsAccounts();
            Account updated = bank.getAccount(savings.getAccountNumber());
            assertEquals(1, processed, 0.0, "Exactly one savings account should be processed");
            assertNotNull(updated, "Account should remain available");
            assertTrue(updated.getBalance() > 1200.0, "Balance should increase after interest");
        } finally {
            bank.shutdown();
        }
    }

    private void shouldGenerateStatement() {
        Bank bank = new Bank(new InMemoryAccountRepository());
        try {
            Account account = bank.createAccount("Frank", "current", 0);
            bank.deposit(account.getAccountNumber(), 500.0).join();
            bank.withdraw(account.getAccountNumber(), 200.0).join();

            StatementGenerator generator = new StatementGenerator();
            LocalDate start = LocalDate.now().minusDays(1);
            LocalDate end = LocalDate.now().plusDays(1);
            Account refreshed = bank.getAccount(account.getAccountNumber());
            assertNotNull(refreshed, "Account should be retrievable before generating statement");
            assertEquals(2, refreshed.getTransactions().size(),
                    "Transaction history should contain deposit and withdrawal");
            AccountStatement statement = generator.generate(refreshed, start, end);

            assertEquals(0.0, statement.getOpeningBalance(), 0.0001, "Opening balance should start at zero");
            assertEquals(300.0, statement.getClosingBalance(), 0.0001, "Closing balance should reflect net activity");
            assertEquals(500.0, statement.getTotalCredits(), 0.0001, "Credits should capture deposits");
            assertEquals(200.0, statement.getTotalDebits(), 0.0001, "Debits should capture withdrawals");
            assertEquals(2, statement.getTransactions().size(), "Statement should include period transactions");
        } finally {
            bank.shutdown();
        }
    }

    private void shouldServeHttpApi() {
<<<<<<<< HEAD:src/test/java/banking/test/BankTestRunner.java
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("bank-http-test");
        } catch (IOException e) {
            throw new AssertionError("Unable to create temp directory", e);
        }
        System.setProperty("banking.storage.mode", "snapshot");
        System.setProperty("banking.data.path", tempDir.resolve("banking_state.properties").toString());

        Bank bank = new Bank();
        TokenService tokenService = new TokenService();
        AuthorizationService authorizationService = new AuthorizationService();
        BankHttpServer server = new BankHttpServer(bank, 0, tokenService, authorizationService);
        PasswordHasher hasher = new PasswordHasher();
        CredentialStore store = new CredentialStore();
        store.store(new OperatorCredential("tester", hasher.hash("password"), Set.of(Role.ADMIN)));
        AuthenticationService authenticationService = new AuthenticationService(
            store, hasher, tokenService, Duration.ofHours(1));
        AuthenticationToken token = authenticationService.login("tester", "password");
========
        Bank bank = new Bank(new InMemoryAccountRepository());
        BankHttpServer server = new BankHttpServer(bank, 0);
>>>>>>>> origin/pr/20:src/main/java/banking/test/BankTestRunner.java
        try {
            server.start();
            int port = server.getPort();
            String baseUrl = "http://localhost:" + port;

            HttpResponse createSavings = sendRequest("POST", baseUrl + "/accounts",
                    "name=Grace&type=savings&deposit=1500", token.token());
            assertEquals(201, createSavings.statusCode(), "Account creation should return 201");
            int savingsAccount = extractAccountNumber(createSavings.body());

            HttpResponse createCurrent = sendRequest("POST", baseUrl + "/accounts",
                    "name=Henry&type=current&deposit=50", token.token());
            assertEquals(201, createCurrent.statusCode(), "Account creation should return 201");
            int currentAccount = extractAccountNumber(createCurrent.body());

            HttpResponse deposit = sendRequest("POST", baseUrl + "/operations/deposit",
                    "accountNumber=" + savingsAccount + "&amount=100", token.token());
            assertEquals(200, deposit.statusCode(), "Deposit should succeed");
            assertTrue(deposit.body().contains("\"success\":true"), "Deposit response should indicate success");

            HttpResponse transfer = sendRequest("POST", baseUrl + "/operations/transfer",
                    "sourceAccount=" + savingsAccount + "&targetAccount=" + currentAccount + "&amount=75", token.token());
            assertEquals(200, transfer.statusCode(), "Transfer should succeed");
            assertTrue(transfer.body().contains("\"success\":true"), "Transfer response should indicate success");

            HttpResponse savingsDetails = sendRequest("GET", baseUrl + "/accounts/" + savingsAccount, null);
            assertEquals(200, savingsDetails.statusCode(), "Account lookup should succeed");
            assertTrue(savingsDetails.body().contains("\"userName\":\"Grace\""),
                    "Account payload should contain the owner name");

            HttpResponse rename = sendRequest("PUT", baseUrl + "/accounts/" + currentAccount,
                    "userName=Henry%20Updated");
            assertEquals(200, rename.statusCode(), "Rename should succeed");
            assertTrue(rename.body().contains("Henry Updated"),
                    "Rename response should include the updated account");

            HttpResponse delete = sendRequest("DELETE", baseUrl + "/accounts/" + currentAccount, null);
            assertEquals(200, delete.statusCode(), "Account deletion should succeed");
            assertTrue(delete.body().contains("\"success\":true"),
                    "Delete response should indicate success");

            HttpResponse deletedLookup = sendRequest("GET", baseUrl + "/accounts/" + currentAccount, null);
            assertEquals(404, deletedLookup.statusCode(), "Deleted accounts should not be retrievable");

            HttpResponse accounts = sendRequest("GET", baseUrl + "/accounts", null);
            HttpResponse accounts = sendRequest("GET", baseUrl + "/accounts", null, token.token());
            assertEquals(200, accounts.statusCode(), "Accounts listing should succeed");
            assertTrue(accounts.body().contains("\"formattedBalance\":\"1525.00\""),
                    "Savings account should reflect post-transfer balance");
            assertFalse(accounts.body().contains("\"formattedBalance\":\"125.00\""),
                    "Closed accounts should not appear in listings");
        } finally {
            server.stop();
            bank.shutdown();
            System.clearProperty("banking.storage.mode");
            System.clearProperty("banking.data.path");
            try (var paths = Files.walk(tempDir)) {
                paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }
    }

    private void shouldGenerateAnalyticsReport() {
        Bank bank = new Bank();
        try {
            Account alpha = bank.createAccount("Iris", "savings", 0);
            Account beta = bank.createAccount("Jules", "current", 0);

            bank.deposit(alpha.getAccountNumber(), 1600.0).join();
            bank.withdraw(alpha.getAccountNumber(), 200.0).join();
            bank.deposit(beta.getAccountNumber(), 600.0).join();
            bank.transfer(beta.getAccountNumber(), alpha.getAccountNumber(), 150.0).join();

            AnalyticsReportRequest request = AnalyticsReportRequest.builder()
                    .withStartDate(LocalDate.now().minusDays(2))
                    .withEndDate(LocalDate.now().plusDays(1))
                    .withLargeTransactionThreshold(400.0)
                    .withRollingWindowDays(2)
                    .build();
            AnalyticsReport report = bank.generateAnalyticsReport(request, new AccountAnalyticsService()).join();

            assertEquals(2, report.balanceSnapshots().size(), "Both accounts should be represented");
            assertTrue(report.totalBalance() > 0, "Total balance should reflect account holdings");
            assertTrue(report.trendPoints().size() >= 3, "Trend should include multiple days");

            double alphaNetChange = report.balanceSnapshots().stream()
                    .filter(s -> s.accountNumber() == alpha.getAccountNumber())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing alpha snapshot"))
                    .netChange();
            assertEquals(1550.0, alphaNetChange, 0.0001, "Alpha net change should reflect transactions");

            boolean anomalyDetected = report.anomalies().stream()
                    .anyMatch(anomaly -> anomaly.accountNumber() == alpha.getAccountNumber()
                            && anomaly.description().contains("High value"));
            assertTrue(anomalyDetected, "High value transaction anomaly should be reported");
        } finally {
            bank.shutdown();
        }
    }

<<<<<<< HEAD:src/test/java/banking/test/BankTestRunner.java
    private void shouldFormatAnalyticsExports() {
        Bank bank = new Bank();
=======
    private void shouldHardenGateway() {
        Bank bank = new Bank();
        BankHttpServer server = new BankHttpServer(bank, 0);
        try {
            server.start();
            String baseUrl = "http://localhost:" + server.getPort();

            HttpResponse rejectedContentType = sendRequest("POST", baseUrl + "/accounts",
                    "name=Invalid&type=savings", "text/plain");
            assertEquals(400, rejectedContentType.statusCode(), "Invalid content type should be rejected");
            assertTrue(rejectedContentType.body().contains("Unsupported Content-Type"),
                    "Error response should describe validation failure");

            boolean rateLimited = false;
            for (int i = 0; i < 25; i++) {
                HttpResponse response = sendRequest("GET", baseUrl + "/health", null);
                if (response.statusCode() == 429) {
                    rateLimited = true;
                    break;
                }
            }
            assertTrue(rateLimited, "Burst traffic should trigger rate limiting");
        } finally {
            server.stop();
            bank.shutdown();
        }
    }

    private void shouldEmitTelemetry() {
        Bank bank = new Bank();
        try {
            Account account = bank.createAccount("Ivy", "savings", 500);
            bank.deposit(account.getAccountNumber(), 250).join();
            bank.withdraw(account.getAccountNumber(), 100).join();

            TelemetryCollector.TelemetrySnapshot snapshot = TelemetryCollector.getInstance().snapshot();
            assertTrue(snapshot.successfulOperations() >= 2,
                    "Successful operations should be tracked");
            assertEquals(0, snapshot.httpRequests(), "No HTTP requests should be recorded for pure service flows");
            assertTrue(!snapshot.events().isEmpty(), "Telemetry events should be captured");
        } finally {
            bank.shutdown();
        }
    }

    private HttpResponse sendRequest(String method, String url, String body) {
        return sendRequest(method, url, body, "application/x-www-form-urlencoded; charset=utf-8");
    }

    private HttpResponse sendRequest(String method, String url, String body, String contentType) {
>>>>>>> origin/pr/21:src/banking/test/BankTestRunner.java
        try {
            Account account = bank.createAccount("Kara", "savings", 0);
            bank.deposit(account.getAccountNumber(), 1300.0).join();
            bank.withdraw(account.getAccountNumber(), 100.0).join();

            AnalyticsReportRequest request = AnalyticsReportRequest.builder()
                    .withStartDate(LocalDate.now().minusDays(1))
                    .withEndDate(LocalDate.now().plusDays(1))
                    .build();
            AnalyticsReport report = bank.generateAnalyticsReport(request, new AccountAnalyticsService()).join();

            AnalyticsPresenter presenter = new AnalyticsPresenter();
            String csv = presenter.toCsv(report);
            String json = presenter.toJson(report);

            assertTrue(csv.contains("totalBalance"), "CSV should list total balance metric");
            assertTrue(csv.contains(Integer.toString(account.getAccountNumber())),
                    "CSV should include account number");
            assertTrue(json.contains("\"balances\""), "JSON should embed balances section");
            assertTrue(
                    json.contains(String.format(java.util.Locale.US, "\"totalBalance\":%.2f", report.totalBalance())),
                    "JSON should embed numeric totals");
        } finally {
            bank.shutdown();
        }
    }

    private HttpResponse sendRequest(String method, String url, String body, String token) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setDoInput(true);
<<<<<<< HEAD:src/test/java/banking/test/BankTestRunner.java
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            connection.setRequestProperty("X-API-Key", System.getProperty("banking.test.apiKey", "local-dev-key"));
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
=======
            if (contentType != null) {
                connection.setRequestProperty("Content-Type", contentType);
>>>>>>> origin/pr/21:src/banking/test/BankTestRunner.java
            }
            if (body != null && !body.isEmpty()) {
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(payload.length);
                connection.connect();
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(payload);
                }
            } else {
                connection.connect();
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseBody = stream == null ? "" : readStream(stream);
            connection.disconnect();
            return new HttpResponse(status, responseBody);
        } catch (IOException e) {
            throw new AssertionError("HTTP request failed: " + e.getMessage(), e);
        }
    }

    private String readStream(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private int extractAccountNumber(String json) {
        int index = json.indexOf("\"accountNumber\":");
        if (index < 0) {
            throw new AssertionError("Account number not present in response: " + json);
        }
        int start = index + "\"accountNumber\":".length();
        int end = json.indexOf(',', start);
        if (end < 0) {
            end = json.indexOf('}', start);
        }
        return Integer.parseInt(json.substring(start, end));
    }

    private static void assertEquals(double expected, double actual, double delta, String message) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    private static void assertNotNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    private record HttpResponse(int statusCode, String body) {
    }

    @FunctionalInterface
    private interface TestCase {
        void run();
    }
}