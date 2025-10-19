package banking.api;

import banking.account.Account;
import banking.operation.OperationResult;
import banking.persistence.BankDAO;
import banking.security.AuthenticationToken;
import banking.security.AuthorizationService;
import banking.security.ForbiddenException;
import banking.security.Permission;
import banking.security.TokenService;
import banking.security.UnauthorizedException;
import banking.service.Bank;
import banking.ui.presenter.AnalyticsPresenter;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Hardened HTTP facade exposing banking capabilities for automation and
 * integration tests.
 */
public final class BankHttpServer {
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(10);
    private static final String APPLICATION_JSON = "application/json; charset=utf-8";
    private static final String TEXT_PLAIN = "text/plain; charset=utf-8";

    private final Bank bank;
    private final int requestedPort;<<<<<<<HEAD:src/banking/api/BankHttpServer.java
    private final String expectedApiKey;=======
    private final TokenService tokenService;
    private final AuthorizationService authorizationService;>>>>>>>origin/pr/19:src/main/java/banking/api/BankHttpServer.java
    private HttpServer server;
    private ExecutorService executorService;
    private Instant bootInstant;

    <<<<<<<HEAD:src/banking/api/BankHttpServer.java

    public BankHttpServer(Bank bank, int port) {
        this(bank,
                port,
                new AnalyticsReportService(bank,
                        new TrendAnalyticsService(),
                        new AnomalyDetectionService(),
                        new RangeAnalyticsService()),
                new ReportFormatter());
    }

    public BankHttpServer(Bank bank,
            int port,
            AnalyticsReportService analyticsReportService,
            ReportFormatter reportFormatter) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.requestedPort = port;
        this.expectedApiKey = Optional.ofNullable(System.getenv("BANKING_API_KEY"))
                .filter(value -> !value.isBlank())
                .orElse("local-dev-key");
=======

    public BankHttpServer(Bank bank, int port, TokenService tokenService,
                          AuthorizationService authorizationService) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.requestedPort = port;
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
>>>>>>> origin/pr/19:src/main/java/banking/api/BankHttpServer.java
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

        createContext("/health", new HealthHandler());
        createContext("/healthz", new HealthHandler());
        createContext("/metrics", new MetricsHandler());
        createContext("/accounts", new AccountsHandler());
        createContext("/accounts/", new AccountDetailHandler());
        createContext("/operations/deposit", new OperationHandler(OperationType.DEPOSIT));
        createContext("/operations/withdraw", new OperationHandler(OperationType.WITHDRAW));
        createContext("/operations/transfer", new OperationHandler(OperationType.TRANSFER));

        server.start();
        System.out.printf("HTTP API listening on port %d%n", getPort());
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

        bank.shutdown();
        BankDAO.saveBank(bank);
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

    private void createContext(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
    }

    private abstract class BaseHandler implements HttpHandler {
        @Override
        public final void handle(HttpExchange exchange) throws IOException {
            try {
                if (requiresAuthentication()) {
                    enforceApiKey(exchange.getRequestHeaders());
                }
                handleInternal(exchange);
<<<<<<< HEAD:src/banking/api/BankHttpServer.java
            } catch (ClientErrorException e) {
                writeJson(exchange, e.statusCode,
                        Map.of("error", e.getMessage(), "success", false));
=======
            } catch (UnauthorizedException e) {
                respond(exchange, 401, jsonError(e.getMessage()));
            } catch (ForbiddenException e) {
                respond(exchange, 403, jsonError(e.getMessage()));
            } catch (IllegalArgumentException e) {
                respond(exchange, 400, jsonError(e.getMessage()));
>>>>>>> origin/pr/19:src/main/java/banking/api/BankHttpServer.java
            } catch (Exception e) {
                writeJson(exchange, 500,
                        Map.of("error", "Internal server error: " + e.getMessage(), "success", false));
            }
        }

        protected abstract void handleInternal(HttpExchange exchange) throws Exception;

        <<<<<<<HEAD:src/banking/api/BankHttpServer.java

        protected boolean requiresAuthentication() {
            return true;
=======

        protected AuthenticationToken requireAuthentication(HttpExchange exchange) {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header == null || header.isBlank() || !header.startsWith("Bearer ")) {
                throw new UnauthorizedException("Missing bearer token");
            }
            String tokenValue = header.substring("Bearer ".length()).trim();
            return tokenService.validate(tokenValue)
                .orElseThrow(() -> new UnauthorizedException("Token is invalid or expired"));
        }

