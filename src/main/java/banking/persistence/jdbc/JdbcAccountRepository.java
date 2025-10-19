package banking.persistence.jdbc;

import banking.account.Account;
import banking.persistence.AccountRepository;
import banking.persistence.PersistenceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class JdbcAccountRepository implements AccountRepository {
    private final JdbcConnectionProvider connectionProvider;

    public JdbcAccountRepository(JdbcConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public List<Account> findAllAccounts() {
        String sql = "SELECT account_number, payload FROM accounts ORDER BY account_number";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Account> accounts = new ArrayList<>();
            while (resultSet.next()) {
                accounts.add(deserialize(resultSet.getBytes("payload")));
            }
            return accounts;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load accounts", e);
        }
    }

    @Override
    public Account findAccount(int accountNumber) {
        String sql = "SELECT payload FROM accounts WHERE account_number = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return deserialize(resultSet.getBytes("payload"));
                }
                return null;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load account " + accountNumber, e);
        }
    }

    @Override
    public void saveAccount(Account account) {
        saveAccounts(List.of(account));
    }

    @Override
    public void saveAccounts(Collection<Account> accounts) {
        if (accounts.isEmpty()) {
            return;
        }
        String updateSql = "UPDATE accounts SET account_type = ?, account_holder = ?, creation_date = ?, "
                + "updated_at = ?, payload = ? WHERE account_number = ?";
        String insertSql = "INSERT INTO accounts (account_number, account_type, account_holder, creation_date, updated_at, payload)"
                + " VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement(updateSql);
                 PreparedStatement insert = connection.prepareStatement(insertSql)) {
                Instant now = Instant.now();
                for (Account account : accounts) {
                    byte[] payload = serialize(account);
                    update.setString(1, account.getAccountType());
                    update.setString(2, account.getUserName());
                    update.setString(3, account.getCreationDate());
                    update.setTimestamp(4, java.sql.Timestamp.from(now));
                    update.setBytes(5, payload);
                    update.setInt(6, account.getAccountNumber());
                    int updated = update.executeUpdate();
                    if (updated == 0) {
                        insert.setInt(1, account.getAccountNumber());
                        insert.setString(2, account.getAccountType());
                        insert.setString(3, account.getUserName());
                        insert.setString(4, account.getCreationDate());
                        insert.setTimestamp(5, java.sql.Timestamp.from(now));
                        insert.setBytes(6, payload);
                        insert.executeUpdate();
                    }
                }
                connection.commit();
            } catch (IOException e) {
                connection.rollback();
                throw new PersistenceException("Failed to serialize account", e);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to save accounts", e);
        }
    }

    @Override
    public boolean deleteAccount(int accountNumber) {
        String sql = "DELETE FROM accounts WHERE account_number = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountNumber);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to delete account " + accountNumber, e);
        }
    }

    private byte[] serialize(Account account) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(account);
            oos.flush();
            return baos.toByteArray();
        }
    }

    private Account deserialize(byte[] payload) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(payload);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Account) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new PersistenceException("Failed to deserialize account", e);
        }
    }
}
