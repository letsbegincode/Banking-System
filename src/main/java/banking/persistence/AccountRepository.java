package banking.persistence;

import banking.account.Account;

import java.util.Collection;
import java.util.List;

public interface AccountRepository extends AutoCloseable {
    List<Account> findAllAccounts();

    Account findAccount(int accountNumber);

    void saveAccount(Account account);

    void saveAccounts(Collection<Account> accounts);

    boolean deleteAccount(int accountNumber);

    @Override
    default void close() {
        // Default no-op for repositories that do not hold external resources.
    }
}