        protected AuthenticationToken requirePermission(HttpExchange exchange, Permission permission) {
            AuthenticationToken token = requireAuthentication(exchange);
            authorizationService.ensureAuthorized(token, permission);
            return token;
>>>>>>> origin/pr/19:src/main/java/banking/api/BankHttpServer.java
        }

        protected void ensureMethod(HttpExchange exchange, String expectedMethod) {
            if (!expectedMethod.equalsIgnoreCase(exchange.getRequestMethod())) {
                throw new ClientErrorException(405, "Unsupported method. Expected " + expectedMethod);
            }
        }

        private void enforceApiKey(Headers headers) {
            String provided = headers.getFirst("X-API-Key");
            if (provided == null || !provided.equals(expectedApiKey)) {
                throw new ClientErrorException(401, "Missing or invalid API key");
            }
        }

        protected Map<String, String> parseParams(HttpExchange exchange) throws IOException {
            Map<String, String> params = new LinkedHashMap<>();
            URI uri = exchange.getRequestURI();
            String query = uri.getRawQuery();
            if (query != null && !query.isEmpty()) {
                parseInto(params, query);
            }
            String method = exchange.getRequestMethod();
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                    || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                if (bodyBytes.length > 0) {
                    parseInto(params, new String(bodyBytes, StandardCharsets.UTF_8));
                }
            }
            return params;
        }

        private void parseInto(Map<String, String> params, String queryString) {
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                if (pair.isEmpty()) {
                    continue;
                }
                String[] keyValue = pair.split("=", 2);
                String key = decode(keyValue[0]);
                String value = keyValue.length > 1 ? decode(keyValue[1]) : "";
                params.put(key, value);
            }
        }

        private String decode(String value) {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

    }

    private final class HealthHandler extends BaseHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws IOException {
            requirePermission(exchange, Permission.HEALTH_READ);
            ensureMethod(exchange, "GET");
            writeJson(exchange, 200, Map.of("status", "ok", "uptimeSeconds",
                    Duration.between(bootInstant, Instant.now()).toSeconds()));
        }

        @Override
        protected boolean requiresAuthentication() {
            return false;
        }
    }

    private final class MetricsHandler extends BaseHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws IOException {
            ensureMethod(exchange, "GET");
            Duration uptime = Duration.between(bootInstant, Instant.now());
            StringJoiner joiner = new StringJoiner("\n");
            joiner.add("bank_api_uptime_seconds " + uptime.toSeconds());
            joiner.add("bank_accounts_total " + bank.getAllAccounts().size());
            joiner.add("bank_pending_operations " + bank.getPendingOperationCount());
            writeText(exchange, 200, joiner.toString() + "\n");
        }
    }

    private final class AccountsHandler extends BaseHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
