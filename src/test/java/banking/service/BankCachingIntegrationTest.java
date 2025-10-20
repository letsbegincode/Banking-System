package banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import banking.account.Account;
import banking.operation.OperationResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BankCachingIntegrationTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("banking.cache.provider");
        System.clearProperty("banking.cache.account.ttlSeconds");
        System.clearProperty("banking.cache.balance.ttlSeconds");
    }

    @Test
    @DisplayName("balance cache reflects updates after deposit operations")
    void balanceCacheUpdatesAfterDeposit() {
        System.setProperty("banking.cache.provider", "memory");

        Bank bank = new Bank();
        Account account = bank.createAccount("Alice", "Savings", 1_000.0);
        int accountNumber = account.getAccountNumber();

        assertEquals(1_000.0, bank.getAccountBalance(accountNumber), 0.001);

        OperationResult result = bank.deposit(accountNumber, 500.0).join();
        assertTrue(result.isSuccess(), () -> "deposit failed: " + result.getMessage());
        bank.awaitPendingOperations();

        assertEquals(1_500.0, bank.getAccountBalance(accountNumber), 0.001);
        bank.close();
    }

    @Test
    @DisplayName("account closure evicts cached entries")
    void closingAccountEvictsCache() {
        System.setProperty("banking.cache.provider", "memory");

        Bank bank = new Bank();
        Account account = bank.createAccount("Bob", "Savings", 2_000.0);
        int accountNumber = account.getAccountNumber();

        assertTrue(bank.closeAccount(accountNumber));

        assertThrows(IllegalArgumentException.class, () -> bank.getAccountBalance(accountNumber));
        bank.close();
    }
}
