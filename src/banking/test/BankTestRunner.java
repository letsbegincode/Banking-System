package banking.test;

import banking.account.Account;
import banking.service.Bank;

import java.util.concurrent.CompletableFuture;

public final class BankTestRunner {
    private int passed;
    private int failed;

    public static void main(String[] args) {
        BankTestRunner runner = new BankTestRunner();
        runner.run();
        runner.report();
        if (runner.failed > 0) {
            System.exit(1);
        }
    }

    private void run() {
        execute("deposit increases balance", this::shouldDepositFunds);
        execute("withdraw reduces balance when allowed", this::shouldWithdrawFunds);
        execute("transfer moves funds between accounts", this::shouldTransferFunds);
        execute("interest applied to savings accounts", this::shouldApplyInterest);
    }

    private void execute(String name, TestCase testCase) {
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
        System.out.println("Tests run: " + (passed + failed));
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }

    private void shouldDepositFunds() {
        Bank bank = new Bank();
        try {
            Account account = bank.createAccount("Alice", "savings", 0);
            CompletableFuture<?> future = bank.deposit(account.getAccountNumber(), 200.0);
            future.join();

            Account updated = bank.getAccount(account.getAccountNumber());
            assertNotNull(updated, "Account should exist after deposit");
            assertEquals(200.0, updated.getBalance(), 0.0001, "Balance should reflect deposit");
        } finally {
            bank.shutdown();
        }
    }

    private void shouldWithdrawFunds() {
        Bank bank = new Bank();
        try {
            Account account = bank.createAccount("Bob", "savings", 0);
            bank.deposit(account.getAccountNumber(), 2000.0).join();
            bank.withdraw(account.getAccountNumber(), 500.0).join();

            Account updated = bank.getAccount(account.getAccountNumber());
            assertNotNull(updated, "Account should exist after withdrawal");
            assertEquals(1500.0, updated.getBalance(), 0.0001, "Balance should reflect withdrawal");
        } finally {
            bank.shutdown();
        }
    }

    private void shouldTransferFunds() {
        Bank bank = new Bank();
        try {
            Account source = bank.createAccount("Charlie", "current", 0);
            Account target = bank.createAccount("Dana", "savings", 0);
            bank.deposit(source.getAccountNumber(), 1000.0).join();

            bank.transfer(source.getAccountNumber(), target.getAccountNumber(), 400.0).join();

            Account updatedSource = bank.getAccount(source.getAccountNumber());
            Account updatedTarget = bank.getAccount(target.getAccountNumber());
            assertNotNull(updatedSource, "Source account should remain available");
            assertNotNull(updatedTarget, "Target account should remain available");
            assertEquals(600.0, updatedSource.getBalance(), 0.0001, "Source balance should decrease");
            assertEquals(400.0, updatedTarget.getBalance(), 0.0001, "Target balance should increase");
        } finally {
            bank.shutdown();
        }
    }

    private void shouldApplyInterest() {
        Bank bank = new Bank();
        try {
            Account savings = bank.createAccount("Eve", "savings", 0);
            bank.deposit(savings.getAccountNumber(), 1200.0).join();

            int processed = bank.addInterestToAllSavingsAccounts();
            Account updated = bank.getAccount(savings.getAccountNumber());
            assertEquals(1, processed, 0.0, "Exactly one savings account should be processed");
            assertNotNull(updated, "Account should remain available");
            assertTrue(updated.getBalance() > 1200.0, "Balance should increase after interest");
        } finally {
            bank.shutdown();
        }
    }

    private static void assertEquals(double expected, double actual, double delta, String message) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    private static void assertNotNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface TestCase {
        void run();
    }
}
