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
import banking.persistence.BankRepository;
import banking.persistence.InMemoryBankRepository;
import banking.persistence.PersistenceStatus;
import banking.persistence.RepositoryException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Bank implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<Integer, Account> accounts;
    private transient List<AccountObserver> observers;
    private transient Queue<QueuedOperation> operationQueue;
    private transient ExecutorService executorService;
    private transient List<CompletableFuture<OperationResult>> pendingOperations;
    private transient BankRepository repository;
    private transient PersistenceStatus activePersistenceStatus;
    private transient PersistenceStatus primaryPersistenceStatus;

    public Bank() {
        this(new InMemoryBankRepository());
    }

    public Bank(BankRepository repository) {
        this(repository, repository.getStatus());
    }

    public Bank(BankRepository repository, PersistenceStatus primaryPersistenceStatus) {
        this.accounts = new HashMap<>();
        this.repository = Objects.requireNonNull(repository, "repository");
        this.primaryPersistenceStatus = Objects.requireNonNull(primaryPersistenceStatus,
                "primaryPersistenceStatus");
        this.activePersistenceStatus = repository.getStatus();
        initializeTransientState();
        loadPersistedAccounts();
    }

    public synchronized void addObserver(AccountObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    public synchronized Account createAccount(String userName, String accountType, double initialDeposit) {
        int accountNumber = generateAccountNumber();
        Account account = AccountFactory.createAccount(accountType, userName, accountNumber, initialDeposit);
        accounts.put(accountNumber, account);
        persistAccount(account);
        notifyObservers("New " + account.getAccountType() + " account created for " + userName
                + ", Account#: " + accountNumber);
        return account;
    }

    public synchronized boolean closeAccount(int accountNumber) {
        Account account = accounts.remove(accountNumber);
        if (account != null) {
            deleteAccountFromPersistence(accountNumber);
            notifyObservers("Account closed: " + account.getAccountNumber() + " for " + account.getUserName());
            return true;
        }
        return false;
    }

    public synchronized boolean updateAccountHolderName(int accountNumber, String newName) {
        Account account = accounts.get(accountNumber);
        if (account == null) {
            return false;
        }

        account.setUserName(newName);
        persistAccount(account);
        notifyObservers("Account holder updated for account " + accountNumber + " to " + account.getUserName());
        return true;
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
        Account account = accounts.get(accountNumber);
        if (account == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Account not found: " + accountNumber));
        }
        return queueOperation(new DepositOperation(account, amount));
    }

    public synchronized CompletableFuture<OperationResult> withdraw(int accountNumber, double amount) {
        Account account = accounts.get(accountNumber);
        if (account == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Account not found: " + accountNumber));
        }
        return queueOperation(new WithdrawOperation(account, amount));
    }

    public synchronized CompletableFuture<OperationResult> transfer(int sourceAccountNumber, int targetAccountNumber,
            double amount) {
        if (sourceAccountNumber == targetAccountNumber) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Source and target accounts must be different."));
        }

        Account sourceAccount = accounts.get(sourceAccountNumber);
        if (sourceAccount == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Source account not found: " + sourceAccountNumber));
        }

        Account targetAccount = accounts.get(targetAccountNumber);
        if (targetAccount == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Target account not found: " + targetAccountNumber));
        }

        return queueOperation(new TransferOperation(sourceAccount, targetAccount, amount));
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
        synchronized (this) {
            closeRepositoryQuietly();
        }
    }

    public synchronized int addInterestToAllSavingsAccounts() {
        int processed = 0;
        List<Account> updatedAccounts = new ArrayList<>();
        for (Account account : accounts.values()) {
            if (account instanceof SavingsAccount || account instanceof FixedDepositAccount) {
                account.addInterest();
                updatedAccounts.add(account);
                processed++;
            }
        }

        if (!updatedAccounts.isEmpty()) {
            persistAccounts(updatedAccounts);
        }

        if (processed > 0) {
            notifyObservers("Monthly interest added to " + processed + " eligible accounts");
        } else {
            notifyObservers("No eligible accounts found for monthly interest processing");
        }

        return processed;
    }

    private void runOperation(QueuedOperation queued) {
        OperationResult result;
        try {
            result = queued.operation().execute();
        } catch (Exception e) {
            result = OperationResult.failure("Unexpected error executing operation: " + e.getMessage());
        }

        if (result.isSuccess()) {
            persistAccounts(queued.operation().affectedAccounts());
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

    private void notifyObservers(String message) {
        for (AccountObserver observer : observers) {
            try {
                observer.update(message);
            } catch (RuntimeException e) {
                System.err.println("Observer notification failed: " + e.getMessage());
            }
        }
    }

    private void persistAccount(Account account) {
        if (account != null) {
            persistAccounts(List.of(account));
        }
    }

    private void persistAccounts(Collection<Account> accountsToPersist) {
        if (accountsToPersist == null || accountsToPersist.isEmpty()) {
            return;
        }
        BankRepository target;
        synchronized (this) {
            target = this.repository;
        }
        try {
            target.saveAccounts(accountsToPersist);
        } catch (RepositoryException e) {
            handlePersistenceFailure("persist accounts", e);
        }
    }

    private void deleteAccountFromPersistence(int accountNumber) {
        BankRepository target;
        synchronized (this) {
            target = this.repository;
        }
        try {
            target.deleteAccount(accountNumber);
        } catch (RepositoryException e) {
            handlePersistenceFailure("delete account", e);
        }
    }

    private synchronized void handlePersistenceFailure(String action, Exception exception) {
        System.err.println("Persistence failure during " + action + ": " + exception.getMessage());
        PersistenceStatus failureStatus = primaryPersistenceStatus == null
                ? PersistenceStatus.unavailable("jdbc", exception.getMessage(), exception)
                : primaryPersistenceStatus.unavailableCopy(exception.getMessage(), exception);
        switchToInMemory(failureStatus);
    }

    private synchronized void switchToInMemory(PersistenceStatus failureStatus) {
        if (repository instanceof InMemoryBankRepository) {
            this.primaryPersistenceStatus = failureStatus;
            this.activePersistenceStatus = repository.getStatus();
            return;
        }
        closeRepositoryQuietly();
        InMemoryBankRepository fallback = new InMemoryBankRepository();
        fallback.saveAccounts(accounts.values());
        this.repository = fallback;
        this.activePersistenceStatus = fallback.getStatus();
        this.primaryPersistenceStatus = failureStatus;
    }

    private void closeRepositoryQuietly() {
        if (repository == null) {
            return;
        }
        try {
            repository.close();
        } catch (Exception ignored) {
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

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.repository = new InMemoryBankRepository();
        this.activePersistenceStatus = repository.getStatus();
        if (this.primaryPersistenceStatus == null) {
            this.primaryPersistenceStatus = this.activePersistenceStatus;
        }
        initializeTransientState();
    }

    private void loadPersistedAccounts() {
        try {
            Map<Integer, Account> stored = repository.loadAccounts();
            accounts.putAll(stored);
        } catch (RepositoryException e) {
            handlePersistenceFailure("load accounts", e);
        }
    }

    public synchronized PersistenceStatus getPrimaryPersistenceStatus() {
        return primaryPersistenceStatus;
    }

    public synchronized PersistenceStatus getActivePersistenceStatus() {
        return activePersistenceStatus;
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