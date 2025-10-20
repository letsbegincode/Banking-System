package banking.test.security;

import banking.account.Account;
import banking.api.BankHttpServer;
import banking.security.AuthenticationException;
import banking.security.AuthenticationService;
import banking.security.AuthenticationToken;
import banking.security.AuthorizationService;
import banking.security.CredentialStore;
import banking.security.OperatorCredential;
import banking.security.PasswordHasher;
import banking.security.Role;
import banking.security.TokenService;
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
import java.util.Set;

/**
 * Ad-hoc test harness that validates the authentication and authorization layers.
 */
public final class SecurityTestRunner {
    private int passed;
    private int failed;

    public static void main(String[] args) {
        SecurityTestRunner runner = new SecurityTestRunner();
        runner.run();
        runner.report();
        if (runner.failed > 0) {
            System.exit(1);
        }
    }

    private void run() {
        execute("password hashing uses salt", this::shouldHashPasswords);
        execute("invalid credentials are rejected", this::shouldRejectInvalidLogin);
        execute("api requires bearer token", this::shouldRequireTokenForApi);
        execute("token permissions enforced", this::shouldEnforcePermissions);
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

    private void shouldHashPasswords() {
        PasswordHasher hasher = new PasswordHasher();
        String hash = hasher.hash("secret");
        assertTrue(!"secret".equals(hash), "Hash should not echo the password");
        assertTrue(hasher.verify("secret", hash), "Hasher should validate passwords");
    }

    private void shouldRejectInvalidLogin() {
        PasswordHasher hasher = new PasswordHasher();
        CredentialStore store = new CredentialStore();
        store.store(new OperatorCredential("analyst", hasher.hash("correct"), Set.of(Role.AUDITOR)));
        AuthenticationService service = new AuthenticationService(store, hasher, new TokenService(), Duration.ofMinutes(5));
        try {
            service.login("analyst", "wrong");
            throw new AssertionError("Expected login to fail");
        } catch (AuthenticationException expected) {
            // success
        }
    }

    private void shouldRequireTokenForApi() {
        Bank bank = new Bank();
        PasswordHasher hasher = new PasswordHasher();
        CredentialStore store = new CredentialStore();
        store.store(new OperatorCredential("operator", hasher.hash("password"), Set.of(Role.ADMIN)));
        TokenService tokens = new TokenService();
        AuthenticationService authentication = new AuthenticationService(store, hasher, tokens, Duration.ofMinutes(5));
        BankHttpServer server = new BankHttpServer(bank, 0, authentication, tokens, new AuthorizationService());
        try {
            server.start();
            HttpResponse response = sendRequest("GET", "http://localhost:" + server.getPort() + "/health", null, null);
            assertEquals(401, response.statusCode(), "Missing token should be unauthorized");
        } finally {
            server.stop();
            bank.shutdown();
        }
    }

    private void shouldEnforcePermissions() {
        Bank bank = new Bank();
        Account account = bank.createAccount("Secured", "savings", 100);
        TokenService tokens = new TokenService();
        AuthorizationService authorization = new AuthorizationService();
        PasswordHasher hasher = new PasswordHasher();
        CredentialStore store = new CredentialStore();
        store.store(new OperatorCredential("auditor", hasher.hash("audit"), Set.of(Role.AUDITOR)));
        AuthenticationService auth = new AuthenticationService(store, hasher, tokens, Duration.ofMinutes(5));
        BankHttpServer server = new BankHttpServer(bank, 0, auth, tokens, authorization);
        AuthenticationToken token = auth.login("auditor", "audit");
        try {
            server.start();
            HttpResponse response = sendRequest("POST", "http://localhost:" + server.getPort() + "/operations/deposit",
                "accountNumber=" + account.getAccountNumber() + "&amount=50", token.token());
            assertEquals(403, response.statusCode(), "Role without deposit permission should be forbidden");
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

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record HttpResponse(int statusCode, String body) {
    }

    @FunctionalInterface
    private interface TestCase {
        void run();
    }
}
