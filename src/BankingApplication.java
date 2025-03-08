import java.io.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

// Interface for account operations - improving OOP design with interfaces
interface AccountOperation {
    boolean execute();
    String getDescription();
}

// Base Transaction class
abstract class BaseTransaction implements Serializable {
    private static final long serialVersionUID = 1L;
    
    protected final double amount;
    protected final String dateTime;
    protected final String transactionId;
    
    public BaseTransaction(double amount) {
        this.amount = amount;
        this.dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.transactionId = generateTransactionId();
    }
    
    private String generateTransactionId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public String getDateTime() {
        return dateTime;
    }
    
    public abstract String getType();
    
    @Override
    public String toString() {
        return String.format("ID: %s, Amount: %.2f, Type: %s, Date and Time: %s", 
                transactionId, amount, getType(), dateTime);
    }
}

// Concrete transaction types
class DepositTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;
    
    public DepositTransaction(double amount) {
        super(amount);
    }
    
    @Override
    public String getType() {
        return "Deposit";
    }
}

class WithdrawalTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;
    
    public WithdrawalTransaction(double amount) {
        super(amount);
    }
    
    @Override
    public String getType() {
        return "Withdrawal";
    }
}

class InterestTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;
    
    public InterestTransaction(double amount) {
        super(amount);
    }
    
    @Override
    public String getType() {
        return "Interest Added";
    }
}

class TransferTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;
    private final int targetAccountNumber;
    
    public TransferTransaction(double amount, int targetAccountNumber) {
        super(amount);
        this.targetAccountNumber = targetAccountNumber;
    }
    
    public int getTargetAccountNumber() {
        return targetAccountNumber;
    }
    
    @Override
    public String getType() {
        return "Transfer to Acc#" + targetAccountNumber;
    }
}

class TransferReceiveTransaction extends BaseTransaction {
    private static final long serialVersionUID = 1L;
    private final int sourceAccountNumber;
    
    public TransferReceiveTransaction(double amount, int sourceAccountNumber) {
        super(amount);
        this.sourceAccountNumber = sourceAccountNumber;
    }
    
    @Override
    public String getType() {
        return "Received from Acc#" + sourceAccountNumber;
    }
}

// Abstract account class - improved OOP with abstract classes
abstract class Account implements Serializable {
    private static final long serialVersionUID = 1L;
    
    protected String userName;
    protected final int accountNumber;
    protected double balance;
    protected final List<BaseTransaction> transactions;
    protected final String creationDate;
    
    public Account(String userName, int accountNumber) {
        this.userName = userName;
        this.accountNumber = accountNumber;
        this.balance = 0;
        this.transactions = new ArrayList<>();
        this.creationDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    
    public synchronized void deposit(double amount) {
        if (amount > 0) {
            balance += amount;
            transactions.add(new DepositTransaction(amount));
        } else {
            throw new IllegalArgumentException("Invalid deposit amount. Please enter a positive amount.");
        }
    }
    
    public synchronized boolean withdraw(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive.");
        }
        
        if (canWithdraw(amount)) {
            balance -= amount;
            transactions.add(new WithdrawalTransaction(amount));
            return true;
        }
        return false;
    }
    
    // Abstract method for account-specific withdrawal logic
    protected abstract boolean canWithdraw(double amount);
    
    // Abstract method for adding interest (different for each account type)
    public abstract void addInterest();
    
    // Abstract method to get account type
    public abstract String getAccountType();
    
    public synchronized boolean transfer(double amount, Account targetAccount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive.");
        }
        
        if (canWithdraw(amount)) {
            balance -= amount;
            transactions.add(new TransferTransaction(amount, targetAccount.getAccountNumber()));
            targetAccount.receiveTransfer(amount, this.accountNumber);
            return true;
        }
        return false;
    }
    
    protected synchronized void receiveTransfer(double amount, int sourceAccountNumber) {
        balance += amount;
        transactions.add(new TransferReceiveTransaction(amount, sourceAccountNumber));
    }
    
    public List<BaseTransaction> getTransactions() {
        return new ArrayList<>(transactions);
    }
    
    public List<BaseTransaction> getTransactionsByType(String type) {
        return transactions.stream()
                .filter(t -> t.getType().contains(type))
                .collect(Collectors.toList());
    }
    
    public List<BaseTransaction> getTransactionsByDateRange(String startDate, String endDate) {
        return transactions.stream()
                .filter(t -> t.getDateTime().compareTo(startDate) >= 0 && 
                             t.getDateTime().compareTo(endDate) <= 0)
                .collect(Collectors.toList());
    }
    
    @Override
    public String toString() {
        return String.format("User: %s, Account Number: %d, Balance: %.2f, Type: %s, Created: %s", 
                userName, accountNumber, balance, getAccountType(), creationDate);
    }
    
    // Basic getters
    public int getAccountNumber() {
        return accountNumber;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public double getBalance() {
        return balance;
    }
}

