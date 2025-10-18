package banking.account;

import banking.transaction.BaseTransaction;
import banking.transaction.DepositTransaction;
import banking.transaction.TransferReceiveTransaction;
import banking.transaction.TransferTransaction;
import banking.transaction.WithdrawalTransaction;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class Account implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userName;
    private final int accountNumber;
    private double balance;
    private final List<BaseTransaction> transactions;
    private final String creationDate;

    protected Account(String userName, int accountNumber) {
        this.accountNumber = accountNumber;
        this.balance = 0;
        this.transactions = new ArrayList<>();
        this.creationDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        setUserName(userName);
    }

    public synchronized void deposit(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Invalid deposit amount. Please enter a positive amount.");
        }

        balance += amount;
        recordTransaction(new DepositTransaction(amount));
    }

    public synchronized boolean withdraw(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive.");
        }

        if (canWithdraw(amount)) {
            balance -= amount;
            recordTransaction(new WithdrawalTransaction(amount));
            return true;
        }
        return false;
    }

    protected abstract boolean canWithdraw(double amount);

    public abstract void addInterest();

    public abstract String getAccountType();

    public synchronized boolean transfer(double amount, Account targetAccount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive.");
        }

        if (canWithdraw(amount)) {
            balance -= amount;
            recordTransaction(new TransferTransaction(amount, targetAccount.getAccountNumber()));
            targetAccount.receiveTransfer(amount, this.accountNumber);
            return true;
        }
        return false;
    }

    protected synchronized void receiveTransfer(double amount, int sourceAccountNumber) {
        balance += amount;
        recordTransaction(new TransferReceiveTransaction(amount, sourceAccountNumber));
    }

    public synchronized List<BaseTransaction> getTransactions() {
        return Collections.unmodifiableList(new ArrayList<>(transactions));
    }

    public synchronized List<BaseTransaction> getTransactionsByType(String type) {
        return transactions.stream()
            .filter(t -> t.getType().contains(type))
            .collect(Collectors.toList());
    }

    public synchronized List<BaseTransaction> getTransactionsByDateRange(LocalDateTime startInclusive,
                                                                         LocalDateTime endInclusive) {
        LocalDateTime start = Objects.requireNonNull(startInclusive, "startInclusive");
        LocalDateTime end = Objects.requireNonNull(endInclusive, "endInclusive");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date must not be before start date.");
        }

        return transactions.stream()
            .filter(t -> !t.getTimestamp().isBefore(start) && !t.getTimestamp().isAfter(end))
            .collect(Collectors.toList());
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public synchronized String getUserName() {
        return userName;
    }

    public synchronized void setUserName(String userName) {
        String trimmedName = Objects.requireNonNull(userName, "userName").trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Account holder name cannot be blank.");
        }
        this.userName = trimmedName;
    }

    public synchronized double getBalance() {
        return balance;
    }

    protected synchronized void setBalance(double balance) {
        this.balance = balance;
    }

    public String getCreationDate() {
        return creationDate;
    }

    protected synchronized void recordTransaction(BaseTransaction transaction) {
        transactions.add(transaction);
    }
}
