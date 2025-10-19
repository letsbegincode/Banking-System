import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Centralized logging and tracing utilities
class LoggingConfig {
    private static final String LOG_DIRECTORY = "logs";
    private static final String LOG_FILE = LOG_DIRECTORY + "/application.log";
    private static final Logger ROOT_LOGGER = Logger.getLogger("");
    private static boolean configured = false;

    static {
        configure();
    }

    private static synchronized void configure() {
        if (configured) {
            return;
        }

        try {
            File directory = new File(LOG_DIRECTORY);
            if (!directory.exists() && !directory.mkdirs()) {
                System.err.println("Unable to create log directory: " + LOG_DIRECTORY);
            }

            Handler[] handlers = ROOT_LOGGER.getHandlers();
            for (Handler handler : handlers) {
                ROOT_LOGGER.removeHandler(handler);
            }

            Formatter formatter = new StructuredLogFormatter();

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(formatter);
            consoleHandler.setLevel(Level.INFO);

            FileHandler fileHandler = new FileHandler(LOG_FILE, true);
            fileHandler.setFormatter(formatter);
            fileHandler.setLevel(Level.FINE);

            ROOT_LOGGER.setLevel(Level.FINE);
            ROOT_LOGGER.addHandler(consoleHandler);
            ROOT_LOGGER.addHandler(fileHandler);

            configured = true;
        } catch (IOException e) {
            System.err.println("Failed to initialize logging configuration: " + e.getMessage());
        }
    }

    public static Logger getLogger(Class<?> clazz) {
        configure();
        return Logger.getLogger(clazz.getName());
    }
}

class StructuredLogFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append("[")
                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()))
                .append("] ")
                .append(record.getLevel().getName())
                .append(" ")
                .append("trace=")
                .append(TraceContext.getCurrentTraceId().orElse("-"))
                .append(" ")
                .append(record.getLoggerName())
                .append(" - ")
                .append(formatMessage(record))
                .append(System.lineSeparator());
        if (record.getThrown() != null) {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                builder.append(sw).append(System.lineSeparator());
            } catch (Exception ignored) {
                // Swallow formatting exceptions to avoid masking original error
            }
        }
        return builder.toString();
    }
}

class TraceContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    public static Optional<String> getCurrentTraceId() {
        return Optional.ofNullable(TRACE_ID.get());
    }

    public static String ensureTraceId() {
        String traceId = TRACE_ID.get();
        if (traceId == null) {
            traceId = generateTraceId();
            TRACE_ID.set(traceId);
        }
        return traceId;
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static void clear() {
        TRACE_ID.remove();
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString();
    }
}

// Security domain objects and utilities
enum Role {
    ADMIN,
    AUDITOR,
    TELLER,
    USER
}

class UserPrincipal implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String username;
    private final String passwordHash;
    private final Set<Role> roles;

    public UserPrincipal(String username, String passwordHash, Set<Role> roles) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = EnumSet.copyOf(roles);
    }

    public String getUsername() {
        return username;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}

class AuthenticatedUser {
    private final String username;
    private final Set<Role> roles;
    private final long issuedAt;
    private final long expiresAt;

    public AuthenticatedUser(String username, Set<Role> roles, long issuedAt, long expiresAt) {
        this.username = username;
        this.roles = EnumSet.copyOf(roles);
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String getUsername() {
        return username;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}

class JwtPayload {
    public final String subject;
    public final Set<Role> roles;
    public final long issuedAt;
    public final long expiresAt;

    public JwtPayload(String subject, Set<Role> roles, long issuedAt, long expiresAt) {
        this.subject = subject;
        this.roles = roles;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }
}

class JwtUtil {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Logger LOGGER = LoggingConfig.getLogger(JwtUtil.class);

    private static byte[] getSecret() {
        String secret = System.getenv().getOrDefault("BANKING_JWT_SECRET", "change-me-super-secret-key");
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    public static String generateToken(String username, Set<Role> roles, long ttlSeconds) {
        long issuedAt = System.currentTimeMillis() / 1000;
        long expiresAt = issuedAt + ttlSeconds;
        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = String.format(Locale.ROOT,
                "{\"sub\":\"%s\",\"roles\":\"%s\",\"iat\":%d,\"exp\":%d}",
                username,
                roles.stream().map(Role::name).collect(Collectors.joining("|")),
                issuedAt,
                expiresAt);

        String header = ENCODER.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = ENCODER.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = sign(header + "." + payload);
        return header + "." + payload + "." + signature;
    }

    private static String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(getSecret(), HMAC_ALGORITHM));
            byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return ENCODER.encodeToString(signatureBytes);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unable to sign token", e);
            throw new IllegalStateException("Unable to sign token", e);
        }
    }

    public static Optional<JwtPayload> parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            String data = parts[0] + "." + parts[1];
            String expectedSignature = sign(data);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }

            String payloadJson = new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, String> values = parseSimpleJson(payloadJson);
            String subject = values.get("sub");
            String rolesValue = values.getOrDefault("roles", "");
            long issuedAt = Long.parseLong(values.getOrDefault("iat", "0"));
            long expiresAt = Long.parseLong(values.getOrDefault("exp", "0"));
            Set<Role> roles = Arrays.stream(rolesValue.split("\\|"))
                    .filter(s -> !s.isEmpty())
                    .map(Role::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
            return Optional.of(new JwtPayload(subject, roles, issuedAt, expiresAt));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse JWT token", e);
            return Optional.empty();
        }
    }

    private static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> values = new HashMap<>();
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        if (trimmed.isEmpty()) {
            return values;
        }

        String[] tokens = trimmed.split(",");
        for (String token : tokens) {
            String[] kv = token.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                values.put(key, value);
            }
        }
        return values;
    }
}

class AuthService {
    private static final Logger LOGGER = LoggingConfig.getLogger(AuthService.class);
    private static final long DEFAULT_TTL_SECONDS = 60L * 60L; // 1 hour
    private final Map<String, UserPrincipal> users = new ConcurrentHashMap<>();

    public AuthService() {
        seedDefaultUsers();
    }

    public void registerUser(String username, String password, Set<Role> roles) {
        users.put(username.toLowerCase(Locale.ROOT),
                new UserPrincipal(username, hashPassword(password), roles));
        LOGGER.info(() -> "Registered user '" + username + "' with roles " + roles);
    }

    public Optional<String> authenticate(String username, String password) {
        UserPrincipal principal = users.get(username.toLowerCase(Locale.ROOT));
        if (principal == null) {
            return Optional.empty();
        }

        String providedHash = hashPassword(password);
        if (MessageDigest.isEqual(principal.getPasswordHash().getBytes(StandardCharsets.UTF_8),
                providedHash.getBytes(StandardCharsets.UTF_8))) {
            String token = JwtUtil.generateToken(principal.getUsername(), principal.getRoles(), DEFAULT_TTL_SECONDS);
            LOGGER.fine(() -> "Issued token for user '" + username + "'");
            return Optional.of(token);
        }
        return Optional.empty();
    }

    public Optional<AuthenticatedUser> validateToken(String token) {
        Optional<JwtPayload> payloadOpt = JwtUtil.parseToken(token);
        if (!payloadOpt.isPresent()) {
            return Optional.empty();
        }

        JwtPayload payload = payloadOpt.get();
        if (payload.expiresAt < System.currentTimeMillis() / 1000) {
            LOGGER.fine(() -> "Token expired for user '" + payload.subject + "'");
            return Optional.empty();
        }

        UserPrincipal principal = users.get(payload.subject.toLowerCase(Locale.ROOT));
        if (principal == null) {
            return Optional.empty();
        }

        return Optional.of(new AuthenticatedUser(principal.getUsername(), principal.getRoles(),
                payload.issuedAt, payload.expiresAt));
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to hash password", e);
            throw new IllegalStateException("Failed to hash password", e);
        }
    }

    private void seedDefaultUsers() {
        if (!users.isEmpty()) {
            return;
        }

        registerUser("admin", "Admin@123", EnumSet.of(Role.ADMIN, Role.AUDITOR, Role.TELLER, Role.USER));
        registerUser("auditor", "Audit@123", EnumSet.of(Role.AUDITOR));
        registerUser("teller", "Teller@123", EnumSet.of(Role.TELLER));
        registerUser("customer", "Customer@123", EnumSet.of(Role.USER));
    }
}

