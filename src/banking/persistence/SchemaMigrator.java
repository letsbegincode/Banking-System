package banking.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SchemaMigrator {
    private static final String[] MIGRATIONS = new String[] {
            "CREATE TABLE IF NOT EXISTS accounts ("
                    + "account_number INTEGER PRIMARY KEY,"
                    + "holder_name VARCHAR(120) NOT NULL,"
                    + "account_type VARCHAR(30) NOT NULL,"
                    + "balance DOUBLE PRECISION NOT NULL,"
                    + "creation_date VARCHAR(20) NOT NULL"
                    + ")",
            "CREATE TABLE IF NOT EXISTS transactions ("
                    + "transaction_id VARCHAR(64) PRIMARY KEY,"
                    + "account_number INTEGER NOT NULL,"
                    + "transaction_type VARCHAR(40) NOT NULL,"
                    + "amount DOUBLE PRECISION NOT NULL,"
                    + "occurred_at TIMESTAMP NOT NULL,"
                    + "related_account INTEGER,"
                    + "details VARCHAR(255),"
                    + "FOREIGN KEY (account_number) REFERENCES accounts(account_number)"
                    + ")",
            "CREATE TABLE IF NOT EXISTS observers ("
                    + "observer_id INTEGER PRIMARY KEY,"
                    + "observer_name VARCHAR(120) NOT NULL,"
                    + "observer_type VARCHAR(60) NOT NULL,"
                    + "target VARCHAR(255),"
                    + "is_active BOOLEAN NOT NULL DEFAULT TRUE,"
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")" };

    public void migrate(DatabaseConfiguration configuration) {
        if (!configuration.isConfigured()) {
            throw new RepositoryException("Database configuration missing; cannot run migrations");
        }
        try {
            Class.forName(configuration.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new RepositoryException("JDBC driver not found: " + configuration.getDriverClass(), e);
        }
        try (Connection connection = DriverManager.getConnection(configuration.getUrl(),
                configuration.getUsername(), configuration.getPassword())) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : MIGRATIONS) {
                    statement.execute(sql);
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RepositoryException("Database migration failed", e);
        }
    }
}
