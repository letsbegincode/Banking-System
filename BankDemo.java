import java.io.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;

class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private double amount;
    private String type;
    private String dateTime;

    public Transaction(double amount, String type) {
        this.amount = amount;
        this.type = type;
        this.dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Override
    public String toString() {
        return "Amount: " + amount + ", Type: " + type + ", Date and Time: " + dateTime;
    }
}

class BankAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userName;
    private int accountNumber;
    private double balance;
    private String accountType; // Added accountType field
    private ArrayList<Transaction> transactions;

    public BankAccount(String userName, int accountNumber, String accountType) {
        this.userName = userName;
        this.accountNumber = accountNumber;
        this.balance = 0;
        this.accountType = accountType;
        this.transactions = new ArrayList<>();
    }

    public synchronized void deposit(double amount) {
        if (amount > 0) {
            balance += amount;
            transactions.add(new Transaction(amount, "Deposit"));
        } else {
            System.out.println("Invalid deposit amount. Please enter a positive amount.");
        }
    }

    public synchronized void withdraw(double amount) {
        if (amount > 0 && amount <= balance) {
            balance -= amount;
            transactions.add(new Transaction(amount, "Withdrawal"));
        } else {
            System.out
                    .println("Invalid withdrawal amount. Please enter a positive amount and ensure sufficient funds.");
        }
    }

    // Added method to calculate and add interest for savings account
    public synchronized void addInterest() {
        if (accountType.equalsIgnoreCase("savings")) {
            double interest = balance * 0.04; // 4% annual interest
            balance += interest;
            transactions.add(new Transaction(interest, "Interest Added"));
        }
    }

    public void displayTransactions() {
        System.out.println("Transaction history for account number " + accountNumber + ":");
        for (Transaction transaction : transactions) {
            System.out.println(transaction);
        }
    }

    @Override
    public String toString() {
        return "User: " + userName + ", Account Number: " + accountNumber + ", Balance: " + balance + ", Type: "
                + accountType;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getUserName() {
        return userName;
    }

    public double getBalance() {
        return balance;
    }

    public String getAccountType() {
        return accountType;
    }
}

class BankingSystem implements Serializable {
    private static final long serialVersionUID = 1L;

    private ArrayList<BankAccount> accounts;

    public BankingSystem() {
        this.accounts = new ArrayList<>();
    }

    public void addAccount(BankAccount account) {
        accounts.add(account);
    }

    public BankAccount getAccount(int accountNumber) {
        for (BankAccount account : accounts) {
            if (account.getAccountNumber() == accountNumber) {
                return account;
            }
        }
        return null;
    }

    public boolean deleteAccount(int accountNumber) {
        Iterator<BankAccount> iterator = accounts.iterator();
        while (iterator.hasNext()) {
            BankAccount account = iterator.next();
            if (account.getAccountNumber() == accountNumber) {
                iterator.remove();
                return true;
            }
        }
        System.out.println("Account not found.");
        return false;
    }

    public void displayAllAccounts() {
        System.out.println("All Bank Accounts:");
        for (BankAccount account : accounts) {
            System.out.println(account);
        }
    }
}

public class BankDemo {
    private static final SecureRandom secureRandom = new SecureRandom();

