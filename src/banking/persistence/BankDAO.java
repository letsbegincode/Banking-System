package banking.persistence;

import banking.service.Bank;
import banking.persistence.repository.BankRepository;
import banking.persistence.repository.BankRepositoryFactory;
import banking.snapshot.BankSnapshot;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static banking.persistence.DataPaths.LEGACY_FILENAME;
import static banking.persistence.DataPaths.resolveDataPath;

public final class BankDAO {
    private BankDAO() {
    }

    public static void saveBank(Bank bank) {
        BankRepository repository = BankRepositoryFactory.getRepository();
        repository.save(bank.snapshot());
    }

    public static Bank loadBank() {
        BankRepository repository = BankRepositoryFactory.getRepository();
        Optional<BankSnapshot> snapshot = repository.load();
        if (snapshot.isPresent()) {
            return Bank.restore(snapshot.get());
        }

        Path path = resolveDataPath();
        Path legacyPath = locateLegacyPath(path);
        if (legacyPath != null) {
            Bank legacy = loadLegacyBank(legacyPath);
            if (legacy != null) {
                System.out.printf("Loaded legacy serialized bank from %s. Persisting to snapshot format at %s.%n",
                        legacyPath.toAbsolutePath(), path.toAbsolutePath());
                saveBank(legacy);
                return legacy;
            }
        }

        System.out.println("No existing bank data found. Creating new bank.");
        return new Bank();
    }

    /**
     * Loads a legacy serialized bank instance from the specified path. Intended for
     * migrations and upgrade tooling.
     */
    public static Bank loadLegacyBank(Path path) {
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(path))) {
            Object object = ois.readObject();
            if (object instanceof Bank bank) {
                return bank;
            }
            System.err.println("Legacy file does not contain bank data: " + path);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading legacy bank data from " + path + ": " + e.getMessage());
        }
        return null;
    }

    private static Path locateLegacyPath(Path snapshotPath) {
        if (Files.exists(snapshotPath) && snapshotPath.getFileName().toString().equals(LEGACY_FILENAME)) {
            return snapshotPath;
        }

        Path parent = snapshotPath.getParent();
        Path candidate = parent == null ? Path.of(LEGACY_FILENAME) : parent.resolve(LEGACY_FILENAME);
        return Files.exists(candidate) ? candidate : null;
    }
}