class AuthMiddleware {
    private static final Logger LOGGER = LoggingConfig.getLogger(AuthMiddleware.class);
    private final AuthService authService;

    public AuthMiddleware(AuthService authService) {
        this.authService = authService;
    }

    public AuthenticatedUser requireRoles(String token, Role... requiredRoles) {
        if (token == null || token.trim().isEmpty()) {
            throw new SecurityException("Missing authentication token");
        }

        Optional<AuthenticatedUser> user = authService.validateToken(token);
        if (!user.isPresent()) {
            throw new SecurityException("Invalid or expired token");
        }

        AuthenticatedUser authenticatedUser = user.get();
        if (requiredRoles == null || requiredRoles.length == 0) {
            return authenticatedUser;
        }

        for (Role role : requiredRoles) {
            if (authenticatedUser.hasRole(role)) {
                return authenticatedUser;
            }
        }

        LOGGER.warning(() -> "Authorization failed for user '" + authenticatedUser.getUsername() + "'");
        throw new SecurityException("Insufficient privileges for requested operation");
    }
}

// Audit trail persistence with masking support
class AuditTrailService {
    private static final Logger LOGGER = LoggingConfig.getLogger(AuditTrailService.class);
    private static final String AUDIT_FILE = "logs/audit_trail.jsonl";

    public AuditTrailService() {
        File directory = new File("logs");
        if (!directory.exists() && !directory.mkdirs()) {
            LOGGER.warning("Unable to create logs directory for audit trail");
        }
    }

    public synchronized void recordEvent(String action, String actor, Map<String, String> metadata) {
        String traceId = TraceContext.getCurrentTraceId().orElseGet(TraceContext::generateTraceId);
        Map<String, String> sanitized = new HashMap<>();
        if (metadata != null) {
            metadata.forEach((key, value) -> sanitized.put(key, maskIfSensitive(key, value)));
        }

        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"timestamp\":\"")
                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()))
                .append("\",");
        builder.append("\"traceId\":\"").append(traceId).append("\",");
        builder.append("\"action\":\"").append(action).append("\",");
        builder.append("\"actor\":\"").append(actor == null ? "anonymous" : actor).append("\",");
        builder.append("\"metadata\":{");

        Iterator<Map.Entry<String, String>> iterator = sanitized.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            builder.append("\"").append(entry.getKey()).append("\":\"")
                    .append(entry.getValue()).append("\"");
            if (iterator.hasNext()) {
                builder.append(",");
            }
        }

        builder.append("}}");

        try (FileWriter writer = new FileWriter(AUDIT_FILE, true)) {
            writer.write(builder.toString());
            writer.write(System.lineSeparator());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to persist audit event", e);
        }
    }

    private String maskIfSensitive(String key, String value) {
        if (value == null) {
            return "";
        }
        String lowered = key.toLowerCase(Locale.ROOT);
        if (lowered.contains("account")) {
            return maskAccountNumber(value);
        }
        if (lowered.contains("user") || lowered.contains("customer")) {
            return maskName(value);
        }
        if (lowered.contains("amount")) {
            return value;
        }
        if (lowered.contains("token")) {
            return value.length() <= 6 ? "***" : value.substring(0, 3) + "***" + value.substring(value.length() - 3);
        }
        return value;
    }

    private String maskAccountNumber(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****" + digits;
        }
        String lastFour = digits.substring(digits.length() - 4);
        return "****" + lastFour;
    }

    private String maskName(String value) {
        if (value.length() <= 2) {
            return "*" + value.charAt(value.length() - 1);
        }
        return value.charAt(0) + value.substring(1, value.length() - 1).replaceAll(".", "*")
                + value.charAt(value.length() - 1);
    }
}

// Secure banking service orchestrating auth, logging, and audit trail
class SecureBankService {
    private static final Logger LOGGER = LoggingConfig.getLogger(SecureBankService.class);
    private final Bank bank;
    private final AuthMiddleware authMiddleware;
    private final AuditTrailService auditTrailService;

    public SecureBankService(Bank bank, AuthMiddleware authMiddleware, AuditTrailService auditTrailService) {
        this.bank = bank;
        this.authMiddleware = authMiddleware;
        this.auditTrailService = auditTrailService;
    }

    public Account createAccount(String token, String userName, String accountType, double initialDeposit) {
        AuthenticatedUser user = authMiddleware.requireRoles(token, Role.ADMIN, Role.TELLER);
        String traceId = TraceContext.ensureTraceId();
        try {
            LOGGER.info(
                    () -> String.format("[%s] User %s creating account for %s", traceId, user.getUsername(), userName));
            Account account = bank.createAccount(userName, accountType, initialDeposit);
            auditTrailService.recordEvent("CREATE_ACCOUNT", user.getUsername(),
                    buildMetadata("accountNumber", String.valueOf(account.getAccountNumber()),
                            "accountType", account.getAccountType()));
            return account;
        } finally {
            TraceContext.clear();
        }
    }

    public boolean closeAccount(String token, int accountNumber) {
        AuthenticatedUser user = authMiddleware.requireRoles(token, Role.ADMIN);
        String traceId = TraceContext.ensureTraceId();
        try {
            LOGGER.info(
                    () -> String.format("[%s] User %s closing account %d", traceId, user.getUsername(), accountNumber));
            boolean result = bank.closeAccount(accountNumber);
            auditTrailService.recordEvent("CLOSE_ACCOUNT", user.getUsername(),
                    buildMetadata("accountNumber", String.valueOf(accountNumber), "result", Boolean.toString(result)));
            return result;
        } finally {
            TraceContext.clear();
        }
    }

    public void processMonthlyInterest(String token) {
        AuthenticatedUser user = authMiddleware.requireRoles(token, Role.ADMIN, Role.AUDITOR);
        String traceId = TraceContext.ensureTraceId();
        try {
            LOGGER.info(() -> String.format("[%s] User %s processing monthly interest", traceId, user.getUsername()));
            bank.addInterestToAllSavingsAccounts();
            auditTrailService.recordEvent("PROCESS_INTEREST", user.getUsername(), Collections.emptyMap());
        } finally {
            TraceContext.clear();
        }
    }

    public List<Account> getAllAccounts(String token) {
        AuthenticatedUser user = authMiddleware.requireRoles(token, Role.ADMIN, Role.AUDITOR, Role.TELLER);
        LOGGER.fine(() -> "User " + user.getUsername() + " retrieving all accounts");
        return bank.getAllAccounts();
    }

    public Account getAccount(String token, int accountNumber) {
        AuthenticatedUser user = authMiddleware.requireRoles(token, Role.ADMIN, Role.AUDITOR, Role.TELLER, Role.USER);
        LOGGER.fine(() -> "User " + user.getUsername() + " fetching account " + accountNumber);
        return bank.getAccount(accountNumber);
    }

    public List<Account> searchAccounts(String token, String keyword, boolean byType) {
        AuthenticatedUser user = authMiddleware.requireRoles(token, Role.ADMIN, Role.AUDITOR, Role.TELLER);
        LOGGER.fine(() -> "User " + user.getUsername() + " searching accounts by " + (byType ? "type" : "name"));
        return byType ? bank.getAccountsByType(keyword) : bank.searchAccounts(keyword);
    }

    public void updateAccountHolderName(String token, int accountNumber, String newName) {
        AuthenticatedUser user = authMiddleware.requireRoles(token, Role.ADMIN, Role.TELLER);
        String traceId = TraceContext.ensureTraceId();
        try {
            LOGGER.info(() -> String.format("[%s] User %s updating account %d holder name", traceId, user.getUsername(),
                    accountNumber));
            Account account = bank.getAccount(accountNumber);
            if (account == null) {
                throw new IllegalArgumentException("Account not found");
            }
            String previousName = account.getUserName();
            account.setUserName(newName);
            auditTrailService.recordEvent("UPDATE_ACCOUNT_NAME", user.getUsername(),
                    buildMetadata("accountNumber", String.valueOf(accountNumber),
                            "previousName", previousName,
                            "newName", newName));
        } finally {
            TraceContext.clear();
        }
    }

