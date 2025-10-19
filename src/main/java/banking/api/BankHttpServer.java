package banking.api;

import banking.account.Account;
import banking.operation.OperationResult;
import banking.security.AuthenticationToken;
import banking.security.AuthorizationService;
import banking.security.ForbiddenException;
import banking.security.Permission;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP facade that exposes a subset of the banking service as REST-style endpoints.
 */
public class BankHttpServer {
    private final Bank bank;
    private final int requestedPort;
    private final TokenService tokenService;
    private final AuthorizationService authorizationService;
    private HttpServer server;
    private ExecutorService executorService;

    public BankHttpServer(Bank bank, int port, TokenService tokenService,
                          AuthorizationService authorizationService) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.requestedPort = port;
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
        executorService = Executors.newFixedThreadPool(4);
        server.createContext("/health", new HealthHandler());
        server.createContext("/accounts", new AccountsHandler());
        server.createContext("/operations/deposit", new DepositHandler());
        server.createContext("/operations/withdraw", new WithdrawHandler());
        server.createContext("/operations/transfer", new TransferHandler());
        server.setExecutor(executorService);
        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
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
        }

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
        }

        protected String jsonError(String message) {
            return '{' + "\"success\":false,\"message\":\"" + escape(message) + "\"}";
        }
    }

    private final class HealthHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws IOException {
            requirePermission(exchange, Permission.HEALTH_READ);
            ensureMethod(exchange, "GET");
            respond(exchange, 200, "{\"status\":\"ok\"}");
        }
    }

    private final class AccountsHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                requirePermission(exchange, Permission.ACCOUNT_READ);
                listAccounts(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                requirePermission(exchange, Permission.ACCOUNT_CREATE);
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

    private final class DepositHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            ensureMethod(exchange, "POST");
            requirePermission(exchange, Permission.FUNDS_DEPOSIT);
            Map<String, String> params = parseParams(exchange);
            int accountNumber = parseAccountNumber(params.get("accountNumber"));
            double amount = parseAmount(params.get("amount"));
            OperationResult result = bank.deposit(accountNumber, amount).join();
            respond(exchange, statusFor(result), resultJson(result));
        }
    }

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

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
