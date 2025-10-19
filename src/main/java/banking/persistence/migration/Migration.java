package banking.persistence.migration;

import java.io.IOException;

public interface Migration {
    String version();

    String description();

    void apply(MigrationContext context) throws IOException;
}
