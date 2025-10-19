package banking.persistence.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.stream.Stream;

final class EnsureBackupsDirectoryMigration implements Migration {
    private static final DateTimeFormatter BACKUP_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyyMMdd'T'HHmmss")
            .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
            .toFormatter(Locale.ROOT);

    @Override
    public String version() {
        return "2";
    }

    @Override
    public String description() {
        return "ensure a backups directory exists and capture an initial snapshot of existing state";
    }

    @Override
    public void apply(MigrationContext context) throws IOException {
        Path backupsDirectory = context.stateDirectory().resolve("backups");
        Files.createDirectories(backupsDirectory);

        Path dataPath = context.dataPath();
        if (Files.exists(dataPath)) {
            boolean hasBackup;
            try (Stream<Path> paths = Files.list(backupsDirectory)) {
                hasBackup = paths
                        .anyMatch(path -> path.getFileName().toString().startsWith(dataPath.getFileName().toString()));
            }
            if (!hasBackup) {
                String timestamp = BACKUP_FORMAT.format(Instant.now().atZone(ZoneOffset.UTC));
                String backupName = dataPath.getFileName() + "." + timestamp + ".bak";
                Path backupPath = backupsDirectory.resolve(backupName);
                Files.copy(dataPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.printf("[migrate] Created first-time backup at %s%n", backupPath);
            }
        }
    }
}