    public void queueOperation(String token, AccountOperation operation, String action, Map<String, String> metadata,
            Role... requiredRoles) {
        AuthenticatedUser user = authMiddleware.requireRoles(token, requiredRoles);
        String traceId = TraceContext.ensureTraceId();
        try {
            LOGGER.info(() -> String.format("[%s] User %s queued action %s", traceId, user.getUsername(), action));
            bank.queueOperation(new AccountOperation() {
                @Override
                public boolean execute() {
                    TraceContext.setTraceId(traceId);
                    try {
                        boolean executed = operation.execute();
                        Map<String, String> enrichedMetadata = new HashMap<>(
                                metadata == null ? Collections.emptyMap() : metadata);
                        enrichedMetadata.put("result", executed ? "success" : "failure");
                        auditTrailService.recordEvent(action, user.getUsername(), enrichedMetadata);
                        return executed;
                    } finally {
                        TraceContext.clear();
                    }
                }

                @Override
                public String getDescription() {
                    return operation.getDescription();
                }
            });
        } finally {
            TraceContext.clear();
        }
    }

    private Map<String, String> buildMetadata(String... keyValues) {
        Map<String, String> metadata = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            metadata.put(keyValues[i], keyValues[i + 1]);
        }
        return metadata;
    }
}

// Interface for account operations - improving OOP design with interfaces
interface AccountOperation {
    boolean execute();

    String getDescription();

    Collection<Account> getInvolvedAccounts();
}

// Base Transaction class
abstract class BaseTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    protected final double amount;
    protected final String dateTime;
    protected final String transactionId;

    public BaseTransaction(double amount) {
        this.amount = amount;
        this.dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.transactionId = generateTransactionId();
    }

    private String generateTransactionId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public double getAmount() {
        return amount;
    }

    public String getDateTime() {
        return dateTime;
    }

    public abstract String getType();

    @Override
    public String toString() {
        return String.format("ID: %s, Amount: %.2f, Type: %s, Date and Time: %s",
                transactionId, amount, getType(), dateTime);
    }
}

// Concrete transaction types
class DepositTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;

    public DepositTransaction(double amount) {
        super(amount);
    }

    @Override
    public String getType() {
        return "Deposit";
    }
}

class WithdrawalTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;

    public WithdrawalTransaction(double amount) {
        super(amount);
    }

    @Override
    public String getType() {
        return "Withdrawal";
    }
}

class InterestTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;

    public InterestTransaction(double amount) {
        super(amount);
    }

    @Override
    public String getType() {
        return "Interest Added";
    }
}

class TransferTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;
    private final int targetAccountNumber;

    public TransferTransaction(double amount, int targetAccountNumber) {
        super(amount);
        this.targetAccountNumber = targetAccountNumber;
    }

    public int getTargetAccountNumber() {
        return targetAccountNumber;
    }

    @Override
    public String getType() {
        return "Transfer to Acc#" + targetAccountNumber;
    }
}

class TransferReceiveTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;
    private final int sourceAccountNumber;

    public TransferReceiveTransaction(double amount, int sourceAccountNumber) {
        super(amount);
        this.sourceAccountNumber = sourceAccountNumber;
    }

    @Override
    public String getType() {
        return "Received from Acc#" + sourceAccountNumber;
    }
}

// Abstract account class - improved OOP with abstract classes
abstract class Account implements Serializable {
    private static final long serialVersionUID = 1L;

    protected String userName;
    protected final int accountNumber;
    protected double balance;
    protected final List<BaseTransaction> transactions;
    protected final String creationDate;

    public Account(String userName, int accountNumber) {
        this.userName = userName;
        this.accountNumber = accountNumber;
        this.balance = 0;
        this.transactions = new ArrayList<>();
        this.creationDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public synchronized void deposit(double amount) {
        if (amount > 0) {
            balance += amount;
            transactions.add(new DepositTransaction(amount));
        } else {
            throw new IllegalArgumentException("Invalid deposit amount. Please enter a positive amount.");
        }
    }

    public synchronized boolean withdraw(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive.");
        }

        if (canWithdraw(amount)) {
            balance -= amount;
            transactions.add(new WithdrawalTransaction(amount));
            return true;
        }
        return false;
    }

    // Abstract method for account-specific withdrawal logic
    protected abstract boolean canWithdraw(double amount);

    // Abstract method for adding interest (different for each account type)
    public abstract void addInterest();

    // Abstract method to get account type
    public abstract String getAccountType();

    public synchronized boolean transfer(double amount, Account targetAccount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive.");
        }

        if (canWithdraw(amount)) {
            balance -= amount;
            transactions.add(new TransferTransaction(amount, targetAccount.getAccountNumber()));
            targetAccount.receiveTransfer(amount, this.accountNumber);
            return true;
        }
        return false;
    }

    protected synchronized void receiveTransfer(double amount, int sourceAccountNumber) {
        balance += amount;
        transactions.add(new TransferReceiveTransaction(amount, sourceAccountNumber));
    }

    public List<BaseTransaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    public List<BaseTransaction> getTransactionsByType(String type) {
        return transactions.stream()
                .filter(t -> t.getType().contains(type))
                .collect(Collectors.toList());
    }

    public List<BaseTransaction> getTransactionsByDateRange(String startDate, String endDate) {
        return transactions.stream()
                .filter(t -> t.getDateTime().compareTo(startDate) >= 0 &&
                        t.getDateTime().compareTo(endDate) <= 0)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String.format("User: %s, Account Number: %d, Balance: %.2f, Type: %s, Created: %s",
                userName, accountNumber, balance, getAccountType(), creationDate);
    }

    // Basic getters
    public int getAccountNumber() {
        return accountNumber;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public double getBalance() {
        return balance;
    }
}

// Concrete account implementations
class SavingsAccount extends Account {
    private static final long serialVersionUID = 1L;
    private static final double INTEREST_RATE = 0.04; // 4%
    private double minimumBalance = 1000;

    public SavingsAccount(String userName, int accountNumber) {
        super(userName, accountNumber);
    }

    @Override
    protected boolean canWithdraw(double amount) {
        return balance - amount >= minimumBalance;
    }

    @Override
    public synchronized void addInterest() {
        double interest = balance * INTEREST_RATE / 12; // Monthly interest
        if (interest > 0) {
            balance += interest;
            transactions.add(new InterestTransaction(interest));
        }
    }

    @Override
    public String getAccountType() {
        return "Savings";
    }

    public double getMinimumBalance() {
        return minimumBalance;
    }

    public void setMinimumBalance(double minimumBalance) {
        this.minimumBalance = minimumBalance;
    }
}

class CurrentAccount extends Account {
    private static final long serialVersionUID = 1L;
    private double overdraftLimit = 10000;

    public CurrentAccount(String userName, int accountNumber) {
        super(userName, accountNumber);
    }

    @Override
    protected boolean canWithdraw(double amount) {
        return balance - amount >= -overdraftLimit;
    }

    @Override
    public void addInterest() {
        // Current accounts typically don't earn interest
    }

    @Override
    public String getAccountType() {
        return "Current";
    }

    public double getOverdraftLimit() {
        return overdraftLimit;
    }

    public void setOverdraftLimit(double overdraftLimit) {
        this.overdraftLimit = overdraftLimit;
    }
}

class FixedDepositAccount extends Account {
    private static final long serialVersionUID = 1L;
    private static final double INTEREST_RATE = 0.08; // 8%
    private final LocalDateTime maturityDate;
    private final int termMonths;

