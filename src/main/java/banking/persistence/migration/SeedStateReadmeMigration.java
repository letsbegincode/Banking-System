package banking.persistence.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class SeedStateReadmeMigration implements Migration {
    @Override
    public String version() {
        return "3";
    }

    @Override
    public String description() {
        return "document the purpose of serialized banking state artefacts";
    }

    @Override
    public void apply(MigrationContext context) throws IOException {
        Path readme = context.stateDirectory().resolve("banking-state-readme.txt");
        if (Files.exists(readme)) {
            return;
        }

        String content = "This directory stores the serialized state for the Banking System demo.\n"
                + "\n"
                + "* `" + context.dataPath().getFileName() + "` holds the latest snapshot persisted during a graceful" +
                " shutdown of the API or console applications.\n"
                + "* The `backups/` folder contains timestamped copies captured automatically the first time migrations" +
                " run against an existing state file.\n"
                + "* `banking-migrations.properties` records which structural upgrades have been applied.\n";

        Files.writeString(readme, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