// Concrete account implementations
class SavingsAccount extends Account {
    private static final long serialVersionUID = 1L;
    private static final double INTEREST_RATE = 0.04; // 4%
    private double minimumBalance = 1000;
    
    public SavingsAccount(String userName, int accountNumber) {
        super(userName, accountNumber);
    }
    
    @Override
    protected boolean canWithdraw(double amount) {
        return balance - amount >= minimumBalance;
    }
    
    @Override
    public synchronized void addInterest() {
        double interest = balance * INTEREST_RATE / 12; // Monthly interest
        if (interest > 0) {
            balance += interest;
            transactions.add(new InterestTransaction(interest));
        }
    }
    
    @Override
    public String getAccountType() {
        return "Savings";
    }
    
    public double getMinimumBalance() {
        return minimumBalance;
    }
    
    public void setMinimumBalance(double minimumBalance) {
        this.minimumBalance = minimumBalance;
    }
}

class CurrentAccount extends Account {
    private static final long serialVersionUID = 1L;
    private double overdraftLimit = 10000;
    
    public CurrentAccount(String userName, int accountNumber) {
        super(userName, accountNumber);
    }
    
    @Override
    protected boolean canWithdraw(double amount) {
        return balance - amount >= -overdraftLimit;
    }
    
    @Override
    public void addInterest() {
        // Current accounts typically don't earn interest
    }
    
    @Override
    public String getAccountType() {
        return "Current";
    }
    
    public double getOverdraftLimit() {
        return overdraftLimit;
    }
    
    public void setOverdraftLimit(double overdraftLimit) {
        this.overdraftLimit = overdraftLimit;
    }
}

class FixedDepositAccount extends Account {
    private static final long serialVersionUID = 1L;
    private static final double INTEREST_RATE = 0.08; // 8%
    private final LocalDateTime maturityDate;
    private final int termMonths;
    
    public FixedDepositAccount(String userName, int accountNumber, double initialDeposit, int termMonths) {
        super(userName, accountNumber);
        if (initialDeposit < 5000) {
            throw new IllegalArgumentException("Fixed deposit requires minimum initial deposit of 5000");
        }
        this.balance = initialDeposit;
        this.termMonths = termMonths;
        this.maturityDate = LocalDateTime.now().plusMonths(termMonths);
        transactions.add(new DepositTransaction(initialDeposit));
    }
    
    @Override
    protected boolean canWithdraw(double amount) {
        // Can only withdraw after maturity date
        return LocalDateTime.now().isAfter(maturityDate) && amount <= balance;
    }
    
    @Override
    public synchronized void addInterest() {
        double interest = balance * INTEREST_RATE / 12; // Monthly interest
        balance += interest;
        transactions.add(new InterestTransaction(interest));
    }
    
    @Override
    public String getAccountType() {
        return "Fixed Deposit (" + termMonths + " months)";
    }
    
    public LocalDateTime getMaturityDate() {
        return maturityDate;
    }
    
