package banking.persistence;

import banking.account.Account;
import banking.account.AccountFactory;
import banking.transaction.BaseTransaction;
import banking.transaction.DepositTransaction;
import banking.transaction.InterestTransaction;
import banking.transaction.TransferReceiveTransaction;
import banking.transaction.TransferTransaction;
import banking.transaction.WithdrawalTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class JdbcBankRepository implements BankRepository {
    private final DatabaseConfiguration configuration;
    private final JdbcConnectionPool connectionPool;
    private final PersistenceStatus status;

    public JdbcBankRepository(DatabaseConfiguration configuration) {
        this.configuration = configuration.requireConfigured();
        try {
            Class.forName(configuration.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new RepositoryException("JDBC driver not found: " + configuration.getDriverClass(), e);
        }
        this.connectionPool = new JdbcConnectionPool(configuration);
        this.status = PersistenceStatus.available("jdbc", "Connected to " + configuration.getUrl());
    }

    @Override
    public PersistenceStatus getStatus() {
        return status;
    }

    @Override
    public Map<Integer, Account> loadAccounts() {
        Connection connection = connectionPool.borrow();
        try {
            Map<Integer, AccountRecord> records = new HashMap<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT account_number, holder_name, account_type, balance, creation_date FROM accounts")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int number = rs.getInt("account_number");
                        String holder = rs.getString("holder_name");
                        String type = rs.getString("account_type");
                        double balance = rs.getDouble("balance");
                        String creationDate = rs.getString("creation_date");
                        Account account = createAccountForType(type, holder, number, balance);
                        records.put(number, new AccountRecord(account, balance, creationDate));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT account_number, transaction_id, transaction_type, amount, occurred_at, related_account "
                            + "FROM transactions ORDER BY occurred_at ASC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int number = rs.getInt("account_number");
                        AccountRecord record = records.get(number);
                        if (record == null) {
                            continue;
                        }
                        BaseTransaction transaction = mapTransaction(rs);
                        if (transaction != null) {
                            record.transactions.add(transaction);
                        }
                    }
                }
            }
            connection.commit();
            Map<Integer, Account> accounts = new HashMap<>();
            for (AccountRecord record : records.values()) {
                record.apply();
                accounts.put(record.account.getAccountNumber(), record.account);
            }
            return accounts;
        } catch (SQLException e) {
            rollback(connection);
            throw new RepositoryException("Failed to load accounts", e);
        } finally {
            connectionPool.release(connection);
        }
    }

    @Override
    public void saveAccount(Account account) {
        Objects.requireNonNull(account, "account");
        Connection connection = connectionPool.borrow();
        try {
            upsertAccount(connection, account);
            replaceTransactions(connection, account);
            connection.commit();
        } catch (SQLException e) {
            rollback(connection);
            throw new RepositoryException("Failed to persist account " + account.getAccountNumber(), e);
        } finally {
            connectionPool.release(connection);
        }
    }

    @Override
    public void saveAccounts(Collection<Account> accounts) {
        Connection connection = connectionPool.borrow();
        try {
            for (Account account : accounts) {
                upsertAccount(connection, account);
                replaceTransactions(connection, account);
            }
            connection.commit();
        } catch (SQLException e) {
            rollback(connection);
            throw new RepositoryException("Failed to persist accounts", e);
        } finally {
            connectionPool.release(connection);
        }
    }

    @Override
    public void deleteAccount(int accountNumber) {
        Connection connection = connectionPool.borrow();
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM transactions WHERE account_number = ?")) {
                ps.setInt(1, accountNumber);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM accounts WHERE account_number = ?")) {
                ps.setInt(1, accountNumber);
                ps.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            rollback(connection);
            throw new RepositoryException("Failed to delete account " + accountNumber, e);
        } finally {
            connectionPool.release(connection);
        }
    }

    @Override
    public void clear() {
        Connection connection = connectionPool.borrow();
        try {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM transactions")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM accounts")) {
                ps.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            rollback(connection);
            throw new RepositoryException("Failed to clear database", e);
        } finally {
            connectionPool.release(connection);
        }
    }

    @Override
    public void close() {
        connectionPool.close();
    }

    private void upsertAccount(Connection connection, Account account) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE accounts SET holder_name = ?, account_type = ?, balance = ?, creation_date = ? "
                        + "WHERE account_number = ?")) {
            update.setString(1, account.getUserName());
            update.setString(2, accountStorageType(account));
            update.setDouble(3, account.getBalance());
            update.setString(4, account.getCreationDate());
            update.setInt(5, account.getAccountNumber());
            int updated = update.executeUpdate();
            if (updated == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO accounts(account_number, holder_name, account_type, balance, creation_date) "
                                + "VALUES(?,?,?,?,?)")) {
                    insert.setInt(1, account.getAccountNumber());
                    insert.setString(2, account.getUserName());
                    insert.setString(3, accountStorageType(account));
                    insert.setDouble(4, account.getBalance());
                    insert.setString(5, account.getCreationDate());
                    insert.executeUpdate();
                }
            }
        }
    }

    private void replaceTransactions(Connection connection, Account account) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM transactions WHERE account_number = ?")) {
            delete.setInt(1, account.getAccountNumber());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO transactions(account_number, transaction_id, transaction_type, amount, occurred_at, related_account, details) "
                        + "VALUES(?,?,?,?,?,?,?)")) {
            for (BaseTransaction transaction : account.getTransactions()) {
                insert.setInt(1, account.getAccountNumber());
                insert.setString(2, transaction.getTransactionId());
                insert.setString(3, transaction.getClass().getSimpleName());
                insert.setDouble(4, transaction.getAmount());
                insert.setTimestamp(5, Timestamp.valueOf(transaction.getTimestamp()));
                insert.setObject(6, relatedAccount(transaction));
                insert.setString(7, null);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private Integer relatedAccount(BaseTransaction transaction) {
        if (transaction instanceof TransferTransaction transfer) {
            return transfer.getTargetAccountNumber();
        }
        if (transaction instanceof TransferReceiveTransaction receive) {
            return receive.getSourceAccountNumber();
        }
        return null;
    }

    private String accountStorageType(Account account) {
        return account.getClass().getSimpleName();
    }

    private Account createAccountForType(String storedType, String holder, int accountNumber, double balance) {
        String normalized = storedType == null ? "" : storedType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "savingsaccount", "savings" -> AccountFactory.createAccount("savings", holder, accountNumber, 0);
            case "currentaccount", "current" -> AccountFactory.createAccount("current", holder, accountNumber, 0);
            case "fixeddepositaccount", "fixed", "fd", "fixed deposit (12 months)" -> AccountFactory.createAccount(
                    "fixed", holder, accountNumber, Math.max(5000, balance));
            default -> AccountFactory.createAccount("current", holder, accountNumber, 0);
        };
    }

    private BaseTransaction mapTransaction(ResultSet rs) throws SQLException {
        String type = rs.getString("transaction_type");
        double amount = rs.getDouble("amount");
        String id = rs.getString("transaction_id");
        LocalDateTime timestamp = rs.getTimestamp("occurred_at").toLocalDateTime();
        Integer related = (Integer) rs.getObject("related_account");
        return switch (type) {
            case "DepositTransaction" -> new DepositTransaction(amount, id, timestamp);
            case "WithdrawalTransaction" -> new WithdrawalTransaction(amount, id, timestamp);
            case "TransferTransaction" -> new TransferTransaction(amount,
                    related == null ? 0 : related, id, timestamp);
            case "TransferReceiveTransaction" -> new TransferReceiveTransaction(amount,
                    related == null ? 0 : related, id, timestamp);
            case "InterestTransaction" -> new InterestTransaction(amount, id, timestamp);
            default -> null;
        };
    }

    private static final class AccountRecord {
        private final Account account;
        private final double balance;
        private final String creationDate;
        private final List<BaseTransaction> transactions = new ArrayList<>();

        private AccountRecord(Account account, double balance, String creationDate) {
            this.account = account;
            this.balance = balance;
            this.creationDate = creationDate;
        }

        private void apply() {
            account.restore(balance, transactions, creationDate);
        }
    }

    private void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }
}
