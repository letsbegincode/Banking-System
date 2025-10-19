package banking.test;

import banking.account.Account;
import banking.api.BankHttpServer;
import banking.security.AuthenticationService;
import banking.security.AuthenticationToken;
import banking.security.AuthorizationService;
import banking.security.CredentialStore;
import banking.security.OperatorCredential;
import banking.security.PasswordHasher;
import banking.security.Role;
import banking.security.TokenService;
import banking.report.AccountStatement;
import banking.report.StatementGenerator;
import banking.service.Bank;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

    private void shouldDepositFunds() {
        Bank bank = new Bank();
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
        Bank bank = new Bank();
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
        Bank bank = new Bank();
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
        Bank bank = new Bank();
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
        Bank bank = new Bank();
        try {
            Account account = bank.createAccount("Frank", "current", 0);
            bank.deposit(account.getAccountNumber(), 500.0).join();
            bank.withdraw(account.getAccountNumber(), 200.0).join();

            StatementGenerator generator = new StatementGenerator();
            LocalDate start = LocalDate.now().minusDays(1);
            LocalDate end = LocalDate.now().plusDays(1);
            AccountStatement statement = generator.generate(account, start, end);

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

            HttpResponse accounts = sendRequest("GET", baseUrl + "/accounts", null, token.token());
            assertEquals(200, accounts.statusCode(), "Accounts listing should succeed");
            assertTrue(accounts.body().contains("\"balance\":1525.00"),
                    "Savings account should reflect post-transfer balance");
            assertTrue(accounts.body().contains("\"balance\":125.00"),
                    "Current account should reflect received transfer");
        } finally {
            server.stop();
            bank.shutdown();
        }
    }

    private HttpResponse sendRequest(String method, String url, String body, String token) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setDoInput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
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