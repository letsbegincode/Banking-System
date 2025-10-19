package banking.test;

import banking.persistence.BankDAO;
import banking.service.Bank;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lightweight verification that the snapshot-based persistence layer can round-trip
 * a populated bank instance.
 */
public final class PersistenceSmokeTest {
    private PersistenceSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path tempDirectory = Files.createTempDirectory("bank-persistence-test");
        Path snapshotPath = tempDirectory.resolve("banking_state.properties");
        System.setProperty("banking.data.path", snapshotPath.toString());
        System.setProperty("banking.storage.mode", "snapshot");

        try {
            Bank bank = new Bank();
            bank.createAccount("Test User", "savings", 2500);
            bank.shutdown();
            BankDAO.saveBank(bank);

            if (!Files.exists(snapshotPath)) {
                throw new IllegalStateException("Snapshot file was not created at " + snapshotPath);
            }

            Bank rehydrated = BankDAO.loadBank();
            if (rehydrated.getAllAccounts().size() != 1) {
                throw new IllegalStateException("Expected one account after reload but found "
                        + rehydrated.getAllAccounts().size());
            }

            if (rehydrated.getAllAccounts().get(0).getBalance() < 2500) {
                throw new IllegalStateException("Persisted balance did not match expected value");
            }

            System.out.println("Persistence smoke test passed (" + snapshotPath + ")");
        } finally {
            System.clearProperty("banking.storage.mode");
            System.clearProperty("banking.data.path");
            try (var paths = Files.walk(tempDirectory)) {
                paths.sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }
}
