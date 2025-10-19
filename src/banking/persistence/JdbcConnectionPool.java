package banking.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class JdbcConnectionPool implements AutoCloseable {
    private final DatabaseConfiguration configuration;
    private final BlockingQueue<Connection> pool;
    private final Set<Connection> allConnections = new HashSet<>();

    public JdbcConnectionPool(DatabaseConfiguration configuration) {
        this.configuration = configuration.requireConfigured();
        this.pool = new LinkedBlockingQueue<>(configuration.getPoolSize());
        initialize();
    }

    private void initialize() {
        for (int i = 0; i < configuration.getPoolSize(); i++) {
            poolConnection();
        }
    }

    private void poolConnection() {
        try {
            Connection connection = createConnection();
            connection.setAutoCommit(false);
            pool.offer(connection);
            allConnections.add(connection);
        } catch (SQLException e) {
            throw new RepositoryException("Unable to create pooled connection", e);
        }
    }

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(configuration.getUrl(),
                configuration.getUsername(), configuration.getPassword());
    }

    public Connection borrow() {
        try {
            Connection connection = pool.poll(configuration.getAcquireTimeoutSeconds(), TimeUnit.SECONDS);
            if (connection == null) {
                throw new RepositoryException("Timed out waiting for JDBC connection");
            }
            if (!isValid(connection)) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
                allConnections.remove(connection);
                poolConnection();
                return borrow();
            }
            return connection;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RepositoryException("Interrupted while waiting for JDBC connection", e);
        }
    }

    private boolean isValid(Connection connection) {
        try {
            return connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    public void release(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            allConnections.remove(connection);
            poolConnection();
            return;
        }
        pool.offer(connection);
    }

    @Override
    public void close() {
        for (Connection connection : allConnections) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
        pool.clear();
        allConnections.clear();
    }
}
