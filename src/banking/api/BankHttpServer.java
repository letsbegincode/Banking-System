package banking.api;

import banking.account.Account;
import banking.operation.OperationResult;
import banking.report.AccountAnalyticsService;
import banking.report.AnalyticsReport;
import banking.report.AnalyticsReportRequest;
import banking.service.Bank;
import banking.ui.presenter.AnalyticsPresenter;

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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletionException;

/**
 * Lightweight HTTP facade that exposes a subset of the banking service as REST-style endpoints.
 */
public class BankHttpServer {
    private final Bank bank;
    private final int requestedPort;
    private final AccountAnalyticsService analyticsService;
    private final AnalyticsPresenter analyticsPresenter;
    private HttpServer server;
    private ExecutorService executorService;

    public BankHttpServer(Bank bank, int port) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.requestedPort = port;
        this.analyticsService = new AccountAnalyticsService();
        this.analyticsPresenter = new AnalyticsPresenter();
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
        server.createContext("/reports/analytics.json", new AnalyticsJsonHandler());
        server.createContext("/reports/analytics.csv", new AnalyticsCsvHandler());
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
            } catch (IllegalArgumentException e) {
                respond(exchange, 400, jsonError(e.getMessage()));
            } catch (Exception e) {
                respond(exchange, 500, jsonError("Internal server error: " + e.getMessage()));
            } finally {
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
        }

        protected String jsonError(String message) {
            return '{' + "\"success\":false,\"message\":\"" + escape(message) + "\"}";
        }
    }

    private final class HealthHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws IOException {
            ensureMethod(exchange, "GET");
            respond(exchange, 200, "{\"status\":\"ok\"}");
        }
    }

    private final class AccountsHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
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

    private final class DepositHandler extends JsonHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            ensureMethod(exchange, "POST");
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
            Map<String, String> params = parseParams(exchange);
            int source = parseAccountNumber(params.get("sourceAccount"));
            int target = parseAccountNumber(params.get("targetAccount"));
            double amount = parseAmount(params.get("amount"));
            OperationResult result = bank.transfer(source, target, amount).join();
            respond(exchange, statusFor(result), resultJson(result));
        }
    }

    private abstract class AnalyticsHandler extends JsonHandler {
        protected AnalyticsReportRequest parseRequest(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseParams(exchange);
            LocalDate start = parseDate(params.get("start"), LocalDate.now().minusDays(30));
            LocalDate end = parseDate(params.get("end"), LocalDate.now());
            double threshold = parseDouble(params.get("threshold"), 5000.0);
            int window = parseInt(params.get("window"), 7);
            return AnalyticsReportRequest.builder()
                    .withStartDate(start)
                    .withEndDate(end)
                    .withLargeTransactionThreshold(threshold)
                    .withRollingWindowDays(window)
                    .build();
        }

        protected AnalyticsReport computeReport(AnalyticsReportRequest request) {
            try {
                return bank.generateAnalyticsReport(request, analyticsService).join();
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                throw new IllegalStateException("Analytics computation failed: " + cause.getMessage(), cause);
            }
        }

        private LocalDate parseDate(String value, LocalDate defaultValue) {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return LocalDate.parse(value.trim());
        }

        private double parseDouble(String value, double defaultValue) {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid numeric value: " + value);
            }
        }

        private int parseInt(String value, int defaultValue) {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer value: " + value);
            }
        }
    }

    private final class AnalyticsJsonHandler extends AnalyticsHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            ensureMethod(exchange, "GET");
            AnalyticsReportRequest request = parseRequest(exchange);
            AnalyticsReport report = computeReport(request);
            respond(exchange, 200, analyticsPresenter.toJson(report));
        }
    }

    private final class AnalyticsCsvHandler extends AnalyticsHandler {
        @Override
        protected void handleInternal(HttpExchange exchange) throws Exception {
            ensureMethod(exchange, "GET");
            AnalyticsReportRequest request = parseRequest(exchange);
            AnalyticsReport report = computeReport(request);
            respondCsv(exchange, 200, analyticsPresenter.toCsv(report));
        }

        private void respondCsv(HttpExchange exchange, int status, String body) throws IOException {
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "text/csv; charset=utf-8");
            exchange.sendResponseHeaders(status, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
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
