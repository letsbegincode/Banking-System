package banking.observer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

public class TransactionLogger implements AccountObserver {
    private final String logFilePath;

    public TransactionLogger() {
        this("transaction_log.txt");
    }

    public TransactionLogger(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    @Override
    public void update(String message) {
        try (PrintWriter out = new PrintWriter(new FileWriter(logFilePath, true))) {
            out.println(LocalDateTime.now() + ": " + message);
        } catch (IOException e) {
            System.err.println("Failed to write to transaction log: " + e.getMessage());
        }
    }
}