    public String getFormattedMaturityDate() {
        return maturityDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    
    @Override
    public String toString() {
        return super.toString() + ", Matures: " + getFormattedMaturityDate();
    }
}

// Factory pattern for creating different account types
class AccountFactory {
    public static Account createAccount(String accountType, String userName, int accountNumber, double initialDeposit) {
        Account account;
        
        switch (accountType.toLowerCase()) {
            case "savings":
                account = new SavingsAccount(userName, accountNumber);
                break;
            case "current":
                account = new CurrentAccount(userName, accountNumber);
                break;
            case "fixed":
            case "fd":
                // Default to 12 months if not specified
                account = new FixedDepositAccount(userName, accountNumber, initialDeposit, 12);
                return account; // Return early as deposit is handled in constructor
            default:
                throw new IllegalArgumentException("Unknown account type: " + accountType);
        }
        
        if (initialDeposit > 0) {
            account.deposit(initialDeposit);
        }
        
        return account;
    }
}

// Observer pattern for account notifications
interface AccountObserver {
    void update(String message);
}

class ConsoleNotifier implements AccountObserver {
    @Override
    public void update(String message) {
        System.out.println("NOTIFICATION: " + message);
    }
}

class TransactionLogger implements AccountObserver {
    @Override
    public void update(String message) {
        try (PrintWriter out = new PrintWriter(new FileWriter("transaction_log.txt", true))) {
            out.println(LocalDateTime.now() + ": " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// Command pattern for executing account operations
class DepositOperation implements AccountOperation {
    private final Account account;
    private final double amount;
    
    public DepositOperation(Account account, double amount) {
        this.account = account;
        this.amount = amount;
    }
    
    @Override
    public boolean execute() {
        try {
            account.deposit(amount);
            return true;
        } catch (Exception e) {
            System.out.println("Deposit failed: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getDescription() {
        return "Deposit of " + amount + " to account " + account.getAccountNumber();
    }
}

class WithdrawOperation implements AccountOperation {
    private final Account account;
    private final double amount;
    
    public WithdrawOperation(Account account, double amount) {
        this.account = account;
        this.amount = amount;
    }
    
    @Override
    public boolean execute() {
        try {
            return account.withdraw(amount);
        } catch (Exception e) {
            System.out.println("Withdrawal failed: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getDescription() {
        return "Withdrawal of " + amount + " from account " + account.getAccountNumber();
    }
}

class TransferOperation implements AccountOperation {
    private final Account sourceAccount;
    private final Account targetAccount;
    private final double amount;
    
    public TransferOperation(Account sourceAccount, Account targetAccount, double amount) {
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.amount = amount;
    }
    
    @Override
    public boolean execute() {
        try {
            return sourceAccount.transfer(amount, targetAccount);
        } catch (Exception e) {
            System.out.println("Transfer failed: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getDescription() {
        return "Transfer of " + amount + " from account " + sourceAccount.getAccountNumber() + 
               " to account " + targetAccount.getAccountNumber();
    }
}

// Bank class with enhanced functionality
class Bank implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final SecureRandom secureRandom = new SecureRandom();
    
    private final Map<Integer, Account> accounts;
    private final List<AccountObserver> observers;
    private final Queue<AccountOperation> operationQueue;
    private final ExecutorService executorService;
    
    public Bank() {
        this.accounts = new HashMap<>();
        this.observers = new ArrayList<>();
        this.operationQueue = new LinkedList<>();
        this.executorService = Executors.newFixedThreadPool(5);
        
        // Add default observers
        addObserver(new ConsoleNotifier());
        addObserver(new TransactionLogger());
    }
    
    public void addObserver(AccountObserver observer) {
        observers.add(observer);
    }
    
    private void notifyObservers(String message) {
        for (AccountObserver observer : observers) {
            observer.update(message);
        }
    }
    
    public Account createAccount(String userName, String accountType, double initialDeposit) {
        int accountNumber = generateAccountNumber();
        Account account = AccountFactory.createAccount(accountType, userName, accountNumber, initialDeposit);
        accounts.put(accountNumber, account);
        
        notifyObservers("New " + account.getAccountType() + " account created for " + userName + 
                ", Account#: " + accountNumber);
        return account;
    }
    
    public boolean closeAccount(int accountNumber) {
        Account account = accounts.remove(accountNumber);
        if (account != null) {
            notifyObservers("Account closed: " + account.getAccountNumber() + " for " + account.getUserName());
            return true;
        }
        return false;
    }
    
    public Account getAccount(int accountNumber) {
        return accounts.get(accountNumber);
    }
    
    public List<Account> getAllAccounts() {
        return new ArrayList<>(accounts.values());
    }
    
    public List<Account> getAccountsByType(String accountType) {
        return accounts.values().stream()
                .filter(a -> a.getAccountType().toLowerCase().contains(accountType.toLowerCase()))
                .collect(Collectors.toList());
    }
    
    public List<Account> searchAccounts(String keyword) {
        String lowercaseKeyword = keyword.toLowerCase();
        return accounts.values().stream()
                .filter(a -> a.getUserName().toLowerCase().contains(lowercaseKeyword))
                .collect(Collectors.toList());
    }
    
    public void queueOperation(AccountOperation operation) {
        operationQueue.add(operation);
        executePendingOperations();
    }
    
    public void executePendingOperations() {
        List<Future<Boolean>> futures = new ArrayList<>();
        
        while (!operationQueue.isEmpty()) {
            AccountOperation operation = operationQueue.poll();
            futures.add(executorService.submit(() -> {
                boolean result = operation.execute();
                if (result) {
                    notifyObservers("Operation completed: " + operation.getDescription());
                } else {
                    notifyObservers("Operation failed: " + operation.getDescription());
                }
                return result;
            }));
        }
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
    
    public void addInterestToAllSavingsAccounts() {
        accounts.values().stream()
                .filter(a -> a instanceof SavingsAccount || a instanceof FixedDepositAccount)
                .forEach(Account::addInterest);
        notifyObservers("Monthly interest added to all eligible accounts");
    }
    
    private int generateAccountNumber() {
        int accountNumber;
        do {
            accountNumber = 100000 + secureRandom.nextInt(900000);
        } while (accounts.containsKey(accountNumber));
        return accountNumber;
    }
}

// Data Access Object for persistent storage
class BankDAO {
    private static final String FILENAME = "banking_system.ser";
    
    public static void saveBank(Bank bank) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILENAME))) {
            oos.writeObject(bank);
            System.out.println("Banking system data has been saved successfully.");
        } catch (IOException e) {
            System.err.println("Error saving bank data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static Bank loadBank() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILENAME))) {
            return (Bank) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("No existing bank data found or error loading. Creating new bank.");
            return new Bank();
        }
    }
}

// Enhanced console UI with ANSI colors
class ConsoleUI {
    // ANSI color codes
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    
    private static final String ANSI_BG_BLACK = "\u001B[40m";
    private static final String ANSI_BG_RED = "\u001B[41m";
    private static final String ANSI_BG_GREEN = "\u001B[42m";
    private static final String ANSI_BG_YELLOW = "\u001B[43m";
    private static final String ANSI_BG_BLUE = "\u001B[44m";
    private static final String ANSI_BG_PURPLE = "\u001B[45m";
    private static final String ANSI_BG_CYAN = "\u001B[46m";
    private static final String ANSI_BG_WHITE = "\u001B[47m";
    
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_ITALIC = "\u001B[3m";
    private static final String ANSI_UNDERLINE = "\u001B[4m";
    
    private final Scanner scanner;
    private final Bank bank;
    
    public ConsoleUI(Bank bank) {
        this.scanner = new Scanner(System.in);
        this.bank = bank;
    }
    
    public void start() {
        boolean exit = false;
        
        displayWelcomeBanner();
        
        while (!exit) {
            displayMainMenu();
            int choice = getIntInput("Please select an option: ");
            
            switch (choice) {
                case 1:
                    createAccountMenu();
                    break;
                case 2:
                    accountOperationsMenu();
                    break;
                case 3:
                    displayAllAccounts();
                    break;
                case 4:
                    searchAccounts();
                    break;
                case 5:
                    generateReportsMenu();
                    break;
                case 6:
                    accountManagementMenu();
                    break;
                case 7:
                    exit = true;
                    BankDAO.saveBank(bank);
                    bank.shutdown();
                    System.out.println(ANSI_GREEN + "Thank you for using our banking system. Goodbye!" + ANSI_RESET);
                    break;
                default:
                    System.out.println(ANSI_RED + "Invalid option. Please try again." + ANSI_RESET);
            }
        }
    }
    
    private void displayWelcomeBanner() {
        System.out.println(ANSI_BG_BLUE + ANSI_WHITE + ANSI_BOLD);
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║                                                ║");
        System.out.println("║         WELCOME TO ADVANCED BANKING SYSTEM     ║");
        System.out.println("║                                                ║");
        System.out.println("╚════════════════════════════════════════════════╝" + ANSI_RESET);
        System.out.println();
    }
    
    private void displayMainMenu() {
        System.out.println(ANSI_PURPLE + ANSI_BOLD + "\n===== MAIN MENU =====" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "1. Create New Account");
        System.out.println("2. Account Operations");
        System.out.println("3. View All Accounts");
        System.out.println("4. Search Accounts");
        System.out.println("5. Generate Reports");
        System.out.println("6. Account Management");
        System.out.println("7. Exit" + ANSI_RESET);
    }
    
    private void createAccountMenu() {
        System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== CREATE NEW ACCOUNT =====" + ANSI_RESET);
        
        String userName = getStringInput("Enter customer name: ");
        System.out.println(ANSI_CYAN + "Available account types:");
        System.out.println("1. Savings Account");
        System.out.println("2. Current Account");
        System.out.println("3. Fixed Deposit Account" + ANSI_RESET);
        
        int typeChoice = getIntInput("Select account type (1-3): ");
        String accountType;
        
        switch (typeChoice) {
            case 1:
                accountType = "savings";
                break;
            case 2:
                accountType = "current";
                break;
            case 3:
                accountType = "fixed";
                break;
            default:
                System.out.println(ANSI_RED + "Invalid choice. Defaulting to Savings Account." + ANSI_RESET);
                accountType = "savings";
        }
        
        double initialDeposit = getDoubleInput("Enter initial deposit amount: ");
        
        try {
            Account account = bank.createAccount(userName, accountType, initialDeposit);
            System.out.println(ANSI_GREEN + "Account created successfully!" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Account Number: " + account.getAccountNumber() + ANSI_RESET);
            displayAccountDetails(account);
        } catch (IllegalArgumentException e) {
            System.out.println(ANSI_RED + "Error creating account: " + e.getMessage() + ANSI_RESET);
        }
    }
    
    private void accountOperationsMenu() {
        int accountNumber = getIntInput("Enter account number: ");
        Account account = bank.getAccount(accountNumber);
        
        if (account == null) {
            System.out.println(ANSI_RED + "Account not found!" + ANSI_RESET);
            return;
        }
        
        displayAccountDetails(account);
        
        boolean back = false;
        while (!back) {
            System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== ACCOUNT OPERATIONS =====" + ANSI_RESET);
            System.out.println(ANSI_CYAN + "1. Deposit");
            System.out.println("2. Withdraw");
            System.out.println("3. Transfer");
            System.out.println("4. View Transactions");
            System.out.println("5. Account Statement");
            System.out.println("6. Back to Main Menu" + ANSI_RESET);
            
            int choice = getIntInput("Select operation: ");
            
            switch (choice) {
                case 1:
                    performDeposit(account);
                    break;
                case 2:
                    performWithdrawal(account);
                    break;
                case 3:
                    performTransfer(account);
                    break;
                case 4:
                    viewTransactions(account);
                    break;
                case 5:
                    generateAccountStatement(account);
                    break;
                case 6:
                    back = true;
                    break;
                default:
                    System.out.println(ANSI_RED + "Invalid option!" + ANSI_RESET);
            }
        }
    }
    
    private void performDeposit(Account account) {
        double amount = getDoubleInput("Enter deposit amount: ");
        AccountOperation operation = new DepositOperation(account, amount);
        bank.queueOperation(operation);
        System.out.println(ANSI_GREEN + "Deposit operation queued. Processing..." + ANSI_RESET);
        try {
            Thread.sleep(500); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        displayAccountDetails(account);
    }
    
    private void performWithdrawal(Account account) {
        double amount = getDoubleInput("Enter withdrawal amount: ");
        AccountOperation operation = new WithdrawOperation(account, amount);
        bank.queueOperation(operation);
        System.out.println(ANSI_GREEN + "Withdrawal operation queued. Processing..." + ANSI_RESET);
        try {
            Thread.sleep(500); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        displayAccountDetails(account);
    }
    
    private void performTransfer(Account sourceAccount) {
        int targetAccountNumber = getIntInput("Enter target account number: ");
        Account targetAccount = bank.getAccount(targetAccountNumber);
        
        if (targetAccount == null) {
            System.out.println(ANSI_RED + "Target account not found!" + ANSI_RESET);
            return;
        }
        
        if (sourceAccount.getAccountNumber() == targetAccountNumber) {
            System.out.println(ANSI_RED + "Cannot transfer to the same account!" + ANSI_RESET);
            return;
        }
        
        double amount = getDoubleInput("Enter transfer amount: ");
        AccountOperation operation = new TransferOperation(sourceAccount, targetAccount, amount);
        bank.queueOperation(operation);
        System.out.println(ANSI_GREEN + "Transfer operation queued. Processing..." + ANSI_RESET);
        try {
            Thread.sleep(500); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        displayAccountDetails(sourceAccount);
    }
    
    private void viewTransactions(Account account) {
        List<BaseTransaction> transactions = account.getTransactions();
        
        if (transactions.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No transactions found for this account." + ANSI_RESET);
            return;
        }
        
        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== TRANSACTION HISTORY =====" + ANSI_RESET);
        System.out.println(ANSI_UNDERLINE + "Account Number: " + account.getAccountNumber() + ANSI_RESET);
        
        System.out.println(ANSI_CYAN + 
                           String.format("%-10s %-15s %-12s %-25s", 
                                         "ID", "TYPE", "AMOUNT", "DATE/TIME") + 
                           ANSI_RESET);
        
        for (BaseTransaction transaction : transactions) {
            String amountStr = String.format("%.2f", transaction.getAmount());
            String colorCode = transaction.getType().contains("Deposit") || 
                               transaction.getType().contains("Interest") || 
                               transaction.getType().contains("Received") ? 
                               ANSI_GREEN : ANSI_RED;
            
            System.out.println(colorCode + 
                               String.format("%-10s %-15s %-12s %-25s", 
                                             transaction.getTransactionId(),
                                             transaction.getType(),
                                             amountStr,
                                             transaction.getDateTime()) + 
                               ANSI_RESET);
        }
    }
    
    private void generateAccountStatement(Account account) {
        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== ACCOUNT STATEMENT =====" + ANSI_RESET);
        
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        System.out.println("Select statement period:");
        System.out.println("1. Last Month");
        System.out.println("2. Last 3 Months");
        System.out.println("3. Last 6 Months");
        System.out.println("4. Custom Period");
        
        int choice = getIntInput("Enter your choice: ");
        
        String startDate, endDate;
        endDate = now.format(formatter);
        
        switch (choice) {
            case 1:
                startDate = now.minusMonths(1).format(formatter);
                break;
            case 2:
                startDate = now.minusMonths(3).format(formatter);
                break;
            case 3:
                startDate = now.minusMonths(6).format(formatter);
                break;
            case 4:
                startDate = getStringInput("Enter start date (yyyy-MM-dd): ");
                endDate = getStringInput("Enter end date (yyyy-MM-dd): ");
                break;
            default:
                System.out.println(ANSI_RED + "Invalid choice. Using last month as default." + ANSI_RESET);
                startDate = now.minusMonths(1).format(formatter);
        }
        
        List<BaseTransaction> statementTransactions = account.getTransactionsByDateRange(startDate, endDate);
        
        if (statementTransactions.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No transactions found for the selected period." + ANSI_RESET);
            return;
        }
        
        System.out.println(ANSI_UNDERLINE + "Statement Period: " + startDate + " to " + endDate + ANSI_RESET);
        System.out.println(ANSI_UNDERLINE + "Account Number: " + account.getAccountNumber() + ANSI_RESET);
        System.out.println(ANSI_UNDERLINE + "Account Holder: " + account.getUserName() + ANSI_RESET);
        System.out.println(ANSI_UNDERLINE + "Account Type: " + account.getAccountType() + ANSI_RESET);
        
        System.out.println(ANSI_CYAN + 
                           String.format("%-10s %-15s %-12s %-25s", 
                                         "ID", "TYPE", "AMOUNT", "DATE/TIME") + 
                           ANSI_RESET);
        
        double totalDeposits = 0;
        double totalWithdrawals = 0;
        
        for (BaseTransaction transaction : statementTransactions) {
            String amountStr = String.format("%.2f", transaction.getAmount());
            String colorCode = transaction.getType().contains("Deposit") || 
                               transaction.getType().contains("Interest") || 
                               transaction.getType().contains("Received") ? 
                               ANSI_GREEN : ANSI_RED;
            
            if (colorCode.equals(ANSI_GREEN)) {
                totalDeposits += transaction.getAmount();
            } else {
                totalWithdrawals += transaction.getAmount();
            }
            
            System.out.println(colorCode + 
                               String.format("%-10s %-15s %-12s %-25s", 
                                             transaction.getTransactionId(),
                                             transaction.getType(),
                                             amountStr,
                                             transaction.getDateTime()) + 
                               ANSI_RESET);
        }
        
        System.out.println("\n" + ANSI_BOLD + "Summary:" + ANSI_RESET);
        System.out.println(ANSI_GREEN + "Total Credits: " + String.format("%.2f", totalDeposits) + ANSI_RESET);
        System.out.println(ANSI_RED + "Total Debits: " + String.format("%.2f", totalWithdrawals) + ANSI_RESET);
        System.out.println(ANSI_BOLD + "Current Balance: " + String.format("%.2f", account.getBalance()) + ANSI_RESET);
    }
    
    private void displayAllAccounts() {
        List<Account> allAccounts = bank.getAllAccounts();
        
        if (allAccounts.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No accounts found in the system." + ANSI_RESET);
            return;
        }
        
        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== ALL ACCOUNTS =====" + ANSI_RESET);
        
        System.out.println(ANSI_CYAN + 
                           String.format("%-6s %-20s %-15s %-15s %-15s", 
                                         "ACC#", "NAME", "TYPE", "BALANCE", "CREATED") + 
                           ANSI_RESET);
        
        for (Account account : allAccounts) {
            System.out.println(String.format("%-6d %-20s %-15s %-15.2f %-15s", 
                                            account.getAccountNumber(),
                                            account.getUserName(),
                                            account.getAccountType(),
                                            account.getBalance(),
                                            account.creationDate));
        }
    }
    
    private void searchAccounts() {
        System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== SEARCH ACCOUNTS =====" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "1. Search by Account Type");
        System.out.println("2. Search by Customer Name" + ANSI_RESET);
        
        int choice = getIntInput("Enter your choice: ");
        List<Account> results;
        
        switch (choice) {
            case 1:
                String accountType = getStringInput("Enter account type to search (savings/current/fixed): ");
                results = bank.getAccountsByType(accountType);
                break;
            case 2:
                String keyword = getStringInput("Enter customer name to search: ");
                results = bank.searchAccounts(keyword);
                break;
            default:
                System.out.println(ANSI_RED + "Invalid choice!" + ANSI_RESET);
                return;
        }
        
        if (results.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No matching accounts found." + ANSI_RESET);
            return;
        }
        
        System.out.println(ANSI_GREEN + "Found " + results.size() + " matching accounts:" + ANSI_RESET);
        
        System.out.println(ANSI_CYAN + 
                           String.format("%-6s %-20s %-15s %-15s %-15s", 
                                         "ACC#", "NAME", "TYPE", "BALANCE", "CREATED") + 
                           ANSI_RESET);
        
        for (Account account : results) {
            System.out.println(String.format("%-6d %-20s %-15s %-15.2f %-15s", 
                                            account.getAccountNumber(),
                                            account.getUserName(),
                                            account.getAccountType(),
                                            account.getBalance(),
                                            account.creationDate));
        }
    }
    
    private void generateReportsMenu() {
        System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== GENERATE REPORTS =====" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "1. Account Summary Report");
        System.out.println("2. High-Value Accounts Report");
        System.out.println("3. Transaction Volume Report");
        System.out.println("4. Back to Main Menu" + ANSI_RESET);
        
        int choice = getIntInput("Enter your choice: ");
        
        switch (choice) {
            case 1:
                generateAccountSummaryReport();
                break;
            case 2:
                generateHighValueAccountsReport();
                break;
            case 3:
                generateTransactionVolumeReport();
                break;
            case 4:
                return;
            default:
                System.out.println(ANSI_RED + "Invalid choice!" + ANSI_RESET);
        }
    }
    
    private void generateAccountSummaryReport() {
        List<Account> allAccounts = bank.getAllAccounts();
        
        if (allAccounts.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No accounts found in the system." + ANSI_RESET);
            return;
        }
        
        int totalAccounts = allAccounts.size();
        int savingsAccounts = 0;
        int currentAccounts = 0;
        int fixedDepositAccounts = 0;
        double totalBalance = 0;
        
        for (Account account : allAccounts) {
            if (account.getAccountType().toLowerCase().contains("savings")) {
                savingsAccounts++;
            } else if (account.getAccountType().toLowerCase().contains("current")) {
                currentAccounts++;
            } else if (account.getAccountType().toLowerCase().contains("fixed")) {
                fixedDepositAccounts++;
            }
            
            totalBalance += account.getBalance();
        }
        
        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== ACCOUNT SUMMARY REPORT =====" + ANSI_RESET);
        System.out.println(ANSI_BOLD + "Total Accounts: " + ANSI_RESET + totalAccounts);
        System.out.println(ANSI_BOLD + "Savings Accounts: " + ANSI_RESET + savingsAccounts);
        System.out.println(ANSI_BOLD + "Current Accounts: " + ANSI_RESET + currentAccounts);
        System.out.println(ANSI_BOLD + "Fixed Deposit Accounts: " + ANSI_RESET + fixedDepositAccounts);
        System.out.println(ANSI_BOLD + "Total Balance: " + ANSI_RESET + String.format("%.2f", totalBalance));
        System.out.println(ANSI_BOLD + "Average Balance: " + ANSI_RESET + String.format("%.2f", totalBalance / totalAccounts));
    }
    
    private void generateHighValueAccountsReport() {
        double threshold = getDoubleInput("Enter balance threshold for high-value accounts: ");
        
        List<Account> highValueAccounts = bank.getAllAccounts().stream()
                .filter(a -> a.getBalance() >= threshold)
                .sorted((a1, a2) -> Double.compare(a2.getBalance(), a1.getBalance()))
                .collect(Collectors.toList());
        
        if (highValueAccounts.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No accounts found with balance >= " + threshold + ANSI_RESET);
            return;
        }
        
        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== HIGH-VALUE ACCOUNTS REPORT =====" + ANSI_RESET);
        System.out.println(ANSI_BOLD + "Accounts with balance >= " + threshold + ": " + highValueAccounts.size() + ANSI_RESET);
        
        System.out.println(ANSI_CYAN + 
                           String.format("%-6s %-20s %-15s %-15s %-15s", 
                                         "ACC#", "NAME", "TYPE", "BALANCE", "CREATED") + 
                           ANSI_RESET);
        
        for (Account account : highValueAccounts) {
            System.out.println(String.format("%-6d %-20s %-15s %-15.2f %-15s", 
                                            account.getAccountNumber(),
                                            account.getUserName(),
                                            account.getAccountType(),
                                            account.getBalance(),
                                            account.creationDate));
        }
    }
    
    private void generateTransactionVolumeReport() {
        List<Account> allAccounts = bank.getAllAccounts();
        Map<String, Integer> transactionTypeCount = new HashMap<>();
        
        for (Account account : allAccounts) {
            for (BaseTransaction transaction : account.getTransactions()) {
                String type = transaction.getType();
                transactionTypeCount.put(type, transactionTypeCount.getOrDefault(type, 0) + 1);
            }
        }
        
        if (transactionTypeCount.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No transactions found in the system." + ANSI_RESET);
            return;
        }
        
        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== TRANSACTION VOLUME REPORT =====" + ANSI_RESET);
        
        transactionTypeCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                System.out.println(ANSI_BOLD + entry.getKey() + ": " + ANSI_RESET + entry.getValue());
            });
    }
    
    private void accountManagementMenu() {
        System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== ACCOUNT MANAGEMENT =====" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "1. Update Account Holder Name");
        System.out.println("2. Close Account");
        System.out.println("3. Process Monthly Interest");
        System.out.println("4. Back to Main Menu" + ANSI_RESET);
        
        int choice = getIntInput("Enter your choice: ");
        
        switch (choice) {
            case 1:
                updateAccountName();
                break;
            case 2:
                closeAccount();
                break;
            case 3:
                processMonthlyInterest();
                break;
            case 4:
                return;
            default:
                System.out.println(ANSI_RED + "Invalid choice!" + ANSI_RESET);
        }
    }
    
    private void updateAccountName() {
        int accountNumber = getIntInput("Enter account number: ");
        Account account = bank.getAccount(accountNumber);
        
        if (account == null) {
            System.out.println(ANSI_RED + "Account not found!" + ANSI_RESET);
            return;
        }
        
        System.out.println(ANSI_CYAN + "Current account holder: " + account.getUserName() + ANSI_RESET);
        String newName = getStringInput("Enter new account holder name: ");
        account.setUserName(newName);
        System.out.println(ANSI_GREEN + "Account holder name updated successfully!" + ANSI_RESET);
    }
    
    private void closeAccount() {
        int accountNumber = getIntInput("Enter account number to close: ");
        Account account = bank.getAccount(accountNumber);
        
        if (account == null) {
            System.out.println(ANSI_RED + "Account not found!" + ANSI_RESET);
            return;
        }
        
        displayAccountDetails(account);
        
        String confirm = getStringInput("Are you sure you want to close this account? (yes/no): ");
        if (confirm.equalsIgnoreCase("yes")) {
            boolean success = bank.closeAccount(accountNumber);
            if (success) {
                System.out.println(ANSI_GREEN + "Account closed successfully!" + ANSI_RESET);
            } else {
                System.out.println(ANSI_RED + "Failed to close account!" + ANSI_RESET);
            }
        } else {
            System.out.println(ANSI_YELLOW + "Account closure canceled." + ANSI_RESET);
        }
    }
    
    private void processMonthlyInterest() {
        String confirm = getStringInput("Process monthly interest for all eligible accounts? (yes/no): ");
        if (confirm.equalsIgnoreCase("yes")) {
            bank.addInterestToAllSavingsAccounts();
            System.out.println(ANSI_GREEN + "Monthly interest processed successfully!" + ANSI_RESET);
        } else {
            System.out.println(ANSI_YELLOW + "Interest processing canceled." + ANSI_RESET);
        }
    }
    
    private void displayAccountDetails(Account account) {
        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== ACCOUNT DETAILS =====" + ANSI_RESET);
        System.out.println(ANSI_BOLD + "Account Number: " + ANSI_RESET + account.getAccountNumber());
        System.out.println(ANSI_BOLD + "Account Holder: " + ANSI_RESET + account.getUserName());
        System.out.println(ANSI_BOLD + "Account Type: " + ANSI_RESET + account.getAccountType());
        System.out.println(ANSI_BOLD + "Balance: " + ANSI_RESET + String.format("%.2f", account.getBalance()));
        System.out.println(ANSI_BOLD + "Creation Date: " + ANSI_RESET + account.creationDate);
        
        // Display type-specific details
        if (account instanceof SavingsAccount) {
            SavingsAccount savingsAccount = (SavingsAccount) account;
            System.out.println(ANSI_BOLD + "Minimum Balance: " + ANSI_RESET + 
                              String.format("%.2f", savingsAccount.getMinimumBalance()));
        } else if (account instanceof CurrentAccount) {
            CurrentAccount currentAccount = (CurrentAccount) account;
            System.out.println(ANSI_BOLD + "Overdraft Limit: " + ANSI_RESET + 
                              String.format("%.2f", currentAccount.getOverdraftLimit()));
        } else if (account instanceof FixedDepositAccount) {
            FixedDepositAccount fdAccount = (FixedDepositAccount) account;
            System.out.println(ANSI_BOLD + "Maturity Date: " + ANSI_RESET + fdAccount.getFormattedMaturityDate());
        }
    }
    
    private String getStringInput(String prompt) {
        System.out.print(ANSI_YELLOW + prompt + ANSI_RESET);
        return scanner.nextLine();
    }
    
    private int getIntInput(String prompt) {
        while (true) {
            try {
                System.out.print(ANSI_YELLOW + prompt + ANSI_RESET);
                return Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println(ANSI_RED + "Please enter a valid number." + ANSI_RESET);
            }
        }
    }
    
    private double getDoubleInput(String prompt) {
        while (true) {
            try {
                System.out.print(ANSI_YELLOW + prompt + ANSI_RESET);
                return Double.parseDouble(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println(ANSI_RED + "Please enter a valid number." + ANSI_RESET);
            }
        }
    }
}

// Main application class
public class BankingApplication {
    public static void main(String[] args) {
        // Load existing bank data or create new bank
        Bank bank = BankDAO.loadBank();
        
        // Create and start UI
        ConsoleUI ui = new ConsoleUI(bank);
        ui.start();
    }
}