<<<<<<< HEAD:src/banking/api/BankHttpServer.java
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                listAccounts(exchange);
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
=======
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                requirePermission(exchange, Permission.ACCOUNT_READ);
                listAccounts(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                requirePermission(exchange, Permission.ACCOUNT_CREATE);
>>>>>>> origin/pr/19:src/main/java/banking/api/BankHttpServer.java
                createAccount(exchange);
            } else {
                throw new ClientErrorException(405, "Unsupported method. Expected GET or POST");
            }
        }

        private void listAccounts(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseParams(exchange);
            List<Account> accounts;
            if (params.containsKey("type")) {
                accounts = bank.getAccountsByType(params.get("type"));
            } else if (params.containsKey("search")) {
                accounts = bank.searchAccounts(params.get("search"));
            } else {
                accounts = bank.getAllAccounts();
            }

            List<Map<String, Object>> payload = new ArrayList<>();
            for (Account account : accounts) {
                payload.add(accountPayload(account));
            }
            writeJson(exchange, 200, payload);
        }

        private void createAccount(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseParams(exchange);
            String userName = firstPresent(params, "userName", "name");
            if (userName == null || userName.isBlank()) {
                throw new ClientErrorException(400, "Parameter 'userName' is required");
            }
            String accountType = firstPresent(params, "accountType", "type");
            if (accountType == null || accountType.isBlank()) {
                throw new ClientErrorException(400, "Parameter 'accountType' is required");
            }
            String depositParam = firstPresent(params, "initialDeposit", "deposit");
            double initialDeposit = parseDouble(depositParam, "initialDeposit").orElse(0.0);

            Account account = bank.createAccount(userName, accountType, initialDeposit);
            BankDAO.saveBank(bank);
            writeJson(exchange, 201, accountPayload(account));
        }
    }

    private final class AccountDetailHandler extends BaseHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            int accountNumber = extractAccountNumber(exchange);
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                getAccount(exchange, accountNumber);
            } else if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                updateAccount(exchange, accountNumber);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                deleteAccount(exchange, accountNumber);
            } else {
                throw new ClientErrorException(405, "Unsupported method. Expected GET, PUT, PATCH, or DELETE");
            }
        }

        private void getAccount(HttpExchange exchange, int accountNumber) throws IOException {
            Account account = bank.getAccount(accountNumber);
            if (account == null) {
                throw new ClientErrorException(404, "Account not found: " + accountNumber);
            }
            writeJson(exchange, 200, accountPayload(account));
        }

        private void updateAccount(HttpExchange exchange, int accountNumber) throws IOException {
            Map<String, String> params = parseParams(exchange);
            String newName = firstPresent(params, "userName", "name");
            if (newName == null || newName.isBlank()) {
                throw new ClientErrorException(400, "Parameter 'userName' is required");
            }

            boolean updated = bank.updateAccountHolderName(accountNumber, newName);
            if (!updated) {
                throw new ClientErrorException(404, "Account not found: " + accountNumber);
            }

            Account account = bank.getAccount(accountNumber);
            if (account == null) {
                throw new ClientErrorException(404, "Account not found: " + accountNumber);
            }

            BankDAO.saveBank(bank);
            writeJson(exchange, 200, Map.of(
                    "message", "Account holder updated",
                    "account", accountPayload(account)));
        }

        private void deleteAccount(HttpExchange exchange, int accountNumber) throws IOException {
            boolean removed = bank.closeAccount(accountNumber);
            if (!removed) {
                throw new ClientErrorException(404, "Account not found: " + accountNumber);
            }

            BankDAO.saveBank(bank);
            writeJson(exchange, 200, Map.of(
                    "success", true,
                    "message", "Account closed",
                    "accountNumber", accountNumber));
        }

        private int extractAccountNumber(HttpExchange exchange) {
            String contextPath = exchange.getHttpContext().getPath();
            String requestPath = exchange.getRequestURI().getPath();
            if (!requestPath.startsWith(contextPath)) {
                throw new ClientErrorException(404, "Account not found");
            }

            String remainder = requestPath.substring(contextPath.length());
            if (remainder.startsWith("/")) {
                remainder = remainder.substring(1);
            }
            int slashIndex = remainder.indexOf('/');
            if (slashIndex >= 0) {
                remainder = remainder.substring(0, slashIndex);
            }
            if (remainder.isBlank()) {
                throw new ClientErrorException(404, "Account not found");
            }

            try {
                return Integer.parseInt(remainder);
            } catch (NumberFormatException e) {
                throw new ClientErrorException(400, "Invalid account number: " + remainder);
            }
        }
    }

    private final class OperationHandler extends BaseHandler {
        private final OperationType type;

        OperationHandler(OperationType type) {
            this.type = type;
        }

        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            ensureMethod(exchange, "POST");
            requirePermission(exchange, Permission.FUNDS_DEPOSIT);
            Map<String, String> params = parseParams(exchange);
            switch (type) {
                case DEPOSIT -> handleDeposit(exchange, params);
                case WITHDRAW -> handleWithdraw(exchange, params);
                case TRANSFER -> handleTransfer(exchange, params);
                default -> throw new IllegalStateException("Unhandled operation type: " + type);
            }
        }

        private void handleDeposit(HttpExchange exchange, Map<String, String> params) throws IOException {
            int accountNumber = parseRequiredInt(params, "accountNumber", "Account number is required");
            double amount = parseRequiredAmount(params, "amount");
            executeOperation(exchange, bank.deposit(accountNumber, amount));
        }

        private void handleWithdraw(HttpExchange exchange, Map<String, String> params) throws IOException {
            int accountNumber = parseRequiredInt(params, "accountNumber", "Account number is required");
            double amount = parseRequiredAmount(params, "amount");
            executeOperation(exchange, bank.withdraw(accountNumber, amount));
        }

        private void handleTransfer(HttpExchange exchange, Map<String, String> params) throws IOException {
            int source = parseRequiredInt(params, "accountNumber", "Source account number is required",
                    "sourceAccount");
            int target = parseRequiredInt(params, "targetAccountNumber", "Target account number is required",
                    "targetAccount");
            double amount = parseRequiredAmount(params, "amount");
            executeOperation(exchange, bank.transfer(source, target, amount));
        }
    }

    <<<<<<<HEAD:src/banking/api/BankHttpServer.java

    private void executeOperation(HttpExchange exchange, CompletableFuture<OperationResult> future) throws IOException {
        OperationResult result;
        try {
            result = future.get(OPERATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            writeJson(exchange, 504, Map.of(
                    "success", false,
                    "error", "Operation timed out after " + OPERATION_TIMEOUT.toSeconds() + " seconds"));
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writeJson(exchange, 500, Map.of(
                    "success", false,
                    "error", "Operation interrupted"));
            return;
        } catch (ExecutionException e) {
            String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            writeJson(exchange, 500, Map.of(
                    "success", false,
                    "error", "Operation failed: " + message));
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", result.isSuccess());
        payload.put("message", result.getMessage());
        payload.put("completedAt", Instant.now().toString());

        if (result.isSuccess()) {
            BankDAO.saveBank(bank);
            writeJson(exchange, 200, payload);
        } else {
            writeJson(exchange, 409, payload);
        }
    }

    private Map<String, Object> accountPayload(Account account) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountNumber", account.getAccountNumber());
        payload.put("userName", account.getUserName());
        payload.put("accountType", account.getAccountType());
        payload.put("balance", account.getBalance());
        payload.put("formattedBalance", formatAmount(account.getBalance()));
        return payload;
    }

    private void writeJson(HttpExchange exchange, int status, Map<String, ?> body) throws IOException {
        byte[] responseBytes = JsonFormatter.stringify(body).getBytes(StandardCharsets.UTF_8);
        writeResponse(exchange, status, APPLICATION_JSON, responseBytes);
    }

    private void writeJson(HttpExchange exchange, int status, List<?> body) throws IOException {
        byte[] responseBytes = JsonFormatter.stringify(body).getBytes(StandardCharsets.UTF_8);
        writeResponse(exchange, status, APPLICATION_JSON, responseBytes);
    }

    private void writeText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        writeResponse(exchange, status, TEXT_PLAIN, responseBytes);
    }

    private void writeResponse(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
=======
    private final class WithdrawHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            ensureMethod(exchange, "POST");
            requirePermission(exchange, Permission.FUNDS_WITHDRAW);
            Map<String, String> params = parseParams(exchange);
            int accountNumber = parseAccountNumber(params.get("accountNumber"));
            double amount = parseAmount(params.get("amount"));
            OperationResult result = bank.withdraw(accountNumber, amount).join();
            respond(exchange, statusFor(result), resultJson(result));
        }
    }

    private final class TransferHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            ensureMethod(exchange, "POST");
            requirePermission(exchange, Permission.FUNDS_TRANSFER);
            Map<String, String> params = parseParams(exchange);
            int source = parseAccountNumber(params.get("sourceAccount"));
            int target = parseAccountNumber(params.get("targetAccount"));
            double amount = parseAmount(params.get("amount"));
            OperationResult result = bank.transfer(source, target, amount).join();
            respond(exchange, statusFor(result), resultJson(result));