    public FixedDepositAccount(String userName, int accountNumber, double initialDeposit, int termMonths) {
        super(userName, accountNumber);
        if (initialDeposit < 5000) {
            throw new IllegalArgumentException("Fixed deposit requires minimum initial deposit of 5000");
        }
        this.balance = initialDeposit;
        this.termMonths = termMonths;
        this.maturityDate = LocalDateTime.now().plusMonths(termMonths);
        transactions.add(new DepositTransaction(initialDeposit));
    }

    @Override
    protected boolean canWithdraw(double amount) {
        // Can only withdraw after maturity date
        return LocalDateTime.now().isAfter(maturityDate) && amount <= balance;
    }

    @Override
    public synchronized void addInterest() {
        double interest = balance * INTEREST_RATE / 12; // Monthly interest
        balance += interest;
        transactions.add(new InterestTransaction(interest));
    }

    @Override
    public String getAccountType() {
        return "Fixed Deposit (" + termMonths + " months)";
    }

    public LocalDateTime getMaturityDate() {
        return maturityDate;
    }

    public String getFormattedMaturityDate() {
        return maturityDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    @Override
    public String toString() {
        return super.toString() + ", Matures: " + getFormattedMaturityDate();
    }
}

// Factory pattern for creating different account types
class AccountFactory {
    public static Account createAccount(String accountType, String userName, int accountNumber, double initialDeposit) {
        Account account;

        switch (accountType.toLowerCase()) {
            case "savings":
                account = new SavingsAccount(userName, accountNumber);
                break;
            case "current":
                account = new CurrentAccount(userName, accountNumber);
                break;
            case "fixed":
            case "fd":
                // Default to 12 months if not specified
                account = new FixedDepositAccount(userName, accountNumber, initialDeposit, 12);
                return account; // Return early as deposit is handled in constructor
            default:
                throw new IllegalArgumentException("Unknown account type: " + accountType);
        }

        if (initialDeposit > 0) {
            account.deposit(initialDeposit);
        }

        return account;
    }
}

// Observer pattern for account notifications
interface AccountObserver {
    void update(String message);
}

class ConsoleNotifier implements AccountObserver {
    @Override
    public void update(String message) {
        System.out.println("NOTIFICATION: " + message);
    }
}

class TransactionLogger implements AccountObserver {
    @Override
    public void update(String message) {
        try (PrintWriter out = new PrintWriter(new FileWriter("transaction_log.txt", true))) {
            out.println(LocalDateTime.now() + ": " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// Command pattern for executing account operations
class DepositOperation implements AccountOperation {
    private final Account account;
    private final double amount;

    public DepositOperation(Account account, double amount) {
        this.account = account;
        this.amount = amount;
    }

    @Override
    public boolean execute() {
        try {
            account.deposit(amount);
            return true;
        } catch (Exception e) {
            System.out.println("Deposit failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Deposit of " + amount + " to account " + account.getAccountNumber();
    }

    @Override
    public Collection<Account> getInvolvedAccounts() {
        return Collections.singletonList(account);
    }
}

class WithdrawOperation implements AccountOperation {
    private final Account account;
    private final double amount;

    public WithdrawOperation(Account account, double amount) {
        this.account = account;
        this.amount = amount;
    }

    @Override
    public boolean execute() {
        try {
            return account.withdraw(amount);
        } catch (Exception e) {
            System.out.println("Withdrawal failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Withdrawal of " + amount + " from account " + account.getAccountNumber();
    }

    @Override
    public Collection<Account> getInvolvedAccounts() {
        return Collections.singletonList(account);
    }
}

class TransferOperation implements AccountOperation {
    private final Account sourceAccount;
    private final Account targetAccount;
    private final double amount;

    public TransferOperation(Account sourceAccount, Account targetAccount, double amount) {
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.amount = amount;
    }

    @Override
    public boolean execute() {
        try {
            return sourceAccount.transfer(amount, targetAccount);
        } catch (Exception e) {
            System.out.println("Transfer failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Transfer of " + amount + " from account " + sourceAccount.getAccountNumber() +
                " to account " + targetAccount.getAccountNumber();
    }

    @Override
    public Collection<Account> getInvolvedAccounts() {
        return Arrays.asList(sourceAccount, targetAccount);
    }
}

// Bank class with enhanced functionality
class Bank implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Logger LOGGER = LoggingConfig.getLogger(Bank.class);

    private final Map<Integer, Account> accounts;
    private final List<AccountObserver> observers;
    private transient CacheProvider cacheProvider;
    private transient MessageBroker messageBroker;

    public Bank() {
        this(new InMemoryCacheProvider(), new InMemoryMessageBroker());
    }

    public Bank(CacheProvider cacheProvider, MessageBroker messageBroker) {
        this.accounts = new HashMap<>();
        this.observers = new ArrayList<>();
        initializeInfrastructure(cacheProvider, messageBroker);

        // Add default observers
        addObserver(new ConsoleNotifier());
        addObserver(new TransactionLogger());
    }

    private void initializeInfrastructure(CacheProvider cacheProvider, MessageBroker messageBroker) {
        this.cacheProvider = cacheProvider != null ? cacheProvider : new InMemoryCacheProvider();
        this.messageBroker = messageBroker != null ? messageBroker : new InMemoryMessageBroker();
        accounts.values().forEach(this::refreshAccountCache);
    }

    private void refreshAccountCache(Account account) {
        if (account != null) {
            cacheProvider.cacheAccount(account);
            cacheProvider.cacheBalance(account.getAccountNumber(), account.getBalance());
        }
    }

    public void addObserver(AccountObserver observer) {
        observers.add(observer);
        LOGGER.fine(() -> "Registered observer " + observer.getClass().getSimpleName());
    }

    private void notifyObservers(String message) {
        for (AccountObserver observer : observers) {
            try {
                observer.update(message);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Observer update failed", e);
            }
        }
    }

    public Account createAccount(String userName, String accountType, double initialDeposit) {
        int accountNumber = generateAccountNumber();
        Account account = AccountFactory.createAccount(accountType, userName, accountNumber, initialDeposit);
        accounts.put(accountNumber, account);

        LOGGER.info(
                () -> String.format("Created %s account %d for %s", account.getAccountType(), accountNumber, userName));
        notifyObservers("New " + account.getAccountType() + " account created for " + userName +
                ", Account#: " + accountNumber);
        return account;
    }

    public boolean closeAccount(int accountNumber) {
        Account account = accounts.remove(accountNumber);
        if (account != null) {
            LOGGER.info(() -> "Closed account " + accountNumber);
            notifyObservers("Account closed: " + account.getAccountNumber() + " for " + account.getUserName());
            return true;
        }
        LOGGER.warning(() -> "Attempt to close non-existent account " + accountNumber);
        return false;
    }

    public Account getAccount(int accountNumber) {
        Optional<Account> cachedAccount = cacheProvider.getAccount(accountNumber);
        if (cachedAccount.isPresent()) {
            return cachedAccount.get();
        }

        Account account = accounts.get(accountNumber);
        refreshAccountCache(account);
        return account;
    }

    public double getAccountBalance(int accountNumber) {
        Optional<Double> cachedBalance = cacheProvider.getBalance(accountNumber);
        if (cachedBalance.isPresent()) {
            return cachedBalance.get();
        }

        Account account = accounts.get(accountNumber);
        if (account == null) {
            throw new NoSuchElementException("Account not found: " + accountNumber);
        }

        double balance = account.getBalance();
        cacheProvider.cacheBalance(accountNumber, balance);
        return balance;
    }

    public List<Account> getAllAccounts() {
        accounts.values().forEach(this::refreshAccountCache);
        return new ArrayList<>(accounts.values());
    }

    public List<Account> getAccountsByType(String accountType) {
        return accounts.values().stream()
                .filter(a -> a.getAccountType().toLowerCase().contains(accountType.toLowerCase()))
                .peek(this::refreshAccountCache)
                .collect(Collectors.toList());
    }

    public List<Account> searchAccounts(String keyword) {
        String lowercaseKeyword = keyword.toLowerCase();
        return accounts.values().stream()
                .filter(a -> a.getUserName().toLowerCase().contains(lowercaseKeyword))
                .peek(this::refreshAccountCache)
                .collect(Collectors.toList());
    }

    public void queueOperation(AccountOperation operation) {
        operationQueue.add(operation);
        LOGGER.fine(() -> "Queued operation: " + operation.getDescription());
        executePendingOperations();
    }

    public void executePendingOperations() {
        List<Future<Boolean>> futures = new ArrayList<>();

        while (!operationQueue.isEmpty()) {
            AccountOperation operation = operationQueue.poll();
            futures.add(executorService.submit(() -> {
                String traceId = TraceContext.ensureTraceId();
                LOGGER.fine(() -> String.format("[%s] Executing operation %s", traceId, operation.getDescription()));
                boolean result = operation.execute();
                if (result) {
                    notifyObservers("Operation completed: " + operation.getDescription());
                } else {
                    notifyObservers("Operation failed: " + operation.getDescription());
                }
                TraceContext.clear();
                return result;
            }));
        }
    }

    public void shutdown() {
        executorService.shutdown();
        LOGGER.info("Bank executor service shutdown initiated");
    }

    public void addInterestToAllSavingsAccounts() {
        accounts.values().stream()
                .filter(a -> a instanceof SavingsAccount || a instanceof FixedDepositAccount)
                .forEach(account -> {
                    account.addInterest();
                    refreshAccountCache(account);
                });
        notifyObservers("Monthly interest added to all eligible accounts");
        LOGGER.info("Processed interest for eligible accounts");
    }

    private int generateAccountNumber() {
        int accountNumber;
        do {
            accountNumber = 100000 + secureRandom.nextInt(900000);
        } while (accounts.containsKey(accountNumber));
        return accountNumber;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initializeInfrastructure(new InMemoryCacheProvider(), new InMemoryMessageBroker());
    }
}

// Data Access Object for persistent storage
class BankDAO {
    private static final String FILENAME = "banking_system.ser";
    private static final Logger LOGGER = LoggingConfig.getLogger(BankDAO.class);

    public static void saveBank(Bank bank) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILENAME))) {
            oos.writeObject(bank);
            LOGGER.info("Banking system data has been saved successfully.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving bank data", e);
        }
    }

    public static Bank loadBank() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILENAME))) {
            Bank bank = (Bank) ois.readObject();
            LOGGER.info("Loaded existing bank data from disk");
            return bank;
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.warning("No existing bank data found or error loading. Creating new bank instance.");
            return new Bank();
        }
    }
}

// Enhanced console UI with ANSI colors
class ConsoleUI {
    private static final Logger LOGGER = LoggingConfig.getLogger(ConsoleUI.class);
    // ANSI color codes
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";

