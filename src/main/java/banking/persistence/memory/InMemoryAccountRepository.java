package banking.persistence.memory;

import banking.account.Account;
import banking.persistence.AccountRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAccountRepository implements AccountRepository {
    private final Map<Integer, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public List<Account> findAllAccounts() {
        List<Account> results = new ArrayList<>();
        for (Account account : accounts.values()) {
            results.add(deepCopy(account));
        }
        return results;
    }

    @Override
    public Account findAccount(int accountNumber) {
        Account account = accounts.get(accountNumber);
        return account == null ? null : deepCopy(account);
    }

    @Override
    public void saveAccount(Account account) {
        saveAccounts(List.of(account));
    }

    @Override
    public void saveAccounts(Collection<Account> accountsToSave) {
        for (Account account : accountsToSave) {
            accounts.put(account.getAccountNumber(), deepCopy(account));
        }
    }

    @Override
    public boolean deleteAccount(int accountNumber) {
        return accounts.remove(accountNumber) != null;
    }

    private Account deepCopy(Account account) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(account);
            oos.flush();
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                return (Account) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to copy account", e);
        }
    }
}
