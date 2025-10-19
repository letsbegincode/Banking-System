package banking.persistence;

import banking.snapshot.AccountSnapshot;
import banking.snapshot.BankSnapshot;
import banking.snapshot.TransactionSnapshot;
import banking.snapshot.TransactionType;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public final class SnapshotPersistence {
    private SnapshotPersistence() {
    }

    public static void write(BankSnapshot snapshot, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Properties properties = new Properties();
        encode(snapshot, properties);

        Path directory = parent != null ? parent : path.toAbsolutePath().getParent();
        if (directory == null) {
            directory = Path.of(".");
        }

        Path tempFile = Files.createTempFile(directory, path.getFileName().toString(), ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                try (FileLock lock = channel.lock()) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    try (OutputStreamWriter writer = new OutputStreamWriter(buffer, StandardCharsets.ISO_8859_1)) {
                        properties.store(writer, "Bank snapshot");
                    }
                    byte[] bytes = buffer.toByteArray();
                    channel.truncate(0);
                    channel.position(0);
                    channel.write(ByteBuffer.wrap(bytes));
                    channel.force(true);
                }
            }

            try {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public static Optional<BankSnapshot> read(Path path) throws IOException {
        if (Files.notExists(path)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            try (FileLock lock = channel.lock(0, Long.MAX_VALUE, true)) {
                int size = Math.toIntExact(channel.size());
                ByteBuffer buffer = ByteBuffer.allocate(size);
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) == -1) {
                        break;
                    }
                }
                buffer.flip();
                try (InputStream inputStream = new ByteArrayInputStream(buffer.array(), 0, buffer.limit())) {
                    properties.load(inputStream);
                }
            }
        }

        return Optional.of(decode(properties));
    }

    private static void encode(BankSnapshot snapshot, Properties properties) {
        properties.setProperty("format.version", Integer.toString(snapshot.version()));
        properties.setProperty("account.count", Integer.toString(snapshot.accounts().size()));

        for (int i = 0; i < snapshot.accounts().size(); i++) {
            AccountSnapshot account = snapshot.accounts().get(i);
            String prefix = "account." + i + ".";
            properties.setProperty(prefix + "type", account.accountType());
            properties.setProperty(prefix + "number", Integer.toString(account.accountNumber()));
            properties.setProperty(prefix + "userName", account.userName());
            properties.setProperty(prefix + "balance", Double.toString(account.balance()));
            properties.setProperty(prefix + "creationDate", account.creationDate());

            if (account.minimumBalance() != null) {
                properties.setProperty(prefix + "minimumBalance", Double.toString(account.minimumBalance()));
            }
            if (account.overdraftLimit() != null) {
                properties.setProperty(prefix + "overdraftLimit", Double.toString(account.overdraftLimit()));
            }
            if (account.termMonths() != null) {
                properties.setProperty(prefix + "termMonths", Integer.toString(account.termMonths()));
            }
            if (account.maturityDate() != null) {
                properties.setProperty(prefix + "maturityDate", account.maturityDate());
            }

            properties.setProperty(prefix + "transaction.count", Integer.toString(account.transactions().size()));
            for (int j = 0; j < account.transactions().size(); j++) {
                TransactionSnapshot transaction = account.transactions().get(j);
                String transactionPrefix = prefix + "transaction." + j + ".";
                properties.setProperty(transactionPrefix + "type", transaction.type().name());
                properties.setProperty(transactionPrefix + "amount", Double.toString(transaction.amount()));
                properties.setProperty(transactionPrefix + "timestamp", transaction.timestamp());
                properties.setProperty(transactionPrefix + "id", transaction.transactionId());
                if (transaction.sourceAccount() != null) {
                    properties.setProperty(transactionPrefix + "sourceAccount",
                            Integer.toString(transaction.sourceAccount()));
                }
                if (transaction.targetAccount() != null) {
                    properties.setProperty(transactionPrefix + "targetAccount",
                            Integer.toString(transaction.targetAccount()));
                }
            }
        }
    }

    private static BankSnapshot decode(Properties properties) {
        int version = Integer.parseInt(require(properties, "format.version"));
        int accountCount = Integer.parseInt(properties.getProperty("account.count", "0"));
        List<AccountSnapshot> accounts = new ArrayList<>(accountCount);

        for (int i = 0; i < accountCount; i++) {
            String prefix = "account." + i + ".";
            String accountType = require(properties, prefix + "type");
            int accountNumber = Integer.parseInt(require(properties, prefix + "number"));
            String userName = require(properties, prefix + "userName");
            double balance = Double.parseDouble(require(properties, prefix + "balance"));
            String creationDate = require(properties, prefix + "creationDate");

            Double minimumBalance = parseDouble(properties.getProperty(prefix + "minimumBalance"));
            Double overdraftLimit = parseDouble(properties.getProperty(prefix + "overdraftLimit"));
            Integer termMonths = parseInteger(properties.getProperty(prefix + "termMonths"));
            String maturityDate = properties.getProperty(prefix + "maturityDate");

            int transactionCount = Integer.parseInt(properties.getProperty(prefix + "transaction.count", "0"));
            List<TransactionSnapshot> transactions = new ArrayList<>(transactionCount);
            for (int j = 0; j < transactionCount; j++) {
                String transactionPrefix = prefix + "transaction." + j + ".";
                TransactionType type = TransactionType.valueOf(require(properties, transactionPrefix + "type"));
                double amount = Double.parseDouble(require(properties, transactionPrefix + "amount"));
                String timestamp = require(properties, transactionPrefix + "timestamp");
                String id = require(properties, transactionPrefix + "id");
                Integer sourceAccount = parseInteger(properties.getProperty(transactionPrefix + "sourceAccount"));
                Integer targetAccount = parseInteger(properties.getProperty(transactionPrefix + "targetAccount"));
                transactions.add(new TransactionSnapshot(type, amount, timestamp, id, sourceAccount, targetAccount));
            }

            accounts.add(new AccountSnapshot(accountType, accountNumber, userName, balance, creationDate, minimumBalance,
                    overdraftLimit, termMonths, maturityDate, transactions));
        }

        return new BankSnapshot(version, accounts);
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value;
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.parseDouble(value);
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value);
    }
}
