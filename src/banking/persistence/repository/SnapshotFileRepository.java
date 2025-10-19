package banking.persistence.repository;

import banking.persistence.DataPaths;
import banking.persistence.SnapshotPersistence;
import banking.snapshot.BankSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * File-system based repository that persists bank snapshots to the configured data path.
 */
public final class SnapshotFileRepository implements BankRepository {

    @Override
    public Optional<BankSnapshot> load() {
        Path path = DataPaths.resolveDataPath();
        try {
            return SnapshotPersistence.read(path);
        } catch (IOException e) {
            System.err.println("Error loading bank snapshot from " + path + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(BankSnapshot snapshot) {
        Path path = DataPaths.resolveDataPath();
        try {
            SnapshotPersistence.write(snapshot, path);
            System.out.printf("Bank snapshot persisted to %s%n", path.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error saving bank data: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
