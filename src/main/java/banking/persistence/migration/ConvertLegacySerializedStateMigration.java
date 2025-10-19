package banking.persistence.migration;

import banking.persistence.BankDAO;
import banking.persistence.SnapshotPersistence;
import banking.service.Bank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class ConvertLegacySerializedStateMigration implements Migration {
    @Override
    public String version() {
        return "4";
    }

    @Override
    public String description() {
        return "convert legacy serialized bank data to the snapshot-based persistence format";
    }

    @Override
    public void apply(MigrationContext context) throws IOException {
        Path dataPath = context.dataPath();
        if (Files.exists(dataPath)) {
            return;
        }

        Path legacyPath = context.stateDirectory().resolve(banking.persistence.DataPaths.LEGACY_FILENAME);
        if (!Files.exists(legacyPath)) {
            return;
        }

        Bank bank = BankDAO.loadLegacyBank(legacyPath);
        if (bank == null) {
            System.out.printf("[migrate] Skipping legacy conversion because %s could not be read.%n", legacyPath);
            return;
        }

        SnapshotPersistence.write(bank.snapshot(), dataPath);

        Path backupsDirectory = context.stateDirectory().resolve("backups");
        Files.createDirectories(backupsDirectory);
        String backupName = legacyPath.getFileName() + ".legacy." + System.currentTimeMillis();
        Path archived = backupsDirectory.resolve(backupName);
        Files.move(legacyPath, archived, StandardCopyOption.REPLACE_EXISTING);

        System.out.printf("[migrate] Converted legacy serialized store %s to snapshot format at %s (archived original at %s).%n",
                legacyPath, dataPath, archived);
    }
}
