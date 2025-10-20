package banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import banking.account.Account;
import banking.operation.OperationResult;
import banking.snapshot.BankSnapshot;

import org.junit.jupiter.api.Test;

class BankRestoreIntegrationTest {

    @Test
    void restoredBankSupportsTransfers() {
        Bank original = new Bank();
        Account source = original.createAccount("Source", "Savings", 5_000.0);
        Account target = original.createAccount("Target", "Savings", 1_000.0);

        BankSnapshot snapshot = original.snapshot();
        original.close();

        Bank restored = Bank.restore(snapshot);
        try {
            OperationResult result = restored.transfer(source.getAccountNumber(), target.getAccountNumber(), 500.0).join();
            assertTrue(result.isSuccess(), () -> "transfer failed: " + result.getMessage());

            Account updatedSource = restored.getAccount(source.getAccountNumber());
            Account updatedTarget = restored.getAccount(target.getAccountNumber());

            assertEquals(4_500.0, updatedSource.getBalance(), 0.001);
            assertEquals(1_500.0, updatedTarget.getBalance(), 0.001);
        } finally {
            restored.close();
        }
    }
}
