package banking.persistence.repository;

/**
 * Simple command-line entry point that executes JDBC migrations using the same configuration as
 * the main application. This allows automated deployment pipelines to prepare the schema without
 * booting the API.
 */
public final class DatabaseMigrationCli {

    private DatabaseMigrationCli() {
    }

    public static void main(String[] args) {
        String mode = System.getenv("BANKING_STORAGE_MODE");
        if (mode == null || !mode.equalsIgnoreCase("jdbc")) {
            System.err.println("BANKING_STORAGE_MODE must be set to 'jdbc' to run database migrations.");
            System.exit(1);
        }

        BankRepositoryFactory.getRepository();
        System.out.println("Database migrations completed successfully.");
    }
}
