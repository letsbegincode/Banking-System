package banking;

<<<<<<< HEAD
import banking.persistence.BankDAO;
import banking.security.AuthService;
import banking.security.CredentialStore;
import banking.security.Role;
import banking.security.TokenService;
=======
import banking.persistence.BankRepository;
import banking.persistence.DatabaseConfiguration;
import banking.persistence.InMemoryBankRepository;
import banking.persistence.JdbcBankRepository;
import banking.persistence.PersistenceStatus;
import banking.persistence.RepositoryException;
import banking.persistence.SchemaMigrator;
>>>>>>> origin/pr/14
import banking.service.Bank;
import banking.ui.ConsoleUI;

import java.time.Duration;

public final class BankingApplication {
    private BankingApplication() {
    }

    public static void main(String[] args) {
<<<<<<< HEAD
        Bank bank = BankDAO.loadBank();
        CredentialStore credentialStore = new CredentialStore();
        credentialStore.register("operator", "operator123", Role.OPERATOR);
        credentialStore.register("customer", "customer123", Role.CUSTOMER);
        AuthService authService = new AuthService(credentialStore, new TokenService(Duration.ofMinutes(30)));
        ConsoleUI ui = new ConsoleUI(bank, authService);
=======
        DatabaseConfiguration configuration = DatabaseConfiguration.fromEnvironment();
        BankRepository repository;
        PersistenceStatus primaryStatus;

        if (configuration.isConfigured()) {
            try {
                SchemaMigrator migrator = new SchemaMigrator();
                migrator.migrate(configuration);
                repository = new JdbcBankRepository(configuration);
                primaryStatus = repository.getStatus();
                System.out.println("Database ready: " + primaryStatus.getMessage());
            } catch (RepositoryException e) {
                System.err.println("Database unavailable, using in-memory storage: " + e.getMessage());
                primaryStatus = PersistenceStatus.unavailable("jdbc", e.getMessage(), e);
                repository = new InMemoryBankRepository();
            }
        } else {
            System.out.println("BANK_DB_URL not provided. Launching with in-memory storage only.");
            primaryStatus = PersistenceStatus.unavailable("jdbc", "Database not configured");
            repository = new InMemoryBankRepository();
        }

        Bank bank = new Bank(repository, primaryStatus);
        ConsoleUI ui = new ConsoleUI(bank);
>>>>>>> origin/pr/14
        ui.start();
    }
}
