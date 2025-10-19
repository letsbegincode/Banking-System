package banking.api;

import banking.account.Account;
import banking.api.middleware.ErrorHandling;
import banking.api.middleware.ErrorResponse;
import banking.api.middleware.HttpRequestFilter;
import banking.api.middleware.RateLimiter;
import banking.api.middleware.RateLimitingFilter;
import banking.api.middleware.RequestContext;
import banking.api.middleware.RequestResponseLogger;
import banking.api.middleware.RequestValidationFilter;
import banking.operation.OperationResult;
<<<<<<< HEAD
import banking.security.AuthService;
import banking.security.AuthToken;
import banking.security.Role;
import banking.security.UserPrincipal;
=======
import banking.persistence.PersistenceStatus;
>>>>>>> origin/pr/14
import banking.service.Bank;
import banking.telemetry.TelemetryCollector;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight HTTP facade that exposes a subset of the banking service as
 * REST-style endpoints.
 */
public class BankHttpServer {
    private final Bank bank;
    private final int requestedPort;
    private final RateLimiter rateLimiter;
    private final List<HttpRequestFilter> filters;
    private final AuthService authService;
    private HttpServer server;
    private ExecutorService executorService;
    private Thread shutdownHook;

    public BankHttpServer(Bank bank, int port, AuthService authService) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.requestedPort = port;
        TelemetryCollector collector = TelemetryCollector.getInstance();
        this.rateLimiter = new RateLimiter(20, Duration.ofSeconds(1));
        this.filters = List.of(
                new RateLimitingFilter(rateLimiter),
                new RequestValidationFilter(4096),
                new RequestResponseLogger(collector));
        this.authService = Objects.requireNonNull(authService, "authService");
    }

    public synchronized void start() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(requestedPort), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to start HTTP server", e);
        }
        executorService = Executors.newFixedThreadPool(4);
        server.createContext("/health", new HealthHandler());
        server.createContext("/auth/login", new LoginHandler());
        server.createContext("/accounts", new AccountsHandler());
        server.createContext("/operations/deposit", new DepositHandler());
        server.createContext("/operations/withdraw", new WithdrawHandler());
        server.createContext("/operations/transfer", new TransferHandler());
        server.setExecutor(executorService);
        server.start();
        registerShutdownHook();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executorService = null;
        }
        unregisterShutdownHook();
    }

    public synchronized int getPort() {
        if (server == null) {
            throw new IllegalStateException("Server is not running");
        }
        return server.getAddress().getPort();
    }

    private abstract class JsonHandler implements HttpHandler {
        @Override
        public final void handle(HttpExchange exchange) throws IOException {
            RequestContext context = RequestContext.attach(exchange);
            List<HttpRequestFilter> executedFilters = new ArrayList<>();
            boolean handled = false;
            try {
                for (HttpRequestFilter filter : filters) {
                    filter.before(exchange, context);
                    executedFilters.add(filter);
                }
                handleInternal(exchange);
                handled = true;
            } catch (HttpError e) {
                respond(exchange, e.statusCode(), jsonError(e.getMessage()));
            } catch (IllegalArgumentException e) {
                respond(exchange, 400, jsonError(e.getMessage()));
            } catch (Exception e) {
                context.recordError(e);
                ErrorResponse error = ErrorHandling.resolve(e);
                respond(exchange, error.statusCode(), jsonError(error.message()));
            } finally {
                for (int i = executedFilters.size() - 1; i >= 0; i--) {
                    try {
                        executedFilters.get(i).after(exchange, context);
                    } catch (Exception ignored) {
                        // Best-effort cleanup; errors already reported through telemetry.
                    }
                }
                if (!handled && context.statusCode() < 0) {
                    context.recordResponse(500, 0);
                }
                exchange.close();
            }
        }

        protected abstract void handleInternal(HttpExchange exchange) throws Exception;

        protected void ensureMethod(HttpExchange exchange, String expectedMethod) {
            if (!expectedMethod.equalsIgnoreCase(exchange.getRequestMethod())) {
                throw new IllegalArgumentException("Unsupported method. Expected " + expectedMethod);
            }
        }

        protected Map<String, String> parseParams(HttpExchange exchange) throws IOException {
            Map<String, String> params = new HashMap<>();
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && !query.isEmpty()) {
                parseInto(params, query);
            }
            String method = exchange.getRequestMethod();
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                String body = readBody(exchange.getRequestBody());
                if (!body.isEmpty()) {
                    parseInto(params, body);
                }
            }
            return params;
        }

        private void parseInto(Map<String, String> params, String source) throws IOException {
            String[] pairs = source.split("&");
            for (String pair : pairs) {
                if (pair.isEmpty()) {
                    continue;
                }
                String[] keyValue = pair.split("=", 2);
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                String value = keyValue.length > 1
                        ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name())
                        : "";
                params.put(key, value);
            }
        }

        private String readBody(InputStream inputStream) throws IOException {
            if (inputStream == null) {
                return "";
            }
            byte[] buffer = inputStream.readAllBytes();
            return new String(buffer, StandardCharsets.UTF_8);
        }

        protected void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
            RequestContext.from(exchange).ifPresent(context -> context.recordResponse(status, responseBytes.length));
        }

        protected String jsonError(String message) {
            return '{' + "\"success\":false,\"message\":\"" + escape(message) + "\"}";
        }
    }

    private abstract class ProtectedJsonHandler extends JsonHandler {
        private final Role[] requiredRoles;

        private ProtectedJsonHandler(Role... requiredRoles) {
            this.requiredRoles = requiredRoles;
        }

        @Override
        protected final void handleInternal(HttpExchange exchange) throws Exception {
            UserPrincipal principal = authenticate(exchange);
            handleAuthorized(exchange, principal);
        }

        protected abstract void handleAuthorized(HttpExchange exchange, UserPrincipal principal) throws Exception;

        private UserPrincipal authenticate(HttpExchange exchange) {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new HttpError(401, "Missing bearer token");
            }
            String token = header.substring("Bearer ".length());
            UserPrincipal principal = authService.verifyToken(token)
                    .orElseThrow(() -> new HttpError(401, "Invalid or expired token"));
            if (!authService.hasAnyRole(principal, requiredRoles)) {
                throw new HttpError(403, "Access denied for role " + principal.role());
            }
            return principal;
        }
    }

    private final class HealthHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws IOException {
            ensureMethod(exchange, "GET");
            PersistenceStatus primary = bank.getPrimaryPersistenceStatus();
            PersistenceStatus active = bank.getActivePersistenceStatus();
            boolean available = primary != null && primary.isAvailable();
            int status = available ? 200 : 503;
            String persistence = "\"persistence\":{" + "\"primary\":" + persistenceJson(primary)
                    + ",\"active\":" + persistenceJson(active) + "}";
            String body = '{' + new StringJoiner(",")
                    .add("\"status\":\"" + (available ? "ok" : "degraded") + "\"")
                    .add(persistence)
                    .toString() + '}';
            respond(exchange, status, body);
        }
    }

    private final class LoginHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            ensureMethod(exchange, "POST");
            Map<String, String> params = parseParams(exchange);
            String username = params.get("username");
            String password = params.get("password");
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Parameter 'username' is required");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("Parameter 'password' is required");
            }
            AuthToken token = authService.authenticate(username, password)
                    .orElseThrow(() -> new HttpError(401, "Invalid credentials"));
            respond(exchange, 200, loginResponse(token));
        }

        private String loginResponse(AuthToken token) {
            return '{' + new StringJoiner(",")
                    .add("\"success\":true")
                    .add("\"token\":\"" + escape(token.token()) + "\"")
                    .add("\"role\":\"" + token.principal().role() + "\"")
                    .add("\"expiresAt\":\"" + token.expiresAt() + "\"")
                    .toString() + '}';
        }
    }

    private final class AccountsHandler extends ProtectedJsonHandler {
        private AccountsHandler() {
            super(Role.OPERATOR);
        }

        @Override
        protected void handleAuthorized(HttpExchange exchange, UserPrincipal principal) throws Exception {
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                listAccounts(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                createAccount(exchange);
            } else {
                throw new IllegalArgumentException("Unsupported method. Expected GET or POST");
            }
        }

        private void listAccounts(HttpExchange exchange) throws IOException {
            List<Account> accounts = bank.getAllAccounts();
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (Account account : accounts) {
                joiner.add(accountJson(account));
            }
            respond(exchange, 200, joiner.toString());
        }

        private void createAccount(HttpExchange exchange) throws Exception {
            Map<String, String> params = parseParams(exchange);
            String name = params.get("name");
            String type = params.get("type");
            String depositParam = params.getOrDefault("deposit", "0");
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Parameter 'name' is required");
            }
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("Parameter 'type' is required");
            }
            double deposit;
            try {
                deposit = Double.parseDouble(depositParam);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid deposit amount: " + depositParam);
            }
            Account account = bank.createAccount(name, type, deposit);
            respond(exchange, 201, accountJson(account));
        }
    }

    private final class DepositHandler extends ProtectedJsonHandler {
        private DepositHandler() {
            super(Role.OPERATOR);
        }

        @Override
        protected void handleAuthorized(HttpExchange exchange, UserPrincipal principal) throws Exception {
            ensureMethod(exchange, "POST");
            Map<String, String> params = parseParams(exchange);
            int accountNumber = parseAccountNumber(params.get("accountNumber"));
            double amount = parseAmount(params.get("amount"));
            OperationResult result = bank.deposit(accountNumber, amount).join();
            respond(exchange, statusFor(result), resultJson(result));
        }
    }

    private final class WithdrawHandler extends ProtectedJsonHandler {
        private WithdrawHandler() {
            super(Role.OPERATOR);
        }

        @Override
        protected void handleAuthorized(HttpExchange exchange, UserPrincipal principal) throws Exception {
            ensureMethod(exchange, "POST");
            Map<String, String> params = parseParams(exchange);
            int accountNumber = parseAccountNumber(params.get("accountNumber"));
            double amount = parseAmount(params.get("amount"));
            OperationResult result = bank.withdraw(accountNumber, amount).join();
            respond(exchange, statusFor(result), resultJson(result));
        }
    }

    private final class TransferHandler extends ProtectedJsonHandler {
        private TransferHandler() {
            super(Role.OPERATOR);
        }

        @Override
        protected void handleAuthorized(HttpExchange exchange, UserPrincipal principal) throws Exception {
            ensureMethod(exchange, "POST");
            Map<String, String> params = parseParams(exchange);
            int source = parseAccountNumber(params.get("sourceAccount"));
            int target = parseAccountNumber(params.get("targetAccount"));
            double amount = parseAmount(params.get("amount"));
            OperationResult result = bank.transfer(source, target, amount).join();
            respond(exchange, statusFor(result), resultJson(result));
        }
    }

    private static final class HttpError extends RuntimeException {
        private final int statusCode;

        private HttpError(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }

    private int parseAccountNumber(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Account number is required");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid account number: " + value);
        }
    }

    private double parseAmount(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Amount is required");
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount: " + value);
        }
    }

    private int statusFor(OperationResult result) {
        return result.isSuccess() ? 200 : 422;
    }

    private String resultJson(OperationResult result) {
        return '{' + "\"success\":" + result.isSuccess()
                + ",\"message\":\"" + escape(result.getMessage()) + "\"}";
    }

    private String accountJson(Account account) {
        String balance = String.format(Locale.US, "%.2f", account.getBalance());
        return '{' + new StringJoiner(",")
                .add("\"accountNumber\":" + account.getAccountNumber())
                .add("\"holder\":\"" + escape(account.getUserName()) + "\"")
                .add("\"type\":\"" + escape(account.getAccountType()) + "\"")
                .add("\"balance\":" + balance)
                .toString() + '}';
    }

    private String persistenceJson(PersistenceStatus status) {
        if (status == null) {
            return "{\"provider\":\"unknown\",\"available\":false,\"message\":\"Unavailable\"}";
        }
        StringBuilder builder = new StringBuilder("{");
        builder.append("\"provider\":\"").append(escape(status.getProvider())).append("\"");
        builder.append(",\"available\":").append(status.isAvailable());
        builder.append(",\"message\":\"").append(escape(status.getMessage())).append("\"");
        status.getError()
                .map(Throwable::getMessage)
                .filter(msg -> msg != null && !msg.isBlank())
                .ifPresent(msg -> builder.append(",\"error\":\"").append(escape(msg)).append("\""));
        builder.append('}');
        return builder.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private synchronized void registerShutdownHook() {
        if (shutdownHook != null) {
            return;
        }
        shutdownHook = new Thread(() -> {
            try {
                BankHttpServer.this.stop();
            } finally {
                bank.shutdown();
            }
        }, "bank-http-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private synchronized void unregisterShutdownHook() {
        if (shutdownHook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down.
        }
        shutdownHook = null;
    }
}
