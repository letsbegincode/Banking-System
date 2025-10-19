package banking.persistence.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class EnsureStateDirectoryMigration implements Migration {
    @Override
    public String version() {
        return "1";
    }

    @Override
    public String description() {
        return "create or validate the directory used to persist serialized bank state";
    }

    @Override
    public void apply(MigrationContext context) throws IOException {
        Path stateDirectory = context.stateDirectory();
        Files.createDirectories(stateDirectory);
        Path dataPath = context.dataPath();
        if (Files.notExists(dataPath)) {
            System.out.printf("[migrate] State directory %s prepared. Data file %s will be created on shutdown.%n",
                    stateDirectory, dataPath);
        } else {
            System.out.printf("[migrate] State directory %s prepared. Existing data file detected at %s.%n",
                    stateDirectory, dataPath);
        }
    }
}