    private static final String ANSI_BG_BLACK = "\u001B[40m";
    private static final String ANSI_BG_RED = "\u001B[41m";
    private static final String ANSI_BG_GREEN = "\u001B[42m";
    private static final String ANSI_BG_YELLOW = "\u001B[43m";
    private static final String ANSI_BG_BLUE = "\u001B[44m";
    private static final String ANSI_BG_PURPLE = "\u001B[45m";
    private static final String ANSI_BG_CYAN = "\u001B[46m";
    private static final String ANSI_BG_WHITE = "\u001B[47m";

    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_ITALIC = "\u001B[3m";
    private static final String ANSI_UNDERLINE = "\u001B[4m";

    private final Scanner scanner;
    private final Bank bank;
    private final SecureBankService secureBankService;
    private final AuthService authService;
    private String activeToken;
    private AuthenticatedUser activeUser;

    public ConsoleUI(Bank bank, SecureBankService secureBankService, AuthService authService) {
        this.scanner = new Scanner(System.in);
        this.bank = bank;
        this.secureBankService = secureBankService;
        this.authService = authService;
    }

    public void start() {
        if (!authenticateUser()) {
            LOGGER.warning("Unable to authenticate user. Exiting application");
            return;
        }
        boolean exit = false;

        displayWelcomeBanner();

        while (!exit) {
            displayMainMenu();
            int choice = getIntInput("Please select an option: ");

            switch (choice) {
                case 1:
                    createAccountMenu();
                    break;
                case 2:
                    accountOperationsMenu();
                    break;
                case 3:
                    displayAllAccounts();
                    break;
                case 4:
                    searchAccounts();
                    break;
                case 5:
                    generateReportsMenu();
                    break;
                case 6:
                    accountManagementMenu();
                    break;
                case 7:
                    exit = true;
                    BankDAO.saveBank(bank);
                    bank.shutdown();
                    System.out.println(ANSI_GREEN + "Thank you for using our banking system. Goodbye!" + ANSI_RESET);
                    break;
                case 8:
                    if (authenticateUser()) {
                        System.out.println(ANSI_GREEN + "Re-authentication successful." + ANSI_RESET);
                    }
                    break;
                default:
                    System.out.println(ANSI_RED + "Invalid option. Please try again." + ANSI_RESET);
            }
        }
    }

    private void displayWelcomeBanner() {
        System.out.println(ANSI_BG_BLUE + ANSI_WHITE + ANSI_BOLD);
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║                                                ║");
        System.out.println("║         WELCOME TO ADVANCED BANKING SYSTEM     ║");
        System.out.println("║                                                ║");
        System.out.println("╚════════════════════════════════════════════════╝" + ANSI_RESET);
        System.out.println();
    }

    private void displayMainMenu() {
        System.out.println(ANSI_PURPLE + ANSI_BOLD + "\n===== MAIN MENU =====" + ANSI_RESET);
        if (activeUser != null) {
            String roles = activeUser.getRoles().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            System.out
                    .println(ANSI_BLUE + "Logged in as: " + activeUser.getUsername() + " [" + roles + "]" + ANSI_RESET);
        }
        System.out.println(ANSI_CYAN + "1. Create New Account");
        System.out.println("2. Account Operations");
        System.out.println("3. View All Accounts");
        System.out.println("4. Search Accounts");
        System.out.println("5. Generate Reports");
        System.out.println("6. Account Management");
        System.out.println("7. Exit");
        System.out.println("8. Re-authenticate" + ANSI_RESET);
    }

