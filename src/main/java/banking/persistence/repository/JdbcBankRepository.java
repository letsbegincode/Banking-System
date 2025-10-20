package banking.persistence.repository;

import banking.snapshot.AccountSnapshot;
import banking.snapshot.BankSnapshot;
import banking.snapshot.TransactionSnapshot;
import banking.snapshot.TransactionType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC-backed repository that stores accounts and transactions in normalized
 * tables. The
 * implementation performs a full snapshot write on each save to keep the
 * persistence layer simple
 * while still providing durable storage.
 */
public final class JdbcBankRepository implements BankRepository {

    private final DataSource dataSource;

    public JdbcBankRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<BankSnapshot> load() {
        try (Connection connection = dataSource.getConnection()) {
            Map<Integer, AccountSnapshot.Builder> builders = new HashMap<>();

            try (PreparedStatement accounts = connection.prepareStatement(
                    "SELECT account_number, user_name, account_type, balance, creation_date, " +
                            "minimum_balance, overdraft_limit, term_months, maturity_date " +
                            "FROM bank_accounts")) {
                try (ResultSet rs = accounts.executeQuery()) {
                    while (rs.next()) {
                        int number = rs.getInt("account_number");
                        AccountSnapshot.Builder builder = AccountSnapshot.builder()
                                .accountNumber(number)
                                .userName(rs.getString("user_name"))
                                .accountType(rs.getString("account_type"))
                                .balance(rs.getDouble("balance"))
                                .creationDate(toLocalDateTime(rs.getTimestamp("creation_date")).toString())
                                .minimumBalance(nullableDouble(rs, "minimum_balance"))
                                .overdraftLimit(nullableDouble(rs, "overdraft_limit"))
                                .termMonths(nullableInteger(rs, "term_months"))
                                .maturityDate(rs.getString("maturity_date"));
                        builders.put(number, builder);
                    }
                }
            }

            if (builders.isEmpty()) {
                return Optional.empty();
            }

            try (PreparedStatement transactions = connection.prepareStatement(
                    "SELECT account_number, type, amount, occurred_at, transaction_id, source_account, target_account "
                            +
                            "FROM bank_transactions ORDER BY occurred_at ASC, id ASC")) {
                try (ResultSet rs = transactions.executeQuery()) {
                    while (rs.next()) {
                        int accountNumber = rs.getInt("account_number");
                        AccountSnapshot.Builder builder = builders.get(accountNumber);
                        if (builder == null) {
                            continue;
                        }
                        TransactionSnapshot snapshot = new TransactionSnapshot(
                                TransactionType.valueOf(rs.getString("type")),
                                rs.getDouble("amount"),
                                rs.getTimestamp("occurred_at").toInstant().toString(),
                                rs.getString("transaction_id"),
                                (Integer) rs.getObject("source_account"),
                                (Integer) rs.getObject("target_account"));
                        builder.addTransaction(snapshot);
                    }
                }
            }

            List<AccountSnapshot> accounts = new ArrayList<>();
            for (AccountSnapshot.Builder builder : builders.values()) {
                accounts.add(builder.build());
            }

            accounts.sort((a, b) -> Integer.compare(a.accountNumber(), b.accountNumber()));
            return Optional.of(new BankSnapshot(BankSnapshot.CURRENT_VERSION, accounts));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load bank snapshot from database", e);
        }
    }

    @Override
    public void save(BankSnapshot snapshot) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement truncateTransactions = connection.createStatement()) {
                truncateTransactions.executeUpdate("DELETE FROM bank_transactions");
            }
            try (Statement truncateAccounts = connection.createStatement()) {
                truncateAccounts.executeUpdate("DELETE FROM bank_accounts");
            }

            try (PreparedStatement insertAccount = connection.prepareStatement(
                    "INSERT INTO bank_accounts (account_number, user_name, account_type, balance, creation_date, " +
                            "minimum_balance, overdraft_limit, term_months, maturity_date) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (AccountSnapshot account : snapshot.accounts()) {
                    insertAccount.setInt(1, account.accountNumber());
                    insertAccount.setString(2, account.userName());
                    insertAccount.setString(3, account.accountType());
                    insertAccount.setDouble(4, account.balance());

                    // Robust parsing: accept full date-time or date-only (yyyy-MM-dd).
                    LocalDateTime creation = parseCreationDate(account.creationDate());
                    insertAccount.setTimestamp(5, Timestamp.valueOf(creation));

                    if (account.minimumBalance() != null) {
                        insertAccount.setDouble(6, account.minimumBalance());
                    } else {
                        insertAccount.setNull(6, java.sql.Types.DOUBLE);
                    }
                    if (account.overdraftLimit() != null) {
                        insertAccount.setDouble(7, account.overdraftLimit());
                    } else {
                        insertAccount.setNull(7, java.sql.Types.DOUBLE);
                    }
                    if (account.termMonths() != null) {
                        insertAccount.setInt(8, account.termMonths());
                    } else {
                        insertAccount.setNull(8, java.sql.Types.INTEGER);
                    }
                    insertAccount.setString(9, account.maturityDate());
                    insertAccount.addBatch();
                }
                insertAccount.executeBatch();
            }

            try (PreparedStatement insertTransaction = connection.prepareStatement(
                    "INSERT INTO bank_transactions (account_number, type, amount, occurred_at, transaction_id, " +
                            "source_account, target_account) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (AccountSnapshot account : snapshot.accounts()) {
                    for (TransactionSnapshot transaction : account.transactions()) {
                        insertTransaction.setInt(1, account.accountNumber());
                        insertTransaction.setString(2, transaction.type().name());
                        insertTransaction.setDouble(3, transaction.amount());
                        insertTransaction.setTimestamp(4, Timestamp.from(Instant.parse(transaction.timestamp())));
                        insertTransaction.setString(5, transaction.transactionId());
                        if (transaction.sourceAccount() != null) {
                            insertTransaction.setInt(6, transaction.sourceAccount());
                        } else {
                            insertTransaction.setNull(6, java.sql.Types.INTEGER);
                        }
                        if (transaction.targetAccount() != null) {
                            insertTransaction.setInt(7, transaction.targetAccount());
                        } else {
                            insertTransaction.setNull(7, java.sql.Types.INTEGER);
                        }
                        insertTransaction.addBatch();
                    }
                }
                insertTransaction.executeBatch();
            }

            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist bank snapshot to database", e);
        }
    }

    private static LocalDateTime parseCreationDate(String creationDateStr) {
        if (creationDateStr == null || creationDateStr.isBlank()) {
            return LocalDateTime.now();
        }

        String trimmed = creationDateStr.trim();

        try {
            // Account creation dates are typically stored as ISO local dates (yyyy-MM-dd).
            // Parsing them first avoids propagating parse exceptions from
            // LocalDateTime.parse when no time component is present.
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // fall through to other strategies
        }

        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // continue to other strategies below
        }

        // Handle timestamps persisted with a trailing zone designator (e.g. ...Z).
        try {
            return LocalDateTime.ofInstant(Instant.parse(trimmed), ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
            // fall through to error
        }

        throw new IllegalArgumentException("Unsupported creation date format: " + creationDateStr);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }
        return timestamp.toLocalDateTime();
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        if (rs.wasNull()) {
            return null;
        }
        return value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        if (rs.wasNull()) {
            return null;
        }
        return value;
    }
}
