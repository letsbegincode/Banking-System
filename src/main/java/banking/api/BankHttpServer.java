package banking.api;

import banking.account.Account;
import banking.operation.OperationResult;
import banking.security.AuthenticationException;
import banking.security.AuthenticationService;
import banking.security.AuthenticationToken;
import banking.security.AuthorizationService;
import banking.security.Permission;
import banking.security.ForbiddenException;
import banking.security.TokenService;
import banking.security.UnauthorizedException;
import banking.service.Bank;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Hardened HTTP facade exposing banking capabilities for automation scenarios.
 */
public final class BankHttpServer {
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(15);
    private static final String APPLICATION_JSON = "application/json; charset=utf-8";
    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final Bank bank;
    private final int requestedPort;
    private final AuthenticationService authenticationService;
    private final TokenService tokenService;
    private final AuthorizationService authorizationService;

    private HttpServer server;
    private ExecutorService executorService;
    private Instant bootInstant;

    public BankHttpServer(Bank bank,
                          int port,
                          AuthenticationService authenticationService,
                          TokenService tokenService,
                          AuthorizationService authorizationService) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.requestedPort = port;
        this.authenticationService = Objects.requireNonNull(authenticationService, "authenticationService");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
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
        executorService = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
        server.setExecutor(executorService);
        bootInstant = Instant.now();

        server.createContext("/health", new HealthHandler());
        server.createContext("/healthz", new HealthHandler());
        server.createContext("/metrics", new MetricsHandler());
        server.createContext("/auth/login", new LoginHandler());
        server.createContext("/accounts", new AccountsHandler());
        server.createContext("/accounts/", new AccountDetailHandler());
        server.createContext("/operations/deposit", new DepositHandler());
        server.createContext("/operations/withdraw", new WithdrawHandler());
        server.createContext("/operations/transfer", new TransferHandler());