    public static void main(String[] args) {
        BankingSystem bankingSystem = deserializeBankingSystem();
        if (bankingSystem == null) {
            bankingSystem = new BankingSystem();
        }

        Scanner input = new Scanner(System.in);

        System.out.println("\n\nWelcome to Banking System");
        System.out.println("Press 1: Create Account");
        System.out.println("Press 2: Deposit");
        System.out.println("Press 3: Withdraw");
        System.out.println("Press 4: Display Balance and Transactions");
        System.out.println("Press 5: Display All Accounts");
        System.out.println("Press 6: Delete Account");
        System.out.println("Press 7: Exit");
        while (true) {
            System.out.print("Choose the option: ");
            int choice = input.nextInt();

            switch (choice) {
                case 1:
                    System.out.print("Enter the user Name: ");
                    String name = input.next();
                    System.out.print("Enter the initial balance (can be 0): Rs ");
                    double initialBalance = input.nextDouble();
                    while (initialBalance < 0) {
                        System.out.println(
                                "Initial balance cannot be negative. Please enter a non-negative initial balance: Rs ");
                        initialBalance = input.nextDouble();
                    }
                    System.out.print("Enter account type (Savings or Current): ");
                    String accountType = input.next();

                    BankAccount newAccount = new BankAccount(name, generateAccountNumber(), accountType);
                    newAccount.deposit(initialBalance);
                    bankingSystem.addAccount(newAccount);
                    System.out
                            .println("Account created successfully. Account Number: " + newAccount.getAccountNumber());
                    serializeBankingSystem(bankingSystem);
                    break;

                case 2:
                    performTransaction(input, bankingSystem, "Deposit");
                    break;

                case 3:
                    performTransaction(input, bankingSystem, "Withdraw");
                    serializeBankingSystem(bankingSystem);
                    break;

                case 4:
                    displayAccountDetails(input, bankingSystem);
                    break;

                case 5:
                    bankingSystem.displayAllAccounts();
                    break;

                case 6:
                    deleteAccount(input, bankingSystem);
                    serializeBankingSystem(bankingSystem);
                    break;

                case 7:
                    serializeBankingSystem(bankingSystem);
                    System.exit(0);

                default:
                    System.out.println("Kindly choose a valid input.");
                    break;

            }
            System.out.println("");
        }
    }

    private static void performTransaction(Scanner input, BankingSystem bankingSystem, String transactionType) {
        System.out.print("Enter account number: ");
        int accNumber = input.nextInt();
        BankAccount selectedAccount = bankingSystem.getAccount(accNumber);

        if (selectedAccount != null) {
            double amount = -1;
            while (amount < 0) {
                System.out.print("Enter the amount to " + transactionType.toLowerCase() + ": Rs ");
                amount = input.nextDouble();
                if (amount < 0) {
                    System.out.println("Kindly enter a valid amount.");
                }
            }

            if (transactionType.equals("Deposit")) {
                final double depositAmount = amount; // Create a final variable
                Thread depositThread = new Thread(() -> {
                    selectedAccount.deposit(depositAmount); // Use the final variable
                    // Add interest for savings account after deposit
                    selectedAccount.addInterest();
                });
                depositThread.start();
                try {
                    depositThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                final double withdrawAmount = amount; // Create a final variable
                Thread withdrawThread = new Thread(() -> {
                    selectedAccount.withdraw(withdrawAmount); // Use the final variable
                    // Add interest for savings account after withdrawal
                    selectedAccount.addInterest();
                });
                withdrawThread.start();
                try {
                    withdrawThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println(transactionType + " successful.");
            System.out.println("Updated account details:");
            System.out.println(selectedAccount);
            serializeBankingSystem(bankingSystem);
        } else {
            System.out.println("Account not found.");
        }
    }

    private static void displayAccountDetails(Scanner input, BankingSystem bankingSystem) {
        System.out.print("Enter account number: ");
        int accNumber = input.nextInt();
        BankAccount selectedAccount = bankingSystem.getAccount(accNumber);

        if (selectedAccount != null) {
            System.out.println("Account Details:");
            System.out.println(selectedAccount);
            System.out.println("Transaction History:");
            selectedAccount.displayTransactions();
        } else {
            System.out.println("Account not found.");
        }
    }

    private static void deleteAccount(Scanner input, BankingSystem bankingSystem) {
        System.out.print("Enter account number to delete: ");
        int accNumber = input.nextInt();

        BankAccount accountToDelete = bankingSystem.getAccount(accNumber);

        if (accountToDelete != null) {
            bankingSystem.deleteAccount(accNumber);
            System.out.println("Account deleted successfully.");
        } else {
            System.out.println("Account not found.");
        }
    }

    private static void serializeBankingSystem(BankingSystem bankingSystem) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("banking_system.ser"))) {
            oos.writeObject(bankingSystem);
            System.out.println("Banking system data has been serialized.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static BankingSystem deserializeBankingSystem() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("banking_system.ser"))) {
            return (BankingSystem) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            // e.printStackTrace();
            return null;
        }
    }

    private static int generateAccountNumber() {
        return 100000 + secureRandom.nextInt(900000);
    }
}
