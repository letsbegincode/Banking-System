package banking.report;

import banking.transaction.BaseTransaction;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AccountStatement {
    private final int accountNumber;
    private final String accountHolder;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final double openingBalance;
    private final double closingBalance;
    private final double totalCredits;
    private final double totalDebits;
    private final List<BaseTransaction> transactions;

    public AccountStatement(int accountNumber,
                            String accountHolder,
                            LocalDate startDate,
                            LocalDate endDate,
                            double openingBalance,
                            double closingBalance,
                            double totalCredits,
                            double totalDebits,
                            List<BaseTransaction> transactions) {
        this.accountNumber = accountNumber;
        this.accountHolder = Objects.requireNonNull(accountHolder, "accountHolder");
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        this.endDate = Objects.requireNonNull(endDate, "endDate");
        this.openingBalance = openingBalance;
        this.closingBalance = closingBalance;
        this.totalCredits = totalCredits;
        this.totalDebits = totalDebits;
        this.transactions = Collections.unmodifiableList(List.copyOf(transactions));
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public double getOpeningBalance() {
        return openingBalance;
    }

    public double getClosingBalance() {
        return closingBalance;
    }

    public double getTotalCredits() {
        return totalCredits;
    }

    public double getTotalDebits() {
        return totalDebits;
    }

    public List<BaseTransaction> getTransactions() {
        return transactions;
    }

    public double getNetActivity() {
        return closingBalance - openingBalance;
    }
}
