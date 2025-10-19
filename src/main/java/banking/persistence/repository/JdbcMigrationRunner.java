package banking.persistence.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

/**
 * Executes schema migrations against a JDBC data source. The runner stores applied versions in a
 * dedicated {@code bank_schema_migrations} table to guarantee idempotency across deployments.
 */
public final class JdbcMigrationRunner {

    private final DataSource dataSource;
    private final List<MigrationStep> steps;

    public JdbcMigrationRunner(DataSource dataSource, List<MigrationStep> steps) {
        this.dataSource = dataSource;
        this.steps = steps;
    }

    public void runMigrations() {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            ensureMigrationsTable(connection);

            for (MigrationStep step : steps) {
                if (isApplied(connection, step.version())) {
                    continue;
                }
                applyMigration(connection, step);
            }

            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to execute database migrations", e);
        }
    }

    private void ensureMigrationsTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS bank_schema_migrations (" +
                    "version INT PRIMARY KEY, " +
                    "description VARCHAR(255) NOT NULL, " +
                    "applied_at TIMESTAMP NOT NULL) ");
        }
    }

    private boolean isApplied(Connection connection, int version) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM bank_schema_migrations WHERE version = ?")) {
            ps.setInt(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void applyMigration(Connection connection, MigrationStep step) throws SQLException {
        System.out.printf("[migrations] Applying %s%n", step);
        for (String sql : step.statements()) {
            executeStatement(connection, sql);
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO bank_schema_migrations(version, description, applied_at) VALUES (?, ?, ?)")) {
            insert.setInt(1, step.version());
            insert.setString(2, step.description());
            insert.setTimestamp(3, java.sql.Timestamp.from(Instant.now()));
            insert.executeUpdate();
        }
    }

    private void executeStatement(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            if (isIgnorable(sql, e)) {
                System.out.printf("[migrations] Ignoring benign error for '%s': %s%n", sql, e.getMessage());
                return;
            }
            throw e;
        }
    }

    private boolean isIgnorable(String sql, SQLException exception) {
        int errorCode = exception.getErrorCode();
        String state = exception.getSQLState();
        if (sql.contains("CREATE INDEX") || sql.contains("ADD UNIQUE KEY")) {
            // MySQL error code 1061 indicates the index already exists.
            if (errorCode == 1061 || "42S21".equals(state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple record describing a schema change.
     */
    public record MigrationStep(int version, String description, List<String> statements) {
        @Override
        public String toString() {
            return "MigrationStep{" +
                    "version=" + version +
                    ", description='" + description + '\'' +
                    '}';
        }
    }
}
