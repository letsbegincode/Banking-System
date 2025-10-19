package banking.persistence.migration;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

final class MigrationState {
    private static final String STATE_FILENAME = "banking-migrations.properties";

    private final Path metadataFile;
    private final Set<String> appliedVersions;
    private String currentVersion;
    private String lastDescription;
    private Instant lastUpdated;

    private MigrationState(Path metadataFile, Set<String> appliedVersions, String currentVersion,
            String lastDescription, Instant lastUpdated) {
        this.metadataFile = metadataFile;
        this.appliedVersions = appliedVersions;
        this.currentVersion = currentVersion;
        this.lastDescription = lastDescription;
        this.lastUpdated = lastUpdated;
    }

    static MigrationState load(Path stateDirectory) throws IOException {
        Path metadataFile = stateDirectory.resolve(STATE_FILENAME);
        if (!Files.exists(metadataFile)) {
            return new MigrationState(metadataFile, new LinkedHashSet<>(), "0", "", null);
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(metadataFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        String applied = properties.getProperty("applied", "");
        Set<String> versions = new LinkedHashSet<>();
        if (!applied.isBlank()) {
            String[] tokens = applied.split(",");
            for (String token : tokens) {
                if (!token.isBlank()) {
                    versions.add(token.trim());
                }
            }
        }

        String version = properties.getProperty("version", versions.isEmpty() ? "0" : latest(versions));
        String description = properties.getProperty("last_description", "");
        String updatedAt = properties.getProperty("updated_at");
        Instant updated = updatedAt != null && !updatedAt.isBlank() ? Instant.parse(updatedAt) : null;

        return new MigrationState(metadataFile, versions, version, description, updated);
    }

    private static String latest(Set<String> versions) {
        String latest = "0";
        for (String version : versions) {
            if (version.compareTo(latest) > 0) {
                latest = version;
            }
        }
        return latest;
    }

    Path metadataFile() {
        return metadataFile;
    }

    boolean isApplied(String version) {
        return appliedVersions.contains(version);
    }

    void markApplied(Migration migration) {
        appliedVersions.add(migration.version());
        currentVersion = migration.version();
        lastDescription = migration.description();
        lastUpdated = Instant.now();
    }

    void persist() throws IOException {
        Properties properties = new Properties();
        List<String> orderedVersions = new ArrayList<>(appliedVersions);
        Collections.sort(orderedVersions);
        properties.setProperty("version", currentVersion);
        properties.setProperty("applied", String.join(",", orderedVersions));
        properties.setProperty("last_description", lastDescription == null ? "" : lastDescription);
        if (lastUpdated != null) {
            properties.setProperty("updated_at", lastUpdated.toString());
        }

        Files.createDirectories(metadataFile.getParent());
        try (Writer writer = Files.newBufferedWriter(metadataFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            properties.store(writer, "Banking system migration state");
        }
    }

    String currentVersion() {
        return currentVersion;
    }
}
