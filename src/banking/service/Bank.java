package banking.service;

import banking.account.Account;
import banking.account.AccountFactory;
import banking.account.CurrentAccount;
import banking.account.FixedDepositAccount;
import banking.account.SavingsAccount;
import banking.snapshot.AccountSnapshot;
import banking.snapshot.BankSnapshot;
import banking.snapshot.TransactionSnapshot;
import banking.snapshot.TransactionType;
import banking.observer.AccountObserver;
import banking.observer.ConsoleNotifier;
import banking.observer.TransactionLogger;
import banking.operation.AccountOperation;
import banking.operation.DepositOperation;
import banking.operation.OperationResult;
import banking.operation.TransferOperation;
import banking.operation.WithdrawOperation;
import banking.transaction.BaseTransaction;
import banking.transaction.DepositTransaction;
import banking.transaction.InterestTransaction;
import banking.transaction.TransferReceiveTransaction;
import banking.transaction.TransferTransaction;
import banking.transaction.WithdrawalTransaction;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
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
import java.util.Locale;

public class Bank implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<Integer, Account> accounts;
    private transient List<AccountObserver> observers;
    private transient Queue<QueuedOperation> operationQueue;
    private transient ExecutorService executorService;
    private transient List<CompletableFuture<OperationResult>> pendingOperations;

    public Bank() {
        this(new HashMap<>());
    }

    private Bank(Map<Integer, Account> accounts) {
        this.accounts = new HashMap<>(accounts);
        initializeTransientState();
    }

    public static Bank restore(BankSnapshot snapshot) {
        Map<Integer, Account> accounts = new HashMap<>();
        for (AccountSnapshot accountSnapshot : snapshot.accounts()) {
            Account account = AccountFactory.restoreAccount(accountSnapshot);
            accounts.put(account.getAccountNumber(), account);
        }
        return new Bank(accounts);
    }

    public synchronized BankSnapshot snapshot() {
        List<AccountSnapshot> accountSnapshots = accounts.values().stream()
                .sorted(Comparator.comparingInt(Account::getAccountNumber))
                .map(Bank::toSnapshot)
                .collect(Collectors.toList());
        return new BankSnapshot(BankSnapshot.CURRENT_VERSION, accountSnapshots);
    }

    private static AccountSnapshot toSnapshot(Account account) {
        List<TransactionSnapshot> transactions = account.getTransactions().stream()
                .map(Bank::toSnapshot)
                .collect(Collectors.toCollection(ArrayList::new));

        Double minimumBalance = null;
        Double overdraftLimit = null;
        Integer termMonths = null;
        String maturityDate = null;

        if (account instanceof SavingsAccount savingsAccount) {
            minimumBalance = savingsAccount.getMinimumBalance();
        } else if (account instanceof CurrentAccount currentAccount) {
            overdraftLimit = currentAccount.getOverdraftLimit();
        } else if (account instanceof FixedDepositAccount fixedDepositAccount) {
            termMonths = fixedDepositAccount.getTermMonths();
            maturityDate = fixedDepositAccount.getMaturityDate().toString();
        }

        return new AccountSnapshot(canonicalAccountType(account), account.getAccountNumber(), account.getUserName(),
                account.getBalance(), account.getCreationDate(), minimumBalance, overdraftLimit, termMonths,
                maturityDate, transactions);
    }

    private static TransactionSnapshot toSnapshot(BaseTransaction transaction) {
        TransactionType type;
        Integer sourceAccount = null;
        Integer targetAccount = null;

        if (transaction instanceof DepositTransaction) {
            type = TransactionType.DEPOSIT;
        } else if (transaction instanceof WithdrawalTransaction) {
            type = TransactionType.WITHDRAWAL;
        } else if (transaction instanceof InterestTransaction) {
            type = TransactionType.INTEREST;
        } else if (transaction instanceof TransferTransaction transferTransaction) {
            type = TransactionType.TRANSFER_OUT;
            targetAccount = transferTransaction.getTargetAccountNumber();
        } else if (transaction instanceof TransferReceiveTransaction receiveTransaction) {
            type = TransactionType.TRANSFER_IN;
            sourceAccount = receiveTransaction.getSourceAccountNumber();
        } else {
            throw new IllegalArgumentException("Unsupported transaction type: " + transaction.getClass());
        }

        return new TransactionSnapshot(type, transaction.getAmount(), transaction.getTimestamp().toString(),
                transaction.getTransactionId(), sourceAccount, targetAccount);
    }

    private static String canonicalAccountType(Account account) {
        if (account instanceof SavingsAccount) {
            return "SAVINGS";
        }
        if (account instanceof CurrentAccount) {
            return "CURRENT";
        }
        if (account instanceof FixedDepositAccount) {
            return "FIXED_DEPOSIT";
        }
        return account.getAccountType().toUpperCase(Locale.ROOT).replace(' ', '_');
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

    public synchronized boolean updateAccountHolderName(int accountNumber, String newName) {
        Account account = accounts.get(accountNumber);
        if (account == null) {
            return false;
        }

        account.setUserName(newName);
        notifyObservers("Account holder updated for account " + accountNumber + " to " + account.getUserName());
        return true;
    }

    public synchronized Account getAccount(int accountNumber) {
        return accounts.get(accountNumber);
    }

    public synchronized List<Account> getAllAccounts() {
        return new ArrayList<>(accounts.values());
    }

    public synchronized int getPendingOperationCount() {
        return pendingOperations == null ? 0 : pendingOperations.size();
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

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        initializeTransientState();
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