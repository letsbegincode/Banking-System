package banking.test;

import banking.persistence.DatabaseConfiguration;
import banking.persistence.InMemoryBankRepository;
import banking.persistence.JdbcBankRepository;
import banking.persistence.PersistenceStatus;
import banking.persistence.RepositoryException;
import banking.persistence.SchemaMigrator;
import banking.service.Bank;

public final class DatabaseTestHarness {
    private final DatabaseConfiguration configuration;

    private DatabaseTestHarness(DatabaseConfiguration configuration) {
        this.configuration = configuration;
    }

    public static DatabaseTestHarness initialize() {
        DatabaseConfiguration configuration = DatabaseConfiguration.fromEnvironment();
        if (!configuration.isConfigured()) {
            return null;
        }
        try {
            SchemaMigrator migrator = new SchemaMigrator();
            migrator.migrate(configuration);
            return new DatabaseTestHarness(configuration);
        } catch (RepositoryException e) {
            System.err.println("[DB] Test harness disabled: " + e.getMessage());
            return null;
        }
    }

    public Bank createBank() {
        try {
            JdbcBankRepository repository = new JdbcBankRepository(configuration);
            repository.clear();
            PersistenceStatus status = repository.getStatus();
            return new Bank(repository, status);
        } catch (RepositoryException e) {
            System.err.println("[DB] Falling back to in-memory test bank: " + e.getMessage());
            return new Bank(new InMemoryBankRepository(),
                    PersistenceStatus.unavailable("jdbc", e.getMessage(), e));
        }
    }
}
