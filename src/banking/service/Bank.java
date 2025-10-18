package banking.service;

import banking.account.Account;
import banking.account.AccountFactory;
import banking.account.FixedDepositAccount;
import banking.account.SavingsAccount;
import banking.observer.AccountObserver;
import banking.observer.ConsoleNotifier;
import banking.observer.TransactionLogger;
import banking.operation.AccountOperation;
<<<<<<< HEAD
<<<<<<< HEAD
=======
=======
>>>>>>> origin/pr/11
import banking.operation.DepositOperation;
import banking.operation.OperationResult;
import banking.operation.TransferOperation;
import banking.operation.WithdrawOperation;
<<<<<<< HEAD
>>>>>>> origin/pr/10
=======
>>>>>>> origin/pr/11

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
<<<<<<< HEAD
<<<<<<< HEAD
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
=======
=======
>>>>>>> origin/pr/11
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
<<<<<<< HEAD
>>>>>>> origin/pr/10
=======
>>>>>>> origin/pr/11
import java.util.stream.Collectors;

public class Bank implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<Integer, Account> accounts;
    private transient List<AccountObserver> observers;
<<<<<<< HEAD
<<<<<<< HEAD
    private transient Queue<AccountOperation> operationQueue;
    private transient ExecutorService executorService;
=======
    private transient Queue<QueuedOperation> operationQueue;
    private transient ExecutorService executorService;
    private transient List<CompletableFuture<OperationResult>> pendingOperations;
>>>>>>> origin/pr/10
=======
    private transient Queue<QueuedOperation> operationQueue;
    private transient ExecutorService executorService;
    private transient List<CompletableFuture<OperationResult>> pendingOperations;
>>>>>>> origin/pr/11

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

<<<<<<< HEAD
<<<<<<< HEAD
=======
=======
>>>>>>> origin/pr/11
    public synchronized boolean updateAccountHolderName(int accountNumber, String newName) {
        Account account = accounts.get(accountNumber);
        if (account == null) {
            return false;
        }

        account.setUserName(newName);
        notifyObservers("Account holder updated for account " + accountNumber + " to " + account.getUserName());
        return true;
    }

<<<<<<< HEAD
>>>>>>> origin/pr/10
=======
>>>>>>> origin/pr/11
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

<<<<<<< HEAD
<<<<<<< HEAD
    public synchronized void queueOperation(AccountOperation operation) {
        operationQueue.add(operation);
        executePendingOperations();
=======
=======
>>>>>>> origin/pr/11
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
<<<<<<< HEAD
>>>>>>> origin/pr/10
=======
>>>>>>> origin/pr/11
    }

    public synchronized void executePendingOperations() {
        while (!operationQueue.isEmpty()) {
<<<<<<< HEAD
<<<<<<< HEAD
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
=======
=======
>>>>>>> origin/pr/11
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
    }

    public synchronized int addInterestToAllSavingsAccounts() {
        int processed = 0;
        for (Account account : accounts.values()) {
            if (account instanceof SavingsAccount || account instanceof FixedDepositAccount) {
                account.addInterest();
                processed++;
            }
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

        notifyObservers(result.isSuccess()
            ? "SUCCESS: " + result.getMessage()
            : "FAILED: " + result.getMessage());
        try {
            queued.future().complete(result);
        } finally {
            pendingOperations.remove(queued.future());
        }
<<<<<<< HEAD
>>>>>>> origin/pr/10
=======
>>>>>>> origin/pr/11
    }

    private void notifyObservers(String message) {
        for (AccountObserver observer : observers) {
<<<<<<< HEAD
<<<<<<< HEAD
            observer.update(message);
=======
=======
>>>>>>> origin/pr/11
            try {
                observer.update(message);
            } catch (RuntimeException e) {
                System.err.println("Observer notification failed: " + e.getMessage());
            }
<<<<<<< HEAD
>>>>>>> origin/pr/10
=======
>>>>>>> origin/pr/11
        }
    }

    private void initializeTransientState() {
<<<<<<< HEAD
<<<<<<< HEAD
        this.observers = new ArrayList<>();
        this.operationQueue = new ConcurrentLinkedQueue<>();
        this.executorService = Executors.newFixedThreadPool(5);
=======
=======
>>>>>>> origin/pr/11
        this.observers = new CopyOnWriteArrayList<>();
        this.operationQueue = new ConcurrentLinkedQueue<>();
        this.executorService = Executors.newFixedThreadPool(5);
        this.pendingOperations = new CopyOnWriteArrayList<>();
<<<<<<< HEAD
>>>>>>> origin/pr/10
=======
>>>>>>> origin/pr/11

        addObserver(new ConsoleNotifier());
        addObserver(new TransactionLogger());
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        initializeTransientState();
    }

<<<<<<< HEAD
<<<<<<< HEAD
=======
=======
>>>>>>> origin/pr/11
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

<<<<<<< HEAD
>>>>>>> origin/pr/10
=======
>>>>>>> origin/pr/11
    private int generateAccountNumber() {
        int accountNumber;
        do {
            accountNumber = 100000 + SECURE_RANDOM.nextInt(900000);
        } while (accounts.containsKey(accountNumber));
        return accountNumber;
    }
<<<<<<< HEAD
<<<<<<< HEAD
=======

    private record QueuedOperation(AccountOperation operation, CompletableFuture<OperationResult> future) {
    }
>>>>>>> origin/pr/10
=======

    private record QueuedOperation(AccountOperation operation, CompletableFuture<OperationResult> future) {
    }
>>>>>>> origin/pr/11
}