>>>>>>> origin/pr/19:src/main/java/banking/api/BankHttpServer.java
        }
    }

    private Optional<Double> parseDouble(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            throw new ClientErrorException(400, "Invalid " + fieldName + ": " + value);
        }
    }

    private double parseRequiredAmount(Map<String, String> params, String key) {
        String rawAmount = params.get(key);
        if (rawAmount == null || rawAmount.isBlank()) {
            throw new ClientErrorException(400, "Parameter '" + key + "' is required");
        }
        double amount = parseDouble(rawAmount, key).orElse(0.0);
        if (amount <= 0.0) {
            throw new ClientErrorException(400, "Amount must be greater than zero");
        }
        return amount;
    }

    private int parseRequiredInt(Map<String, String> params, String primaryKey, String message, String... aliases) {
        String value = params.get(primaryKey);
        if (value == null || value.isBlank()) {
            for (String alias : aliases) {
                value = params.get(alias);
                if (value != null && !value.isBlank()) {
                    break;
                }
            }
        }
        if (value == null || value.isBlank()) {
            throw new ClientErrorException(400, message);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ClientErrorException(400, "Invalid integer for parameter '" + primaryKey + "': " + value);
        }
    }

    private String firstPresent(Map<String, String> params, String primary, String... aliases) {
        String value = params.get(primary);
        if (value != null && !value.isBlank()) {
            return value;
        }
        for (String alias : aliases) {
            value = params.get(alias);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String formatAmount(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private enum OperationType {
        DEPOSIT,
        WITHDRAW,
        TRANSFER
    }

    private static final class ClientErrorException extends RuntimeException {
        private final int statusCode;

        ClientErrorException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
