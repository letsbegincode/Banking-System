package banking.service;

import banking.account.Account;
import banking.account.AccountFactory;
import banking.account.FixedDepositAccount;
import banking.account.SavingsAccount;
import banking.observer.AccountObserver;
import banking.observer.ConsoleNotifier;
import banking.observer.TransactionLogger;
import banking.operation.AccountOperation;
import banking.operation.DepositOperation;
import banking.operation.OperationResult;
import banking.operation.TransferOperation;
import banking.operation.WithdrawOperation;
import banking.persistence.AccountRepository;
import banking.persistence.PersistenceException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class Bank implements AutoCloseable {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<Integer, Account> accounts;
    private final AccountRepository repository;
    private final Map<Integer, ReentrantLock> accountLocks;
    private transient List<AccountObserver> observers;
    private transient Queue<QueuedOperation> operationQueue;
    private transient ExecutorService executorService;
    private transient List<CompletableFuture<OperationResult>> pendingOperations;

    public Bank(AccountRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accounts = new HashMap<>();
        this.accountLocks = new ConcurrentHashMap<>();
        initializeTransientState();
        loadAccountsFromRepository();
    }

    private void loadAccountsFromRepository() {
        for (Account account : repository.findAllAccounts()) {
            accounts.put(account.getAccountNumber(), account);
            accountLocks.putIfAbsent(account.getAccountNumber(), new ReentrantLock());
        }
    }

    public synchronized void addObserver(AccountObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    public synchronized Account createAccount(String userName, String accountType, double initialDeposit) {
        int accountNumber = generateAccountNumber();
        Account account = AccountFactory.createAccount(accountType, userName, accountNumber, initialDeposit);
        accounts.put(accountNumber, account);
        accountLocks.putIfAbsent(accountNumber, new ReentrantLock());

        try {
            repository.saveAccount(account);
            notifyObservers("New " + account.getAccountType() + " account created for " + userName
                    + ", Account#: " + accountNumber);
            return account;
        } catch (PersistenceException e) {
            accounts.remove(accountNumber);
            accountLocks.remove(accountNumber);
            throw e;
        }
    }

    public synchronized boolean closeAccount(int accountNumber) {
        Account account = accounts.remove(accountNumber);
        if (account != null) {
            try {
                boolean deleted = repository.deleteAccount(accountNumber);
                if (!deleted) {
                    accounts.put(accountNumber, account);
                    return false;
                }
                accountLocks.remove(accountNumber);
                notifyObservers("Account closed: " + account.getAccountNumber() + " for " + account.getUserName());
                return true;
            } catch (PersistenceException e) {
                accounts.put(accountNumber, account);
                throw e;
            }
        }
        return false;
    }

    public synchronized boolean updateAccountHolderName(int accountNumber, String newName) {
        Account account = accounts.get(accountNumber);
        if (account == null) {
            return false;
        }

        account.setUserName(newName);
        try {
            repository.saveAccount(account);
            notifyObservers("Account holder updated for account " + accountNumber + " to " + account.getUserName());
            return true;
        } catch (PersistenceException e) {
            throw e;
        }
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

    public synchronized CompletableFuture<OperationResult> deposit(int accountNumber, double amount) {
        if (!accounts.containsKey(accountNumber)) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Account not found: " + accountNumber));
        }
        return queueOperation(new DepositOperation(repository, accountNumber, amount));
    }

    public synchronized CompletableFuture<OperationResult> withdraw(int accountNumber, double amount) {
        if (!accounts.containsKey(accountNumber)) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Account not found: " + accountNumber));
        }
        return queueOperation(new WithdrawOperation(repository, accountNumber, amount));
    }

    public synchronized CompletableFuture<OperationResult> transfer(int sourceAccountNumber, int targetAccountNumber,
            double amount) {
        if (!accounts.containsKey(sourceAccountNumber)) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Source account not found: " + sourceAccountNumber));
        }
        if (!accounts.containsKey(targetAccountNumber)) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Target account not found: " + targetAccountNumber));
        }
        try {
            return queueOperation(new TransferOperation(repository, sourceAccountNumber, targetAccountNumber, amount));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(OperationResult.failure(e.getMessage()));
        }
    }

    public synchronized CompletableFuture<OperationResult> queueOperation(AccountOperation operation) {
        Objects.requireNonNull(operation, "operation");
        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        pendingOperations.add(future);
        operationQueue.add(new QueuedOperation(operation, future));
        executePendingOperations();
        return future;
    }

    public synchronized void executePendingOperations() {
        while (!operationQueue.isEmpty()) {
            QueuedOperation queued = operationQueue.poll();
            if (queued == null) {
                continue;
            }
            try {
                executorService.submit(() -> runOperation(queued));
            } catch (RejectedExecutionException e) {
                OperationResult result = OperationResult.failure(
                        "Operation rejected during shutdown: " + queued.operation().getDescription());
                notifyObservers("FAILED: " + result.getMessage());
                queued.future().complete(result);
                pendingOperations.remove(queued.future());
            }
        }
    }

    public void shutdown() {
        awaitPendingOperations();
        ExecutorService executor;
        synchronized (this) {
            executor = this.executorService;
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        try {
            repository.close();
        } catch (Exception e) {
            System.err.println("Failed to close repository: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    public synchronized int addInterestToAllSavingsAccounts() {
        List<Account> updatedAccounts = new ArrayList<>();
        for (Account account : accounts.values()) {
            if (account instanceof SavingsAccount || account instanceof FixedDepositAccount) {
                account.addInterest();
                updatedAccounts.add(account);
            }
        }

        if (!updatedAccounts.isEmpty()) {
            repository.saveAccounts(updatedAccounts);
            notifyObservers("Monthly interest added to " + updatedAccounts.size() + " eligible accounts");
        } else {
            notifyObservers("No eligible accounts found for monthly interest processing");
        }

        return updatedAccounts.size();
    }

    private void runOperation(QueuedOperation queued) {
        AccountOperation operation = queued.operation();
        List<ReentrantLock> locks = acquireLocks(operation);
        OperationResult result;
        try {
            result = operation.execute();
            if (result.isSuccess()) {
                updateAccounts(operation.getAffectedAccounts());
            }
        } catch (Exception e) {
            result = OperationResult.failure("Unexpected error executing operation: " + e.getMessage());
        } finally {
            releaseLocks(locks);
        }

        notifyObservers(result.isSuccess()
                ? "SUCCESS: " + result.getMessage()
                : "FAILED: " + result.getMessage());
        try {
            queued.future().complete(result);
        } finally {
            pendingOperations.remove(queued.future());
        }
    }

    private List<ReentrantLock> acquireLocks(AccountOperation operation) {
        List<Integer> accountNumbers = operation.getInvolvedAccountNumbers();
        if (accountNumbers.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> sorted = new ArrayList<>(accountNumbers.stream().distinct().collect(Collectors.toList()));
        Collections.sort(sorted);
        List<ReentrantLock> locks = new ArrayList<>(sorted.size());
        for (Integer number : sorted) {
            ReentrantLock lock = accountLocks.computeIfAbsent(number, key -> new ReentrantLock());
            lock.lock();
            locks.add(lock);
        }
        return locks;
    }

    private void releaseLocks(List<ReentrantLock> locks) {
        for (ReentrantLock lock : locks) {
            lock.unlock();
        }
    }

    private void updateAccounts(List<Account> updatedAccounts) {
        if (updatedAccounts == null || updatedAccounts.isEmpty()) {
            return;
        }
        synchronized (this) {
            for (Account account : updatedAccounts) {
                accounts.put(account.getAccountNumber(), account);
                accountLocks.computeIfAbsent(account.getAccountNumber(), key -> new ReentrantLock());
            }
        }
    }

    private void notifyObservers(String message) {
        for (AccountObserver observer : observers) {
            try {
                observer.update(message);
            } catch (RuntimeException e) {
                System.err.println("Observer notification failed: " + e.getMessage());
            }
        }
    }

    private void initializeTransientState() {
        this.observers = new CopyOnWriteArrayList<>();
        this.operationQueue = new ConcurrentLinkedQueue<>();
        this.executorService = Executors.newFixedThreadPool(5);
        this.pendingOperations = new CopyOnWriteArrayList<>();

        addObserver(new ConsoleNotifier());
        addObserver(new TransactionLogger());
    }

    public void awaitPendingOperations() {
        while (true) {
            CompletableFuture<?>[] futures;
            synchronized (this) {
                executePendingOperations();
                if (pendingOperations.isEmpty()) {
                    return;
                }
                futures = pendingOperations.toArray(new CompletableFuture[0]);
            }
            CompletableFuture.allOf(futures).join();
        }
    }

    private int generateAccountNumber() {
        int accountNumber;
        do {
            accountNumber = 100000 + SECURE_RANDOM.nextInt(900000);
        } while (accounts.containsKey(accountNumber));
        return accountNumber;
    }

    private record QueuedOperation(AccountOperation operation, CompletableFuture<OperationResult> future) {
    }
}