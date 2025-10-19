package banking.persistence.jdbc;

import banking.persistence.PersistenceException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class MigrationRunner {
    private static final String MIGRATIONS_PATH = "db/migration";

    private final JdbcConnectionProvider connectionProvider;
    private final ClassLoader classLoader;

    public MigrationRunner(JdbcConnectionProvider connectionProvider, ClassLoader classLoader) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    public void runMigrations() {
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            ensureVersionTable(connection);
            List<Path> migrations = resolveMigrationPaths();
            migrations.sort(Comparator.comparing(Path::getFileName));
            for (Path migration : migrations) {
                String fileName = migration.getFileName().toString();
                String version = extractVersion(fileName);
                if (isApplied(connection, version)) {
                    continue;
                }
                applyMigration(connection, migration, version, extractDescription(fileName));
            }
            connection.commit();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to apply migrations", e);
        }
    }

    private void ensureVersionTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS schema_version (" +
                "version VARCHAR(50) PRIMARY KEY, " +
                "description VARCHAR(255) NOT NULL, " +
                "installed_on TIMESTAMP NOT NULL" +
                ")";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<Path> resolveMigrationPaths() {
        try {
            URL resource = classLoader.getResource(MIGRATIONS_PATH);
            if (resource == null) {
                return List.of();
            }
            Path directory = Path.of(resource.toURI());
            List<Path> paths = new ArrayList<>();
            try (var stream = Files.list(directory)) {
                stream.filter(path -> path.getFileName().toString().startsWith("V"))
                        .forEach(paths::add);
            }
            return paths;
        } catch (URISyntaxException | IOException e) {
            throw new PersistenceException("Unable to resolve migrations", e);
        }
    }

    private boolean isApplied(Connection connection, String version) throws SQLException {
        String sql = "SELECT 1 FROM schema_version WHERE version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void applyMigration(Connection connection, Path migration, String version, String description) {
        try {
            String sql = Files.readString(migration, StandardCharsets.UTF_8);
            for (String statementSql : splitStatements(sql)) {
                if (statementSql.isBlank()) {
                    continue;
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute(statementSql);
                }
            }
            recordVersion(connection, version, description);
        } catch (IOException e) {
            throw new PersistenceException("Failed to read migration " + migration.getFileName(), e);
        } catch (SQLException e) {
            throw new PersistenceException("Failed to execute migration " + migration.getFileName(), e);
        }
    }

    private List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue;
            }
            builder.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                statements.add(builder.toString().trim().replaceAll(";+$", ""));
                builder.setLength(0);
            }
        }
        if (builder.length() > 0) {
            statements.add(builder.toString().trim());
        }
        return statements;
    }

    private void recordVersion(Connection connection, String version, String description) throws SQLException {
        String sql = "INSERT INTO schema_version (version, description, installed_on) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, version);
            statement.setString(2, description);
            statement.setTimestamp(3, java.sql.Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private String extractVersion(String fileName) {
        int start = fileName.indexOf('V') + 1;
        int end = fileName.indexOf("__");
        if (start <= 0 || end <= start) {
            throw new IllegalArgumentException("Invalid migration file name: " + fileName);
        }
        return fileName.substring(start, end);
    }

    private String extractDescription(String fileName) {
        int separator = fileName.indexOf("__");
        int extension = fileName.lastIndexOf('.');
        if (separator < 0 || extension < 0) {
            return fileName;
        }
        return fileName.substring(separator + 2, extension).replace('_', ' ');
    }
}
