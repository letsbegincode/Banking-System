package banking.report.analytics;

import banking.account.Account;
import banking.transaction.BaseTransaction;
import banking.transaction.DepositTransaction;
import banking.transaction.InterestTransaction;
import banking.transaction.TransferReceiveTransaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Summarises the configured range to surface KPI-friendly totals.
 */
public class RangeAnalyticsService {

    public RangeSummary summarize(List<Account> accounts, AnalyticsRange range) {
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(range, "range");

        int activeAccounts = 0;
        int accountsOpened = 0;
        int totalTransactions = 0;
        double totalCredits = 0.0;
        double totalDebits = 0.0;
        Map<LocalDate, Integer> volumeByDay = new HashMap<>();

        for (Account account : accounts) {
            LocalDate created = LocalDate.parse(account.getCreationDate());
            if (range.contains(created)) {
                accountsOpened++;
            }

            int accountTransactions = 0;
            for (BaseTransaction transaction : account.getTransactions()) {
                LocalDateTime timestamp = transaction.getTimestamp();
                if (timestamp.isBefore(range.getStartTimestamp()) || timestamp.isAfter(range.getEndTimestamp())) {
                    continue;
                }
                accountTransactions++;
                totalTransactions++;

                LocalDate day = timestamp.toLocalDate();
                volumeByDay.merge(day, 1, Integer::sum);

                double amount = transaction.getAmount();
                if (transaction instanceof DepositTransaction
                        || transaction instanceof TransferReceiveTransaction
                        || transaction instanceof InterestTransaction) {
                    totalCredits += amount;
                } else {
                    totalDebits += amount;
                }
            }

            if (accountTransactions > 0) {
                activeAccounts++;
            }
        }

        double netCashFlow = totalCredits - totalDebits;
        double averageTransactionValue = totalTransactions == 0
                ? 0.0
                : (totalCredits + totalDebits) / totalTransactions;

        LocalDate peakDay = null;
        int peakVolume = 0;
        for (Map.Entry<LocalDate, Integer> entry : volumeByDay.entrySet()) {
            if (entry.getValue() > peakVolume || (entry.getValue() == peakVolume && peakDay != null
                    && entry.getKey().isBefore(peakDay))) {
                peakDay = entry.getKey();
                peakVolume = entry.getValue();
            } else if (peakDay == null) {
                peakDay = entry.getKey();
                peakVolume = entry.getValue();
            }
        }

        return new RangeSummary(range,
                activeAccounts,
                accountsOpened,
                totalTransactions,
                round(totalCredits),
                round(totalDebits),
                round(netCashFlow),
                round(averageTransactionValue),
                peakDay,
                peakVolume);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
