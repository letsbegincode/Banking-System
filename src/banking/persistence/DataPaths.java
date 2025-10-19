package banking.persistence;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Shared helper for determining where serialized bank state should be persisted.
 */
public final class DataPaths {
    public static final String LEGACY_FILENAME = "banking_system.ser";
    public static final String DEFAULT_FILENAME = "banking_state.properties";
    private static final String DATA_PATH_PROPERTY = "banking.data.path";

    private DataPaths() {
    }

    /**
     * Resolves the path to the serialized banking state, honoring both the
     * {@code banking.data.path} system property and the {@code BANKING_DATA_PATH}
     * environment variable.
     */
    public static Path resolveDataPath() {
        String override = Optional.ofNullable(System.getProperty(DATA_PATH_PROPERTY))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> Optional.ofNullable(System.getenv("BANKING_DATA_PATH"))
                        .filter(value -> !value.isBlank())
                        .orElse(null));
        if (override == null) {
            return Paths.get(DEFAULT_FILENAME);
        }
        return Paths.get(override);
    }
}
