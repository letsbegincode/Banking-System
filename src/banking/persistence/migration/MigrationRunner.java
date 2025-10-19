package banking.persistence.migration;

import banking.persistence.DataPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public final class MigrationRunner {
    private static final List<Migration> MIGRATIONS = List.of(
            new EnsureStateDirectoryMigration(),
            new EnsureBackupsDirectoryMigration(),
            new SeedStateReadmeMigration(),
            new ConvertLegacySerializedStateMigration());

    private MigrationRunner() {
    }

    public static void main(String[] args) throws IOException {
        Path dataPath = DataPaths.resolveDataPath().toAbsolutePath();
        Path stateDirectory = dataPath.getParent();
        if (stateDirectory == null) {
            stateDirectory = Path.of(".");
        }

        Files.createDirectories(stateDirectory);

        MigrationState state = MigrationState.load(stateDirectory);
        MigrationContext context = new MigrationContext(dataPath, stateDirectory, state);

        boolean appliedAny = false;
        for (Migration migration : MIGRATIONS) {
            if (state.isApplied(migration.version())) {
                continue;
            }
            System.out.printf("[migrate] Applying %s - %s%n", migration.version(), migration.description());
            migration.apply(context);
            state.markApplied(migration);
            state.persist();
            appliedAny = true;
        }

        if (!appliedAny) {
            System.out.printf("[migrate] No pending migrations. Current version: %s%n", state.currentVersion());
            state.persist();
        } else {
            System.out.printf("[migrate] Migration complete at %s. Current version: %s%n", Instant.now(),
                    state.currentVersion());
        }
    }
}
