package banking.service;

import banking.account.Account;
import banking.account.AccountFactory;
import banking.account.FixedDepositAccount;
import banking.account.SavingsAccount;
import banking.observer.AccountObserver;
import banking.observer.ConsoleNotifier;
import banking.observer.TransactionLogger;
import banking.operation.AccountOperation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Bank implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<Integer, Account> accounts;
    private transient List<AccountObserver> observers;
    private transient Queue<AccountOperation> operationQueue;
    private transient ExecutorService executorService;

    public Bank() {
        this.accounts = new HashMap<>();
        initializeTransientState();
    }

    public synchronized void addObserver(AccountObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    public synchronized Account createAccount(String userName, String accountType, double initialDeposit) {
        int accountNumber = generateAccountNumber();
        Account account = AccountFactory.createAccount(accountType, userName, accountNumber, initialDeposit);
        accounts.put(accountNumber, account);

        notifyObservers("New " + account.getAccountType() + " account created for " + userName
            + ", Account#: " + accountNumber);
        return account;
    }

    public synchronized boolean closeAccount(int accountNumber) {
        Account account = accounts.remove(accountNumber);
        if (account != null) {
            notifyObservers("Account closed: " + account.getAccountNumber() + " for " + account.getUserName());
            return true;
        }
        return false;
    }

    public synchronized Account getAccount(int accountNumber) {
        return accounts.get(accountNumber);
    }

    public synchronized List<Account> getAllAccounts() {
        return new ArrayList<>(accounts.values());
    }

    public synchronized List<Account> getAccountsByType(String accountType) {
        return accounts.values().stream()
            .filter(a -> a.getAccountType().toLowerCase().contains(accountType.toLowerCase()))
            .collect(Collectors.toList());
    }

    public synchronized List<Account> searchAccounts(String keyword) {
        String lowercaseKeyword = keyword.toLowerCase();
        return accounts.values().stream()
            .filter(a -> a.getUserName().toLowerCase().contains(lowercaseKeyword))
            .collect(Collectors.toList());
    }

    public synchronized void queueOperation(AccountOperation operation) {
        operationQueue.add(operation);
        executePendingOperations();
    }

    public synchronized void executePendingOperations() {
        while (!operationQueue.isEmpty()) {
            AccountOperation operation = operationQueue.poll();
            executorService.submit(() -> {
                boolean result = operation.execute();
                if (result) {
                    notifyObservers("Operation completed: " + operation.getDescription());
                } else {
                    notifyObservers("Operation failed: " + operation.getDescription());
                }
            });
        }
    }

    public synchronized void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public synchronized void addInterestToAllSavingsAccounts() {
        accounts.values().stream()
            .filter(a -> a instanceof SavingsAccount || a instanceof FixedDepositAccount)
            .forEach(Account::addInterest);
        notifyObservers("Monthly interest added to all eligible accounts");
    }

    private void notifyObservers(String message) {
        for (AccountObserver observer : observers) {
            observer.update(message);
        }
    }

    private void initializeTransientState() {
        this.observers = new ArrayList<>();
        this.operationQueue = new ConcurrentLinkedQueue<>();
        this.executorService = Executors.newFixedThreadPool(5);

        addObserver(new ConsoleNotifier());
        addObserver(new TransactionLogger());
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        initializeTransientState();
    }

    private int generateAccountNumber() {
        int accountNumber;
        do {
            accountNumber = 100000 + SECURE_RANDOM.nextInt(900000);
        } while (accounts.containsKey(accountNumber));
        return accountNumber;
    }
}
