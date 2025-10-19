package banking.persistence;

import java.util.Locale;
import java.util.Objects;

/**
 * Connection settings for the JDBC persistence layer.
 */
public final class DatabaseConfiguration {
    private final String url;
    private final String username;
    private final String password;
    private final String driverClass;
    private final int poolSize;
    private final int acquireTimeoutSeconds;

    private DatabaseConfiguration(String url, String username, String password,
            String driverClass, int poolSize, int acquireTimeoutSeconds) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.driverClass = driverClass;
        this.poolSize = poolSize;
        this.acquireTimeoutSeconds = acquireTimeoutSeconds;
    }

    public static DatabaseConfiguration fromEnvironment() {
        String url = System.getenv().getOrDefault("BANK_DB_URL", "").trim();
        String username = System.getenv().getOrDefault("BANK_DB_USER", "");
        String password = System.getenv().getOrDefault("BANK_DB_PASSWORD", "");
        String driver = System.getenv().getOrDefault("BANK_DB_DRIVER", "org.h2.Driver");
        int pool = parseInt(System.getenv().get("BANK_DB_POOL_SIZE"), 5);
        int timeout = parseInt(System.getenv().get("BANK_DB_POOL_TIMEOUT"), 5);
        return new DatabaseConfiguration(url, username, password, driver, pool, timeout);
    }

    public static DatabaseConfiguration of(String url, String username, String password,
            String driverClass, int poolSize, int acquireTimeoutSeconds) {
        return new DatabaseConfiguration(url, username, password, driverClass, poolSize,
                acquireTimeoutSeconds);
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean isConfigured() {
        return url != null && !url.isBlank();
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public int getPoolSize() {
        return Math.max(1, poolSize);
    }

    public int getAcquireTimeoutSeconds() {
        return Math.max(1, acquireTimeoutSeconds);
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "DatabaseConfiguration[url=%s, driver=%s, poolSize=%d, timeout=%ds]",
                url, driverClass, getPoolSize(), getAcquireTimeoutSeconds());
    }

    public DatabaseConfiguration requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Database URL is not configured");
        }
        Objects.requireNonNull(driverClass, "driverClass");
        return this;
    }
}
