package banking.persistence;

import banking.account.Account;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBankRepository implements BankRepository {
    private final Map<Integer, Account> accounts = new ConcurrentHashMap<>();
    private final PersistenceStatus status =
            PersistenceStatus.available("in-memory", "Ephemeral in-memory repository");

    @Override
    public PersistenceStatus getStatus() {
        return status;
    }

    @Override
    public Map<Integer, Account> loadAccounts() {
        return Collections.unmodifiableMap(accounts);
    }

    @Override
    public void saveAccount(Account account) {
        accounts.put(account.getAccountNumber(), account);
    }

    @Override
    public void saveAccounts(Collection<Account> accounts) {
        for (Account account : accounts) {
            saveAccount(account);
        }
    }

    @Override
    public void deleteAccount(int accountNumber) {
        accounts.remove(accountNumber);
    }

    @Override
    public void clear() {
        accounts.clear();
    }
}
