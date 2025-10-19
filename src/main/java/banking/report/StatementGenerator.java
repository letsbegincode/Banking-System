package banking.report;

import banking.account.Account;
import banking.transaction.BaseTransaction;
import banking.transaction.DepositTransaction;
import banking.transaction.InterestTransaction;
import banking.transaction.TransferReceiveTransaction;
import banking.transaction.TransferTransaction;
import banking.transaction.WithdrawalTransaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class StatementGenerator {
    public AccountStatement generate(Account account, LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(account, "account");
        LocalDate start = Objects.requireNonNull(startDate, "startDate");
        LocalDate end = Objects.requireNonNull(endDate, "endDate");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date must be on or after the start date.");
        }

        LocalDateTime startTimestamp = start.atStartOfDay();
        LocalDateTime endTimestamp = end.plusDays(1).atStartOfDay().minusNanos(1);

        List<BaseTransaction> allTransactions = new ArrayList<>(account.getTransactions());
        allTransactions.sort(Comparator.comparing(BaseTransaction::getTimestamp));

        double openingBalance = 0.0;
        double closingBalance = 0.0;
        double totalCredits = 0.0;
        double totalDebits = 0.0;

        List<BaseTransaction> periodTransactions = new ArrayList<>();

        for (BaseTransaction transaction : allTransactions) {
            double signedAmount = signedAmount(transaction);
            LocalDateTime timestamp = transaction.getTimestamp();

            if (timestamp.isBefore(startTimestamp)) {
                openingBalance += signedAmount;
                closingBalance = openingBalance;
                continue;
            }

            if (timestamp.isAfter(endTimestamp)) {
                break;
            }

            closingBalance += signedAmount;
            if (signedAmount >= 0) {
                totalCredits += signedAmount;
            } else {
                totalDebits += Math.abs(signedAmount);
            }

            periodTransactions.add(transaction);
        }

        return new AccountStatement(
            account.getAccountNumber(),
            account.getUserName(),
            start,
            end,
            round(openingBalance),
            round(closingBalance),
            round(totalCredits),
            round(totalDebits),
            periodTransactions
        );
    }

    private double signedAmount(BaseTransaction transaction) {
        if (transaction instanceof DepositTransaction
            || transaction instanceof TransferReceiveTransaction
            || transaction instanceof InterestTransaction) {
            return transaction.getAmount();
        }

        if (transaction instanceof WithdrawalTransaction || transaction instanceof TransferTransaction) {
            return -transaction.getAmount();
        }

        return transaction.getAmount();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
