package banking;

import banking.api.BankHttpServer;
import banking.persistence.BankDAO;
import banking.security.AuthenticationService;
import banking.security.AuthorizationService;
import banking.security.CredentialStore;
import banking.security.OperatorCredential;
import banking.security.PasswordHasher;
import banking.security.Role;
import banking.security.TokenService;
import banking.service.Bank;
import banking.ui.ConsoleUI;

import java.time.Duration;
import java.util.Set;
import banking.persistence.AccountRepository;
import banking.persistence.jdbc.JdbcAccountRepository;
import banking.persistence.jdbc.JdbcConnectionProvider;
import banking.persistence.jdbc.MigrationRunner;
import banking.persistence.memory.InMemoryAccountRepository;
import banking.service.Bank;
import banking.ui.ConsoleUI;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BankingApplication {
    private BankingApplication() {
    }

    public static void main(String[] args) {
        Bank bank = BankDAO.loadBank();
        PasswordHasher hasher = new PasswordHasher();
        CredentialStore credentialStore = bootstrapCredentials(hasher);
        TokenService tokenService = new TokenService();
        AuthorizationService authorizationService = new AuthorizationService();
        AuthenticationService authenticationService = new AuthenticationService(
                credentialStore, hasher, tokenService, Duration.ofHours(2));
        BankHttpServer httpServer = new BankHttpServer(bank, 8080, tokenService, authorizationService);
        ConsoleUI ui = new ConsoleUI(bank, authenticationService, tokenService, httpServer);
        ui.start();
    }

    private static CredentialStore bootstrapCredentials(PasswordHasher hasher) {
        CredentialStore store = new CredentialStore();
        store.store(new OperatorCredential("admin", hasher.hash("admin123!"), Set.of(Role.ADMIN)));
        store.store(new OperatorCredential("teller", hasher.hash("teller123!"), Set.of(Role.TELLER)));
        store.store(new OperatorCredential("auditor", hasher.hash("auditor123!"), Set.of(Role.AUDITOR)));
        return store;
        Properties properties = loadProperties();
        String persistence = resolve(properties, "banking.persistence", "BANKING_PERSISTENCE", "memory");
        AccountRepository repository = createRepository(persistence, properties);
        Bank bank = new Bank(repository);
        ConsoleUI ui = new ConsoleUI(bank);
        ui.start();
    }

    private static AccountRepository createRepository(String persistence, Properties properties) {
        if ("jdbc".equalsIgnoreCase(persistence)) {
            String url = resolve(properties, "banking.jdbc.url", "BANKING_JDBC_URL", null);
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("banking.jdbc.url must be provided for JDBC persistence");
            }
            String username = resolve(properties, "banking.jdbc.username", "BANKING_JDBC_USERNAME", "");
            String password = resolve(properties, "banking.jdbc.password", "BANKING_JDBC_PASSWORD", "");
            String driver = resolve(properties, "banking.jdbc.driver", "BANKING_JDBC_DRIVER", "org.h2.Driver");
            try {
                Class.forName(driver);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Unable to load JDBC driver: " + driver, e);
            }
            JdbcConnectionProvider provider = new JdbcConnectionProvider(url, username, password);
            new MigrationRunner(provider, BankingApplication.class.getClassLoader()).runMigrations();
            return new JdbcAccountRepository(provider);
        }
        return new InMemoryAccountRepository();
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = BankingApplication.class.getClassLoader()
                .getResourceAsStream("banking.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException e) {
            System.err.println("Failed to load banking.properties: " + e.getMessage());
        }
        return properties;
    }

    private static String resolve(Properties properties, String propertyKey, String envKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String sysValue = System.getProperty(propertyKey);
        if (sysValue != null && !sysValue.isBlank()) {
            return sysValue;
        }
        String fileValue = properties.getProperty(propertyKey);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue;
        }
        return defaultValue;
    }
}