    private void createAccountMenu() {
        if (!ensureLoggedIn()) {
            return;
        }
        System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== CREATE NEW ACCOUNT =====" + ANSI_RESET);

        String userName = getStringInput("Enter customer name: ");
        System.out.println(ANSI_CYAN + "Available account types:");
        System.out.println("1. Savings Account");
        System.out.println("2. Current Account");
        System.out.println("3. Fixed Deposit Account" + ANSI_RESET);

        int typeChoice = getIntInput("Select account type (1-3): ");
        String accountType;

        switch (typeChoice) {
            case 1:
                accountType = "savings";
                break;
            case 2:
                accountType = "current";
                break;
            case 3:
                accountType = "fixed";
                break;
            default:
                System.out.println(ANSI_RED + "Invalid choice. Defaulting to Savings Account." + ANSI_RESET);
                accountType = "savings";
        }

        double initialDeposit = getDoubleInput("Enter initial deposit amount: ");

        try {
            Account account = secureBankService.createAccount(activeToken, userName, accountType, initialDeposit);
            System.out.println(ANSI_GREEN + "Account created successfully!" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Account Number: " + account.getAccountNumber() + ANSI_RESET);
            displayAccountDetails(account);
        } catch (SecurityException e) {
            handleSecurityException(e);
        } catch (IllegalArgumentException e) {
            System.out.println(ANSI_RED + "Error creating account: " + e.getMessage() + ANSI_RESET);
            LOGGER.warning(e::getMessage);
        }
    }

    private void accountOperationsMenu() {
        if (!ensureLoggedIn()) {
            return;
        }
        int accountNumber = getIntInput("Enter account number: ");
        Account account;
        try {
            account = secureBankService.getAccount(activeToken, accountNumber);
        } catch (SecurityException e) {
            handleSecurityException(e);
            return;
        }

        if (account == null) {
            System.out.println(ANSI_RED + "Account not found!" + ANSI_RESET);
            return;
        }

        displayAccountDetails(account);

        boolean back = false;
        while (!back) {
            System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== ACCOUNT OPERATIONS =====" + ANSI_RESET);
            System.out.println(ANSI_CYAN + "1. Deposit");
            System.out.println("2. Withdraw");
            System.out.println("3. Transfer");
            System.out.println("4. View Transactions");
            System.out.println("5. Account Statement");
            System.out.println("6. Back to Main Menu" + ANSI_RESET);

            int choice = getIntInput("Select operation: ");

            switch (choice) {
                case 1:
                    performDeposit(account);
                    break;
                case 2:
                    performWithdrawal(account);
                    break;
                case 3:
                    performTransfer(account);
                    break;
                case 4:
                    viewTransactions(account);
                    break;
                case 5:
                    generateAccountStatement(account);
                    break;
                case 6:
                    back = true;
                    break;
                default:
                    System.out.println(ANSI_RED + "Invalid option!" + ANSI_RESET);
            }
        }
    }

    private void performDeposit(Account account) {
        if (!ensureLoggedIn()) {
            return;
        }
        double amount = getDoubleInput("Enter deposit amount: ");
        AccountOperation operation = new DepositOperation(account, amount);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("accountNumber", String.valueOf(account.getAccountNumber()));
        metadata.put("amount", String.format(Locale.ROOT, "%.2f", amount));
        try {
            secureBankService.queueOperation(activeToken, operation, "DEPOSIT", metadata, Role.ADMIN, Role.TELLER);
            System.out.println(ANSI_GREEN + "Deposit operation queued. Processing..." + ANSI_RESET);
            Thread.sleep(500);
        } catch (SecurityException e) {
            handleSecurityException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        displayAccountDetails(account);
    }

    private void performWithdrawal(Account account) {
        if (!ensureLoggedIn()) {
            return;
        }
        double amount = getDoubleInput("Enter withdrawal amount: ");
        AccountOperation operation = new WithdrawOperation(account, amount);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("accountNumber", String.valueOf(account.getAccountNumber()));
        metadata.put("amount", String.format(Locale.ROOT, "%.2f", amount));
        try {
            secureBankService.queueOperation(activeToken, operation, "WITHDRAW", metadata, Role.ADMIN, Role.TELLER);
            System.out.println(ANSI_GREEN + "Withdrawal operation queued. Processing..." + ANSI_RESET);
            Thread.sleep(500);
        } catch (SecurityException e) {
            handleSecurityException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        displayAccountDetails(account);
    }

    private void performTransfer(Account sourceAccount) {
        if (!ensureLoggedIn()) {
            return;
        }
        int targetAccountNumber = getIntInput("Enter target account number: ");
        Account targetAccount;
        try {
            targetAccount = secureBankService.getAccount(activeToken, targetAccountNumber);
        } catch (SecurityException e) {
            handleSecurityException(e);
            return;
        }

        if (targetAccount == null) {
            System.out.println(ANSI_RED + "Target account not found!" + ANSI_RESET);
            return;
        }

        if (sourceAccount.getAccountNumber() == targetAccountNumber) {
            System.out.println(ANSI_RED + "Cannot transfer to the same account!" + ANSI_RESET);
            return;
        }

        double amount = getDoubleInput("Enter transfer amount: ");
        AccountOperation operation = new TransferOperation(sourceAccount, targetAccount, amount);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("sourceAccount", String.valueOf(sourceAccount.getAccountNumber()));
        metadata.put("targetAccount", String.valueOf(targetAccount.getAccountNumber()));
        metadata.put("amount", String.format(Locale.ROOT, "%.2f", amount));
        try {
            secureBankService.queueOperation(activeToken, operation, "TRANSFER", metadata, Role.ADMIN, Role.TELLER);
            System.out.println(ANSI_GREEN + "Transfer operation queued. Processing..." + ANSI_RESET);
            Thread.sleep(500);
        } catch (SecurityException e) {
            handleSecurityException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        displayAccountDetails(sourceAccount);
    }

    private void viewTransactions(Account account) {
        List<BaseTransaction> transactions = account.getTransactions();

        if (transactions.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No transactions found for this account." + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== TRANSACTION HISTORY =====" + ANSI_RESET);
        System.out.println(ANSI_UNDERLINE + "Account Number: " + account.getAccountNumber() + ANSI_RESET);

        System.out.println(ANSI_CYAN +
                String.format("%-10s %-15s %-12s %-25s",
                        "ID", "TYPE", "AMOUNT", "DATE/TIME")
                +
                ANSI_RESET);

        for (BaseTransaction transaction : transactions) {
            String amountStr = String.format("%.2f", transaction.getAmount());
            String colorCode = transaction.getType().contains("Deposit") ||
                    transaction.getType().contains("Interest") ||
                    transaction.getType().contains("Received") ? ANSI_GREEN : ANSI_RED;

            System.out.println(colorCode +
                    String.format("%-10s %-15s %-12s %-25s",
                            transaction.getTransactionId(),
                            transaction.getType(),
                            amountStr,
                            transaction.getDateTime())
                    +
                    ANSI_RESET);
        }
    }

    private void generateAccountStatement(Account account) {
        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== ACCOUNT STATEMENT =====" + ANSI_RESET);

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        System.out.println("Select statement period:");
        System.out.println("1. Last Month");
        System.out.println("2. Last 3 Months");
        System.out.println("3. Last 6 Months");
        System.out.println("4. Custom Period");

        int choice = getIntInput("Enter your choice: ");

        String startDate, endDate;
        endDate = now.format(formatter);

        switch (choice) {
            case 1:
                startDate = now.minusMonths(1).format(formatter);
                break;
            case 2:
                startDate = now.minusMonths(3).format(formatter);
                break;
            case 3:
                startDate = now.minusMonths(6).format(formatter);
                break;
            case 4:
                startDate = getStringInput("Enter start date (yyyy-MM-dd): ");
                endDate = getStringInput("Enter end date (yyyy-MM-dd): ");
                break;
            default:
                System.out.println(ANSI_RED + "Invalid choice. Using last month as default." + ANSI_RESET);
                startDate = now.minusMonths(1).format(formatter);
        }

        List<BaseTransaction> statementTransactions = account.getTransactionsByDateRange(startDate, endDate);

        if (statementTransactions.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No transactions found for the selected period." + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_UNDERLINE + "Statement Period: " + startDate + " to " + endDate + ANSI_RESET);
        System.out.println(ANSI_UNDERLINE + "Account Number: " + account.getAccountNumber() + ANSI_RESET);
        System.out.println(ANSI_UNDERLINE + "Account Holder: " + account.getUserName() + ANSI_RESET);
        System.out.println(ANSI_UNDERLINE + "Account Type: " + account.getAccountType() + ANSI_RESET);

        System.out.println(ANSI_CYAN +
                String.format("%-10s %-15s %-12s %-25s",
                        "ID", "TYPE", "AMOUNT", "DATE/TIME")
                +
                ANSI_RESET);

        double totalDeposits = 0;
        double totalWithdrawals = 0;

        for (BaseTransaction transaction : statementTransactions) {
            String amountStr = String.format("%.2f", transaction.getAmount());
            String colorCode = transaction.getType().contains("Deposit") ||
                    transaction.getType().contains("Interest") ||
                    transaction.getType().contains("Received") ? ANSI_GREEN : ANSI_RED;

            if (colorCode.equals(ANSI_GREEN)) {
                totalDeposits += transaction.getAmount();
            } else {
                totalWithdrawals += transaction.getAmount();
            }

            System.out.println(colorCode +
                    String.format("%-10s %-15s %-12s %-25s",
                            transaction.getTransactionId(),
                            transaction.getType(),
                            amountStr,
                            transaction.getDateTime())
                    +
                    ANSI_RESET);
        }

        System.out.println("\n" + ANSI_BOLD + "Summary:" + ANSI_RESET);
        System.out.println(ANSI_GREEN + "Total Credits: " + String.format("%.2f", totalDeposits) + ANSI_RESET);
        System.out.println(ANSI_RED + "Total Debits: " + String.format("%.2f", totalWithdrawals) + ANSI_RESET);
        double currentBalance = bank.getAccountBalance(account.getAccountNumber());
        System.out.println(ANSI_BOLD + "Current Balance: " + String.format("%.2f", currentBalance) + ANSI_RESET);
    }

    private void displayAllAccounts() {
        if (!ensureLoggedIn()) {
            return;
        }
        List<Account> allAccounts;
        try {
            allAccounts = secureBankService.getAllAccounts(activeToken);
        } catch (SecurityException e) {
            handleSecurityException(e);
            return;
        }

        if (allAccounts.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No accounts found in the system." + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== ALL ACCOUNTS =====" + ANSI_RESET);

        System.out.println(ANSI_CYAN +
                String.format("%-6s %-20s %-15s %-15s %-15s",
                        "ACC#", "NAME", "TYPE", "BALANCE", "CREATED")
                +
                ANSI_RESET);

        for (Account account : allAccounts) {
            double balance = bank.getAccountBalance(account.getAccountNumber());
            System.out.println(String.format("%-6d %-20s %-15s %-15.2f %-15s",
                    account.getAccountNumber(),
                    account.getUserName(),
                    account.getAccountType(),
                    balance,
                    account.creationDate));
        }
    }

    private void searchAccounts() {
        if (!ensureLoggedIn()) {
            return;
        }
        System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== SEARCH ACCOUNTS =====" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "1. Search by Account Type");
        System.out.println("2. Search by Customer Name" + ANSI_RESET);

        int choice = getIntInput("Enter your choice: ");
        List<Account> results;

        try {
            switch (choice) {
                case 1:
                    String accountType = getStringInput("Enter account type to search (savings/current/fixed): ");
                    results = secureBankService.searchAccounts(activeToken, accountType, true);
                    break;
                case 2:
                    String keyword = getStringInput("Enter customer name to search: ");
                    results = secureBankService.searchAccounts(activeToken, keyword, false);
                    break;
                default:
                    System.out.println(ANSI_RED + "Invalid choice!" + ANSI_RESET);
                    return;
            }
        } catch (SecurityException e) {
            handleSecurityException(e);
            return;
        }

        if (results.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No matching accounts found." + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_GREEN + "Found " + results.size() + " matching accounts:" + ANSI_RESET);

        System.out.println(ANSI_CYAN +
                String.format("%-6s %-20s %-15s %-15s %-15s",
                        "ACC#", "NAME", "TYPE", "BALANCE", "CREATED")
                +
                ANSI_RESET);

        for (Account account : results) {
            double balance = bank.getAccountBalance(account.getAccountNumber());
            System.out.println(String.format("%-6d %-20s %-15s %-15.2f %-15s",
                    account.getAccountNumber(),
                    account.getUserName(),
                    account.getAccountType(),
                    balance,
                    account.creationDate));
        }
    }

    private void generateReportsMenu() {
        System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== GENERATE REPORTS =====" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "1. Account Summary Report");
        System.out.println("2. High-Value Accounts Report");
        System.out.println("3. Transaction Volume Report");
        System.out.println("4. Back to Main Menu" + ANSI_RESET);

        int choice = getIntInput("Enter your choice: ");

        switch (choice) {
            case 1:
                generateAccountSummaryReport();
                break;
            case 2:
                generateHighValueAccountsReport();
                break;
            case 3:
                generateTransactionVolumeReport();
                break;
            case 4:
                return;
            default:
                System.out.println(ANSI_RED + "Invalid choice!" + ANSI_RESET);
        }
    }

    private void generateAccountSummaryReport() {
        if (!ensureLoggedIn()) {
            return;
        }
        List<Account> allAccounts;
        try {
            allAccounts = secureBankService.getAllAccounts(activeToken);
        } catch (SecurityException e) {
            handleSecurityException(e);
            return;
        }

        if (allAccounts.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No accounts found in the system." + ANSI_RESET);
            return;
        }

        int totalAccounts = allAccounts.size();
        int savingsAccounts = 0;
        int currentAccounts = 0;
        int fixedDepositAccounts = 0;
        double totalBalance = 0;

        for (Account account : allAccounts) {
            double balance = bank.getAccountBalance(account.getAccountNumber());
            if (account.getAccountType().toLowerCase().contains("savings")) {
                savingsAccounts++;
            } else if (account.getAccountType().toLowerCase().contains("current")) {
                currentAccounts++;
            } else if (account.getAccountType().toLowerCase().contains("fixed")) {
                fixedDepositAccounts++;
            }

            totalBalance += balance;
        }

        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== ACCOUNT SUMMARY REPORT =====" + ANSI_RESET);
        System.out.println(ANSI_BOLD + "Total Accounts: " + ANSI_RESET + totalAccounts);
        System.out.println(ANSI_BOLD + "Savings Accounts: " + ANSI_RESET + savingsAccounts);
        System.out.println(ANSI_BOLD + "Current Accounts: " + ANSI_RESET + currentAccounts);
        System.out.println(ANSI_BOLD + "Fixed Deposit Accounts: " + ANSI_RESET + fixedDepositAccounts);
        System.out.println(ANSI_BOLD + "Total Balance: " + ANSI_RESET + String.format("%.2f", totalBalance));
        System.out.println(
                ANSI_BOLD + "Average Balance: " + ANSI_RESET + String.format("%.2f", totalBalance / totalAccounts));
    }

    private void generateHighValueAccountsReport() {
        if (!ensureLoggedIn()) {
            return;
        }
        double threshold = getDoubleInput("Enter balance threshold for high-value accounts: ");

        List<Account> sourceAccounts;
        try {
            sourceAccounts = secureBankService.getAllAccounts(activeToken);
        } catch (SecurityException e) {
            handleSecurityException(e);
            return;
        }

        List<Account> highValueAccounts = sourceAccounts.stream()
                .filter(a -> a.getBalance() >= threshold)
                .sorted((a1, a2) -> Double.compare(a2.getBalance(), a1.getBalance()))
                .collect(Collectors.toList());

        if (highValueAccounts.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No accounts found with balance >= " + threshold + ANSI_RESET);
            return;
        }

        highValueAccounts.sort((a1, a2) -> Double.compare(
                balances.get(a2.getAccountNumber()),
                balances.get(a1.getAccountNumber())));

        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== HIGH-VALUE ACCOUNTS REPORT =====" + ANSI_RESET);
        System.out.println(
                ANSI_BOLD + "Accounts with balance >= " + threshold + ": " + highValueAccounts.size() + ANSI_RESET);

        System.out.println(ANSI_CYAN +
                String.format("%-6s %-20s %-15s %-15s %-15s",
                        "ACC#", "NAME", "TYPE", "BALANCE", "CREATED")
                +
                ANSI_RESET);

        for (Account account : highValueAccounts) {
            double balance = balances.get(account.getAccountNumber());
            System.out.println(String.format("%-6d %-20s %-15s %-15.2f %-15s",
                    account.getAccountNumber(),
                    account.getUserName(),
                    account.getAccountType(),
                    balance,
                    account.creationDate));
        }
    }

    private void generateTransactionVolumeReport() {
        if (!ensureLoggedIn()) {
            return;
        }
        List<Account> allAccounts;
        try {
            allAccounts = secureBankService.getAllAccounts(activeToken);
        } catch (SecurityException e) {
            handleSecurityException(e);
            return;
        }
        Map<String, Integer> transactionTypeCount = new HashMap<>();

        for (Account account : allAccounts) {
            for (BaseTransaction transaction : account.getTransactions()) {
                String type = transaction.getType();
                transactionTypeCount.put(type, transactionTypeCount.getOrDefault(type, 0) + 1);
            }
        }

        if (transactionTypeCount.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No transactions found in the system." + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== TRANSACTION VOLUME REPORT =====" + ANSI_RESET);

        transactionTypeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    System.out.println(ANSI_BOLD + entry.getKey() + ": " + ANSI_RESET + entry.getValue());
                });
    }

    private void accountManagementMenu() {
        if (!ensureLoggedIn()) {
            return;
        }
        System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== ACCOUNT MANAGEMENT =====" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "1. Update Account Holder Name");
        System.out.println("2. Close Account");
        System.out.println("3. Process Monthly Interest");
        System.out.println("4. Back to Main Menu" + ANSI_RESET);

        int choice = getIntInput("Enter your choice: ");

        switch (choice) {
            case 1:
                updateAccountName();
                break;
            case 2:
                closeAccount();
                break;
            case 3:
                processMonthlyInterest();
                break;
            case 4:
                return;
            default:
                System.out.println(ANSI_RED + "Invalid choice!" + ANSI_RESET);
        }
    }

    private void updateAccountName() {
        if (!ensureLoggedIn()) {
            return;
        }
        int accountNumber = getIntInput("Enter account number: ");
        Account account;
        try {
            account = secureBankService.getAccount(activeToken, accountNumber);
        } catch (SecurityException e) {
            handleSecurityException(e);
            return;
        }

        if (account == null) {
            System.out.println(ANSI_RED + "Account not found!" + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_CYAN + "Current account holder: " + account.getUserName() + ANSI_RESET);
        String newName = getStringInput("Enter new account holder name: ");
        try {
            secureBankService.updateAccountHolderName(activeToken, accountNumber, newName);
            System.out.println(ANSI_GREEN + "Account holder name updated successfully!" + ANSI_RESET);
        } catch (SecurityException e) {
            handleSecurityException(e);
        } catch (IllegalArgumentException e) {
            System.out.println(ANSI_RED + e.getMessage() + ANSI_RESET);
            LOGGER.warning(e::getMessage);
        }
    }

    private void closeAccount() {
        if (!ensureLoggedIn()) {
            return;
        }
        int accountNumber = getIntInput("Enter account number to close: ");
        Account account;
        try {
            account = secureBankService.getAccount(activeToken, accountNumber);
        } catch (SecurityException e) {
            handleSecurityException(e);
            return;
        }

        if (account == null) {
            System.out.println(ANSI_RED + "Account not found!" + ANSI_RESET);
            return;
        }

        displayAccountDetails(account);

        String confirm = getStringInput("Are you sure you want to close this account? (yes/no): ");
        if (confirm.equalsIgnoreCase("yes")) {
            try {
                boolean success = secureBankService.closeAccount(activeToken, accountNumber);
                if (success) {
                    System.out.println(ANSI_GREEN + "Account closed successfully!" + ANSI_RESET);
                } else {
                    System.out.println(ANSI_RED + "Failed to close account!" + ANSI_RESET);
                }
            } catch (SecurityException e) {
                handleSecurityException(e);
            }
        } else {
            System.out.println(ANSI_YELLOW + "Account closure canceled." + ANSI_RESET);
        }
    }

    private void processMonthlyInterest() {
        if (!ensureLoggedIn()) {
            return;
        }
        String confirm = getStringInput("Process monthly interest for all eligible accounts? (yes/no): ");
        if (confirm.equalsIgnoreCase("yes")) {
            try {
                secureBankService.processMonthlyInterest(activeToken);
                System.out.println(ANSI_GREEN + "Monthly interest processed successfully!" + ANSI_RESET);
            } catch (SecurityException e) {
                handleSecurityException(e);
            }
        } else {
            System.out.println(ANSI_YELLOW + "Interest processing canceled." + ANSI_RESET);
        }
    }

    private void displayAccountDetails(Account account) {
        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== ACCOUNT DETAILS =====" + ANSI_RESET);
        System.out.println(ANSI_BOLD + "Account Number: " + ANSI_RESET + account.getAccountNumber());
        System.out.println(ANSI_BOLD + "Account Holder: " + ANSI_RESET + account.getUserName());
        System.out.println(ANSI_BOLD + "Account Type: " + ANSI_RESET + account.getAccountType());
        double balance = bank.getAccountBalance(account.getAccountNumber());
        System.out.println(ANSI_BOLD + "Balance: " + ANSI_RESET + String.format("%.2f", balance));
        System.out.println(ANSI_BOLD + "Creation Date: " + ANSI_RESET + account.creationDate);

        // Display type-specific details
        if (account instanceof SavingsAccount) {
            SavingsAccount savingsAccount = (SavingsAccount) account;
            System.out.println(ANSI_BOLD + "Minimum Balance: " + ANSI_RESET +
                    String.format("%.2f", savingsAccount.getMinimumBalance()));
        } else if (account instanceof CurrentAccount) {
            CurrentAccount currentAccount = (CurrentAccount) account;
            System.out.println(ANSI_BOLD + "Overdraft Limit: " + ANSI_RESET +
                    String.format("%.2f", currentAccount.getOverdraftLimit()));
        } else if (account instanceof FixedDepositAccount) {
            FixedDepositAccount fdAccount = (FixedDepositAccount) account;
            System.out.println(ANSI_BOLD + "Maturity Date: " + ANSI_RESET + fdAccount.getFormattedMaturityDate());
        }
    }

    private boolean authenticateUser() {
        System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== USER AUTHENTICATION =====" + ANSI_RESET);
        int attempts = 0;
        while (attempts < 5) {
            String username = getStringInput("Username (or type 'exit' to quit): ");
            if ("exit".equalsIgnoreCase(username)) {
                return false;
            }

            String password = getStringInput("Password: ");
            Optional<String> tokenOpt = authService.authenticate(username, password);
            if (tokenOpt.isPresent()) {
                this.activeToken = tokenOpt.get();
                this.activeUser = authService.validateToken(activeToken).orElse(null);
                if (activeUser != null) {
                    System.out.println(ANSI_GREEN + "Authentication successful. Welcome, " + activeUser.getUsername()
                            + "!" + ANSI_RESET);
                    LOGGER.info(() -> "User " + activeUser.getUsername() + " authenticated successfully");
                    return true;
                }
            }

            attempts++;
            System.out.println(ANSI_RED + "Invalid credentials. Attempts remaining: " + (5 - attempts) + ANSI_RESET);
        }

        System.out.println(ANSI_RED + "Too many failed attempts. Exiting." + ANSI_RESET);
        return false;
    }

    private boolean ensureLoggedIn() {
        if (activeToken == null) {
            System.out.println(ANSI_YELLOW + "Session expired or not authenticated. Please log in." + ANSI_RESET);
            return authenticateUser();
        }
        return true;
    }

    private void handleSecurityException(SecurityException e) {
        System.out.println(ANSI_RED + "Security violation: " + e.getMessage() + ANSI_RESET);
        LOGGER.warning(e::getMessage);
        activeToken = null;
        activeUser = null;
        if (!authenticateUser()) {
            System.out.println(ANSI_RED + "Unable to continue without authentication." + ANSI_RESET);
        }
    }

    private String getStringInput(String prompt) {
        System.out.print(ANSI_YELLOW + prompt + ANSI_RESET);
        return scanner.nextLine();
    }

    private int getIntInput(String prompt) {
        while (true) {
            try {
                System.out.print(ANSI_YELLOW + prompt + ANSI_RESET);
                return Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println(ANSI_RED + "Please enter a valid number." + ANSI_RESET);
            }
        }
    }

    private double getDoubleInput(String prompt) {
        while (true) {
            try {
                System.out.print(ANSI_YELLOW + prompt + ANSI_RESET);
                return Double.parseDouble(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println(ANSI_RED + "Please enter a valid number." + ANSI_RESET);
            }
        }
    }
}

// Main application class
public class BankingApplication {
    public static void main(String[] args) {
        // Load existing bank data or create new bank
        Bank bank = BankDAO.loadBank();

        // Create and start UI
        AuthService authService = new AuthService();
        AuthMiddleware authMiddleware = new AuthMiddleware(authService);
        AuditTrailService auditTrailService = new AuditTrailService();
        SecureBankService secureBankService = new SecureBankService(bank, authMiddleware, auditTrailService);
        ConsoleUI ui = new ConsoleUI(bank, secureBankService, authService);
        ui.start();
    }
}