package banking.persistence.repository;

import javax.sql.DataSource;
import java.util.Locale;
import java.util.Optional;

/**
 * Factory that determines which {@link BankRepository} implementation should be used for the
 * current runtime based on environment variables. By default the system will use the snapshot
 * filesystem repository for backwards compatibility. Setting {@code BANKING_STORAGE_MODE=jdbc}
 * switches persistence to the MySQL-backed implementation.
 */
public final class BankRepositoryFactory {

    private static volatile BankRepository INSTANCE;

    private BankRepositoryFactory() {
    }

    public static BankRepository getRepository() {
        BankRepository repository = INSTANCE;
        if (repository == null) {
            synchronized (BankRepositoryFactory.class) {
                repository = INSTANCE;
                if (repository == null) {
                    INSTANCE = repository = createRepository();
                }
            }
        }
        return repository;
    }

    private static BankRepository createRepository() {
        String mode = Optional.ofNullable(System.getProperty("banking.storage.mode"))
                .or(() -> Optional.ofNullable(System.getenv("BANKING_STORAGE_MODE")))
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .orElse("snapshot");

        if (mode.equals("jdbc")) {
            return createJdbcRepository();
        }
        return new SnapshotFileRepository();
    }

    private static BankRepository createJdbcRepository() {
        String url = Optional.ofNullable(System.getProperty("banking.jdbc.url"))
                .orElseGet(() -> System.getenv("BANKING_JDBC_URL"));
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("BANKING_JDBC_URL must be provided when BANKING_STORAGE_MODE=jdbc");
        }
        String username = Optional.ofNullable(System.getProperty("banking.db.user"))
                .orElseGet(() -> System.getenv("BANKING_DB_USER"));
        String password = Optional.ofNullable(System.getProperty("banking.db.password"))
                .orElseGet(() -> System.getenv("BANKING_DB_PASSWORD"));

        DataSource dataSource = new DriverManagerDataSource(url, username, password);
        ensureDriverLoaded(url);
        runMigrations(dataSource);
        return new JdbcBankRepository(dataSource);
    }

    private static void ensureDriverLoaded(String url) {
        if (url.startsWith("jdbc:mysql:")) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("MySQL JDBC driver not found on the classpath", e);
            }
        }
    }

    private static void runMigrations(DataSource dataSource) {
        JdbcMigrationRunner runner = new JdbcMigrationRunner(dataSource, java.util.List.of(
                new JdbcMigrationRunner.MigrationStep(1, "create accounts and transactions tables", java.util.List.of(
                        "CREATE TABLE IF NOT EXISTS bank_accounts (" +
                                "account_number INT PRIMARY KEY," +
                                "user_name VARCHAR(255) NOT NULL," +
                                "account_type VARCHAR(64) NOT NULL," +
                                "balance DOUBLE NOT NULL," +
                                "creation_date TIMESTAMP NOT NULL," +
                                "minimum_balance DOUBLE NULL," +
                                "overdraft_limit DOUBLE NULL," +
                                "term_months INT NULL," +
                                "maturity_date VARCHAR(64) NULL" +
                                ")",
                        "CREATE TABLE IF NOT EXISTS bank_transactions (" +
                                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                                "account_number INT NOT NULL," +
                                "type VARCHAR(32) NOT NULL," +
                                "amount DOUBLE NOT NULL," +
                                "occurred_at TIMESTAMP NOT NULL," +
                                "transaction_id VARCHAR(128) NOT NULL," +
                                "source_account INT NULL," +
                                "target_account INT NULL," +
                                "CONSTRAINT fk_account FOREIGN KEY (account_number) REFERENCES bank_accounts(account_number) ON DELETE CASCADE" +
                                ")",
                        "CREATE INDEX idx_transactions_account ON bank_transactions(account_number)",
                        "CREATE INDEX idx_transactions_timestamp ON bank_transactions(occurred_at)")),
                new JdbcMigrationRunner.MigrationStep(2, "add unique constraint to transaction ids", java.util.List.of(
                        "ALTER TABLE bank_transactions ADD UNIQUE KEY uk_transaction_id (transaction_id)"))
        ));
        runner.runMigrations();
    }
}
