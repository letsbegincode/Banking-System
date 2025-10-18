package banking.ui.presenter;

import banking.transaction.BaseTransaction;
import banking.ui.console.ConsoleIO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TransactionPresenter {
    private final ConsoleIO io;

    public TransactionPresenter(ConsoleIO io) {
        this.io = io;
    }

    public void showTransactions(List<BaseTransaction> transactions) {
        if (transactions.isEmpty()) {
            io.warning("No transactions found for this account.");
            return;
        }

        io.subHeading("Transaction History");
        io.printTableHeader("%-10s %-18s %-15s %-25s", "ID", "TYPE", "AMOUNT", "DATE/TIME");
        transactions.forEach(transaction -> io.println(formatTransaction(transaction)));
    }

    public void showStatement(List<BaseTransaction> transactions, String periodLabel, Predicate<BaseTransaction> filter) {
        List<BaseTransaction> filtered = transactions.stream()
            .filter(filter)
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            io.warning("No transactions found for the selected period.");
            return;
        }

        io.subHeading("Statement - " + periodLabel);
        io.printTableHeader("%-10s %-18s %-15s %-25s", "ID", "TYPE", "AMOUNT", "DATE/TIME");
        filtered.forEach(transaction -> io.println(formatTransaction(transaction)));
    }

    public void showStatement(List<BaseTransaction> transactions, String startDateInclusive, String endDateInclusive) {
        showStatement(transactions,
            startDateInclusive + " to " + endDateInclusive,
            transaction -> transaction.getDateTime().compareTo(startDateInclusive) >= 0
                && transaction.getDateTime().compareTo(endDateInclusive) <= 0);
    }

    public void showStatement(List<BaseTransaction> transactions, int monthsBack) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusMonths(monthsBack);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String startDate = start.format(formatter);
        String endDate = now.format(formatter);
        showStatement(transactions, startDate, endDate);
    }

    private String formatTransaction(BaseTransaction transaction) {
        String amount = String.format("%.2f", transaction.getAmount());
        boolean isCredit = transaction.getType().contains("Deposit")
            || transaction.getType().contains("Interest")
            || transaction.getType().contains("Received");
        String color = isCredit ? "\u001B[32m" : "\u001B[31m";
        return color + String.format("%-10s %-18s %-15s %-25s", transaction.getTransactionId(),
            transaction.getType(),
            amount,
            transaction.getDateTime()) + "\u001B[0m";
    }
}
