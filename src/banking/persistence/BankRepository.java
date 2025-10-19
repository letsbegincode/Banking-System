package banking.persistence;

import banking.account.Account;

import java.util.Collection;
import java.util.Map;

public interface BankRepository extends AutoCloseable {
    PersistenceStatus getStatus();

    Map<Integer, Account> loadAccounts();

    void saveAccount(Account account);

    void saveAccounts(Collection<Account> accounts);

    void deleteAccount(int accountNumber);

    void clear();

    @Override
    default void close() {
        // Default no-op
    }
}
