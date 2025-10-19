package banking.test;

import banking.account.Account;
import banking.persistence.AccountRepository;
import banking.operation.OperationResult;
import banking.persistence.jdbc.JdbcAccountRepository;
import banking.persistence.jdbc.JdbcConnectionProvider;
import banking.persistence.jdbc.MigrationRunner;
import banking.service.Bank;

import java.util.UUID;

public final class JdbcBankIntegrationTest {
    private int passed;
    private int failed;

    public static void main(String[] args) {
        JdbcBankIntegrationTest testSuite = new JdbcBankIntegrationTest();
        testSuite.run();
        testSuite.report();
        if (testSuite.failed > 0) {
            System.exit(1);
        }
    }

    private void run() {
        execute("should persist accounts across sessions", this::shouldPersistAccountsAcrossSessions);
        execute("should handle deposit and withdrawal", this::shouldHandleDepositAndWithdrawal);
        execute("should execute transfers transactionally", this::shouldExecuteTransfersTransactionally);
        execute("should reject overdrafts without mutating state", this::shouldRejectOverdrafts);
    }

    private void execute(String name, Runnable testCase) {
        try {
            testCase.run();
            passed++;
            System.out.println("[PASS] " + name);
        } catch (AssertionError error) {
            failed++;
            System.err.println("[FAIL] " + name + " -> " + error.getMessage());
        } catch (Exception exception) {
            failed++;
            System.err.println("[ERROR] " + name + " -> " + exception.getMessage());
        }
    }

    private void report() {
        System.out.println();
        System.out.println("Integration tests run: " + (passed + failed));
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }

    private void shouldPersistAccountsAcrossSessions() {
        String database = databaseName();
        int accountNumber;
        try (Bank bank = createBank(database)) {
            Account account = bank.createAccount("Persist", "savings", 1000);
            accountNumber = account.getAccountNumber();
        }
        try (Bank bank = createBank(database)) {
            Account reloaded = bank.getAccount(accountNumber);
            assertNotNull(reloaded, "Account should be present after reopening repository");
            assertEquals(1000.0, reloaded.getBalance(), 0.0001, "Balance should be preserved across sessions");
        }
    }

    private void shouldHandleDepositAndWithdrawal() {
        try (Bank bank = createBank(databaseName())) {
            Account account = bank.createAccount("Depositor", "current", 0);
            OperationResult deposit = bank.deposit(account.getAccountNumber(), 500).join();
            assertTrue(deposit.isSuccess(), "Deposit should succeed against JDBC repository");
            OperationResult withdraw = bank.withdraw(account.getAccountNumber(), 120).join();
            assertTrue(withdraw.isSuccess(), "Withdrawal should succeed against JDBC repository");

            Account persisted = bank.getAccount(account.getAccountNumber());
            assertEquals(380.0, persisted.getBalance(), 0.0001, "Balance should reflect persisted operations");
        }
    }

    private void shouldExecuteTransfersTransactionally() {
        try (Bank bank = createBank(databaseName())) {
            Account source = bank.createAccount("Source", "savings", 0);
            Account target = bank.createAccount("Target", "savings", 0);
            bank.deposit(source.getAccountNumber(), 800).join();
            bank.deposit(target.getAccountNumber(), 50).join();

            OperationResult transfer = bank.transfer(source.getAccountNumber(), target.getAccountNumber(), 275).join();
            assertTrue(transfer.isSuccess(), "Transfer should succeed atomically");

            Account persistedSource = bank.getAccount(source.getAccountNumber());
            Account persistedTarget = bank.getAccount(target.getAccountNumber());
            assertEquals(525.0, persistedSource.getBalance(), 0.0001, "Source balance should decrease atomically");
            assertEquals(325.0, persistedTarget.getBalance(), 0.0001, "Target balance should increase atomically");
        }
    }

    private void shouldRejectOverdrafts() {
        try (Bank bank = createBank(databaseName())) {
            Account account = bank.createAccount("Careful", "savings", 0);
            bank.deposit(account.getAccountNumber(), 40).join();

            OperationResult failure = bank.withdraw(account.getAccountNumber(), 500).join();
            assertTrue(!failure.isSuccess(), "Withdrawal beyond balance should fail");

            Account persisted = bank.getAccount(account.getAccountNumber());
            assertEquals(40.0, persisted.getBalance(), 0.0001, "Failed withdrawal should not mutate balance");
        }
    }

    private Bank createBank(String databaseName) {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("H2 driver not available on the classpath", e);
        }
        String url = "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1";
        JdbcConnectionProvider provider = new JdbcConnectionProvider(url, null, null);
        new MigrationRunner(provider, JdbcBankIntegrationTest.class.getClassLoader()).runMigrations();
        AccountRepository repository = new JdbcAccountRepository(provider);
        return new Bank(repository);
    }

    private String databaseName() {
        return "bank_test_" + UUID.randomUUID().toString().replace('-', '_');
    }

    private void assertNotNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    private void assertEquals(double expected, double actual, double delta, String message) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
