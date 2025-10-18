package banking.ui;

import banking.account.Account;
import banking.account.CurrentAccount;
import banking.account.FixedDepositAccount;
import banking.account.SavingsAccount;
import banking.operation.AccountOperation;
import banking.operation.DepositOperation;
import banking.operation.TransferOperation;
import banking.operation.WithdrawOperation;
import banking.persistence.BankDAO;
import banking.service.Bank;
import banking.transaction.BaseTransaction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public class ConsoleUI {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_BG_BLUE = "\u001B[44m";
    private static final String ANSI_BOLD = "\u001B[1m";
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

    private void displayAllAccounts() {
        List<Account> accounts = bank.getAllAccounts();

        if (accounts.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No accounts available in the system." + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== ALL ACCOUNTS =====" + ANSI_RESET);
        System.out.println(ANSI_CYAN
            + String.format("%-6s %-20s %-15s %-15s", "ACC#", "NAME", "TYPE", "BALANCE")
            + ANSI_RESET);

        for (Account account : accounts) {
            System.out.println(String.format("%-6d %-20s %-15s %-15.2f",
                account.getAccountNumber(),
                account.getUserName(),
                account.getAccountType(),
                account.getBalance()));
        }
    }

    private void searchAccounts() {
        String keyword = getStringInput("Enter name or keyword to search: ");
        List<Account> results = bank.searchAccounts(keyword);

        if (results.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No accounts matched your search criteria." + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_CYAN + ANSI_BOLD + "\n===== SEARCH RESULTS =====" + ANSI_RESET);
        for (Account account : results) {
            displayAccountDetails(account);
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
        pauseProcessing();
        displayAccountDetails(account);
    }

    private void performWithdrawal(Account account) {
        double amount = getDoubleInput("Enter withdrawal amount: ");
        AccountOperation operation = new WithdrawOperation(account, amount);
        bank.queueOperation(operation);
        System.out.println(ANSI_GREEN + "Withdrawal operation queued. Processing..." + ANSI_RESET);
        pauseProcessing();
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
        pauseProcessing();
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

        System.out.println(ANSI_CYAN
            + String.format("%-10s %-15s %-12s %-25s", "ID", "TYPE", "AMOUNT", "DATE/TIME")
            + ANSI_RESET);

        for (BaseTransaction transaction : transactions) {
            String amountStr = String.format("%.2f", transaction.getAmount());
            boolean isCredit = transaction.getType().contains("Deposit")
                || transaction.getType().contains("Interest")
                || transaction.getType().contains("Received");
            String colorCode = isCredit ? ANSI_GREEN : ANSI_RED;

            System.out.println(colorCode
                + String.format("%-10s %-15s %-12s %-25s",
                transaction.getTransactionId(),
                transaction.getType(),
                amountStr,
                transaction.getDateTime())
                + ANSI_RESET);
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

        String startDate;
        String endDate = now.format(formatter);

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
                System.out.println(ANSI_RED + "Invalid choice!" + ANSI_RESET);
                return;
        }

        List<BaseTransaction> filteredTransactions = account.getTransactionsByDateRange(startDate, endDate);

        if (filteredTransactions.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No transactions found for the selected period." + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_CYAN + String.format("%-10s %-15s %-12s %-25s", "ID", "TYPE", "AMOUNT", "DATE/TIME")
            + ANSI_RESET);
        for (BaseTransaction transaction : filteredTransactions) {
            System.out.println(String.format("%-10s %-15s %-12.2f %-25s",
                transaction.getTransactionId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDateTime()));
        }
    }

    private void generateReportsMenu() {
        System.out.println(ANSI_YELLOW + ANSI_BOLD + "\n===== REPORTS MENU =====" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "1. Account Summary Report");
        System.out.println("2. High-Value Accounts Report");
        System.out.println("3. Transaction Volume Report");
        System.out.println("4. Back to Main Menu" + ANSI_RESET);

        int choice = getIntInput("Select a report to generate: ");

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
        System.out.println(ANSI_BOLD + "Average Balance: " + ANSI_RESET
            + String.format("%.2f", totalBalance / totalAccounts));
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
        System.out.println(ANSI_BOLD + "Accounts with balance >= " + threshold + ": " + highValueAccounts.size()
            + ANSI_RESET);

        System.out.println(ANSI_CYAN
            + String.format("%-6s %-20s %-15s %-15s %-15s", "ACC#", "NAME", "TYPE", "BALANCE", "CREATED")
            + ANSI_RESET);

        for (Account account : highValueAccounts) {
            System.out.println(String.format("%-6d %-20s %-15s %-15.2f %-15s",
                account.getAccountNumber(),
                account.getUserName(),
                account.getAccountType(),
                account.getBalance(),
                account.getCreationDate()));
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
            .forEach(entry -> System.out.println(ANSI_BOLD + entry.getKey() + ": " + ANSI_RESET + entry.getValue()));
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
        System.out.println(ANSI_BOLD + "Creation Date: " + ANSI_RESET + account.getCreationDate());

        if (account instanceof SavingsAccount) {
            SavingsAccount savingsAccount = (SavingsAccount) account;
            System.out.println(ANSI_BOLD + "Minimum Balance: " + ANSI_RESET
                + String.format("%.2f", savingsAccount.getMinimumBalance()));
        } else if (account instanceof CurrentAccount) {
            CurrentAccount currentAccount = (CurrentAccount) account;
            System.out.println(ANSI_BOLD + "Overdraft Limit: " + ANSI_RESET
                + String.format("%.2f", currentAccount.getOverdraftLimit()));
        } else if (account instanceof FixedDepositAccount) {
            FixedDepositAccount fdAccount = (FixedDepositAccount) account;
            System.out.println(ANSI_BOLD + "Maturity Date: " + ANSI_RESET + fdAccount.getFormattedMaturityDate());
        }
    }

    private void pauseProcessing() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