        server.start();
        System.out.printf(Locale.US, "HTTP API listening on port %d%n", getPort());
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
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
            } finally {
                executorService = null;
            }
        }
    }

    public synchronized boolean isRunning() {
        return server != null;
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
            try {
                handleInternal(exchange);
            } catch (UnauthorizedException e) {
                respond(exchange, 401, jsonError(e.getMessage()));
            } catch (ForbiddenException e) {
                respond(exchange, 403, jsonError(e.getMessage()));
            } catch (IllegalArgumentException e) {
                respond(exchange, 400, jsonError(e.getMessage()));
            } catch (Exception e) {
                respond(exchange, 500, jsonError("Internal server error: " + e.getMessage()));
            } finally {
                exchange.close();
            }
        }

        protected abstract void handleInternal(HttpExchange exchange) throws Exception;

        protected AuthenticationToken requirePermission(HttpExchange exchange, Permission permission) {
            AuthenticationToken token = authenticate(exchange.getRequestHeaders());
            authorizationService.ensureAuthorized(token, permission);
            return token;
        }

        protected AuthenticationToken authenticate(Headers headers) {
            String header = headers.getFirst("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new UnauthorizedException("Missing bearer token");
            }
            String tokenValue = header.substring("Bearer ".length()).trim();
            Optional<AuthenticationToken> token = tokenService.validate(tokenValue);
            return token.orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));
        }

        protected Map<String, String> parseParams(HttpExchange exchange) throws IOException {
            Map<String, String> params = new HashMap<>();
            String query = null;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())
                    || "PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                query = readBody(exchange.getRequestBody());
            }
            if (query == null || query.isBlank()) {
                URI uri = exchange.getRequestURI();
                query = uri.getRawQuery();
            }
            if (query == null || query.isBlank()) {
                return params;
            }
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                if (idx >= 0) {
                    String key = urlDecode(pair.substring(0, idx));
                    String value = urlDecode(pair.substring(idx + 1));
                    params.put(key, value);
                } else {
                    params.put(urlDecode(pair), "");
                }
            }
            return params;
        }

        private String readBody(InputStream inputStream) throws IOException {
            if (inputStream == null) {
                return "";
            }
            byte[] buffer = inputStream.readAllBytes();
            return new String(buffer, StandardCharsets.UTF_8);
        }

        private String urlDecode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

        protected void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", APPLICATION_JSON);
            exchange.sendResponseHeaders(statusCode, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        }

        protected String jsonError(String message) {
            return "{\"success\":false,\"error\":\"" + escapeJson(message) + "\"}";
        }

        protected String escapeJson(String value) {
            return escape(value);
        }
    }

    private final class HealthHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws IOException {
            ensureMethod(exchange, "GET");
            requirePermission(exchange, Permission.HEALTH_READ);
            respond(exchange, 200, "{\"status\":\"UP\"}");
        }
    }

    private final class MetricsHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws IOException {
            ensureMethod(exchange, "GET");
            AuthenticationToken token = requirePermission(exchange, Permission.HEALTH_READ);
            long uptimeSeconds = bootInstant == null ? 0 : Duration.between(bootInstant, Instant.now()).getSeconds();
            String body = "{" +
                    "\"principal\":\"" + escape(token.principal()) + "\"," +
                    "\"uptimeSeconds\":" + uptimeSeconds + "," +
                    "\"accounts\":" + bank.getAllAccounts().size() +
                    "}";
            respond(exchange, 200, body);
        }
    }

    private final class LoginHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws IOException {
            ensureMethod(exchange, "POST");
            Map<String, String> params = parseParams(exchange);
            String username = params.getOrDefault("username", "");
            String password = params.getOrDefault("password", "");
            if (username.isBlank() || password.isBlank()) {
                throw new IllegalArgumentException("username and password are required");
            }
            try {
                AuthenticationToken token = authenticationService.login(username, password);
                String body = "{" +
                        "\"token\":\"" + escape(token.token()) + "\"," +
                        "\"principal\":\"" + escape(token.principal()) + "\"," +
                        "\"expiresAt\":\"" + ISO_INSTANT.format(token.expiresAt()) + "\"," +
                        "\"roles\":[" + joinRoles(token.roles()) + "]}";
                respond(exchange, 200, body);
            } catch (AuthenticationException e) {
                respond(exchange, 401, jsonError(e.getMessage()));
            }
        }

        private String joinRoles(Iterable<?> roles) {
            StringJoiner joiner = new StringJoiner(",");
            for (Object role : roles) {
                joiner.add("\"" + escape(role.toString()) + "\"");
            }
            return joiner.toString();
        }
    }

    private final class AccountsHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            switch (exchange.getRequestMethod()) {
                case "GET" -> listAccounts(exchange);
                case "POST" -> createAccount(exchange);
                default -> throw new IllegalArgumentException("Unsupported method: " + exchange.getRequestMethod());
            }
        }

        private void listAccounts(HttpExchange exchange) throws IOException {
            requirePermission(exchange, Permission.ACCOUNT_READ);
            List<Account> accounts = bank.getAllAccounts();
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            builder.append("\"accounts\":[");
            StringJoiner joiner = new StringJoiner(",");
            for (Account account : accounts) {
                joiner.add(accountJson(account));
            }
            builder.append(joiner);
            builder.append("],\"count\":").append(accounts.size()).append('}');
            respond(exchange, 200, builder.toString());
        }

        private void createAccount(HttpExchange exchange) throws Exception {
            requirePermission(exchange, Permission.ACCOUNT_CREATE);
            Map<String, String> params = parseParams(exchange);
            String userName = params.get("name");
            String type = params.get("type");
            double initialDeposit = parseAmount(params.get("deposit"));
            if (userName == null || userName.isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("type is required");
            }
            Account account = bank.createAccount(userName, type, initialDeposit);
            respond(exchange, 201, accountJson(account));
        }
    }

    private final class AccountDetailHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            String idPart = path.substring("/accounts/".length());
            if (idPart.isEmpty()) {
                throw new IllegalArgumentException("Account identifier missing");
            }
            int accountNumber = parseAccountNumber(idPart);
            switch (exchange.getRequestMethod()) {
                case "GET" -> fetch(exchange, accountNumber);
                case "PUT" -> update(exchange, accountNumber);
                case "DELETE" -> delete(exchange, accountNumber);
                default -> throw new IllegalArgumentException("Unsupported method: " + exchange.getRequestMethod());
            }
        }

        private void fetch(HttpExchange exchange, int accountNumber) throws IOException {
            requirePermission(exchange, Permission.ACCOUNT_READ);
            Account account = bank.getAccount(accountNumber);
            if (account == null) {
                respond(exchange, 404, jsonError("Account not found"));
                return;
            }
            respond(exchange, 200, accountJson(account));
        }

        private void update(HttpExchange exchange, int accountNumber) throws Exception {
            requirePermission(exchange, Permission.ACCOUNT_CREATE);
            Map<String, String> params = parseParams(exchange);
            String userName = params.get("userName");
            if (userName == null || userName.isBlank()) {
                throw new IllegalArgumentException("userName is required");
            }
            boolean updated = bank.updateAccountHolderName(accountNumber, userName);
            if (!updated) {
                respond(exchange, 404, jsonError("Account not found"));
                return;
            }
            Account account = bank.getAccount(accountNumber);
            respond(exchange, 200, accountJson(account));
        }

        private void delete(HttpExchange exchange, int accountNumber) throws IOException {
            requirePermission(exchange, Permission.ACCOUNT_CREATE);
            boolean removed = bank.closeAccount(accountNumber);
            if (!removed) {
                respond(exchange, 404, jsonError("Account not found"));
                return;
            }
            respond(exchange, 200, "{\"success\":true}");
        }
    }

    private final class DepositHandler extends OperationHandler {
        private DepositHandler() {
            super(Permission.FUNDS_DEPOSIT);
        }

        @Override
        protected OperationResult invoke(Bank bank, Map<String, String> params) {
            int accountNumber = parseAccountNumber(params.get("accountNumber"));
            double amount = parseAmount(params.get("amount"));
            return join(bank.deposit(accountNumber, amount));
        }
    }

    private final class WithdrawHandler extends OperationHandler {
        private WithdrawHandler() {
            super(Permission.FUNDS_WITHDRAW);
        }

        @Override
        protected OperationResult invoke(Bank bank, Map<String, String> params) {
            int accountNumber = parseAccountNumber(params.get("accountNumber"));
            double amount = parseAmount(params.get("amount"));
            return join(bank.withdraw(accountNumber, amount));
        }
    }

    private final class TransferHandler extends OperationHandler {
        private TransferHandler() {
            super(Permission.FUNDS_TRANSFER);
        }

        @Override
        protected OperationResult invoke(Bank bank, Map<String, String> params) {
            int source = parseAccountNumber(params.get("sourceAccount"));
            int target = parseAccountNumber(params.get("targetAccount"));
            double amount = parseAmount(params.get("amount"));
            return join(bank.transfer(source, target, amount));
        }
    }

    private abstract class OperationHandler extends JsonHandler {
        private final Permission permission;

        protected OperationHandler(Permission permission) {
            this.permission = permission;
        }

        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            ensureMethod(exchange, "POST");
            requirePermission(exchange, permission);
            Map<String, String> params = parseParams(exchange);
            OperationResult result = invoke(bank, params);
            respond(exchange, statusFor(result), resultJson(result));
        }

        protected abstract OperationResult invoke(Bank bank, Map<String, String> params);
    }

    private OperationResult join(CompletableFuture<OperationResult> future) {
        try {
            return future.get(OPERATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Operation timed out", e);
        }
    }

    private int statusFor(OperationResult result) {
        return result.isSuccess() ? 200 : 422;
    }

    private String resultJson(OperationResult result) {
        return "{" +
                "\"success\":" + result.isSuccess() + "," +
                "\"message\":\"" + escape(result.getMessage()) + "\"" +
                "}";
    }

    private int parseAccountNumber(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("accountNumber is required");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid account number: " + value);
        }
    }

    private double parseAmount(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("amount is required");
        }
        try {
            double amount = Double.parseDouble(value.trim());
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount: " + value);
        }
    }

    private String accountJson(Account account) {
        return "{" +
                "\"accountNumber\":" + account.getAccountNumber() + "," +
                "\"userName\":\"" + escape(account.getUserName()) + "\"," +
                "\"accountType\":\"" + escape(account.getAccountType()) + "\"," +
                "\"balance\":\"" + String.format(Locale.US, "%.2f", account.getBalance()) + "\"" +
                "}";
    }

    private void ensureMethod(HttpExchange exchange, String expected) {
        if (!expected.equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + exchange.getRequestMethod());
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
