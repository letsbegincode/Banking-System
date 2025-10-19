package banking.persistence.migration;

import java.nio.file.Path;

public final class MigrationContext {
    private final Path dataPath;
    private final Path stateDirectory;
    private final MigrationState state;

    MigrationContext(Path dataPath, Path stateDirectory, MigrationState state) {
        this.dataPath = dataPath;
        this.stateDirectory = stateDirectory;
        this.state = state;
    }

    public Path dataPath() {
        return dataPath;
    }

    public Path stateDirectory() {
        return stateDirectory;
    }

    public Path metadataFile() {
        return state.metadataFile();
    }

    public MigrationState state() {
        return state;
    }
}
