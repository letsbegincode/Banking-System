package banking.persistence.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public class JdbcConnectionProvider {
    private final String url;
    private final String username;
    private final String password;

    public JdbcConnectionProvider(String url, String username, String password) {
        this.url = Objects.requireNonNull(url, "url");
        this.username = username;
        this.password = password;
    }

    public Connection getConnection() throws SQLException {
        if (username == null || username.isBlank()) {
            return DriverManager.getConnection(url);
        }
        return DriverManager.getConnection(url, username, password);
    }
}
