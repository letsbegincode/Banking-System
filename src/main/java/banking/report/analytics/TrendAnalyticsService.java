package banking.report.analytics;

import banking.account.Account;
import banking.transaction.BaseTransaction;
import banking.transaction.DepositTransaction;
import banking.transaction.InterestTransaction;
import banking.transaction.TransferReceiveTransaction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Aggregates transaction activity to build per-day trend information.
 */
public class TrendAnalyticsService {

    public TrendReport generate(List<Account> accounts, AnalyticsRange range) {
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(range, "range");

        Map<LocalDate, DailyStats> statsByDate = new TreeMap<>();
        LocalDate cursor = range.getStartDate();
        while (!cursor.isAfter(range.getEndDate())) {
            statsByDate.put(cursor, new DailyStats(cursor));
            cursor = cursor.plusDays(1);
        }

        for (Account account : accounts) {
            for (BaseTransaction transaction : account.getTransactions()) {
                LocalDate date = transaction.getTimestamp().toLocalDate();
                if (!range.contains(date)) {
                    continue;
                }
                DailyStats stats = statsByDate.get(date);
                stats.transactionCount++;
                double amount = transaction.getAmount();
                if (isCredit(transaction)) {
                    stats.totalCredits += amount;
                } else {
                    stats.totalDebits += amount;
                }
            }
        }

        List<TrendPoint> points = new ArrayList<>();
        double totalCredits = 0.0;
        double totalDebits = 0.0;
        for (DailyStats stats : statsByDate.values()) {
            totalCredits += stats.totalCredits;
            totalDebits += stats.totalDebits;
            points.add(new TrendPoint(stats.date, round(stats.totalCredits), round(stats.totalDebits), stats.transactionCount));
        }

        return new TrendReport(range, points, round(totalCredits), round(totalDebits));
    }

    private boolean isCredit(BaseTransaction transaction) {
        return transaction instanceof DepositTransaction
                || transaction instanceof TransferReceiveTransaction
                || transaction instanceof InterestTransaction;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class DailyStats {
        private final LocalDate date;
        private double totalCredits;
        private double totalDebits;
        private int transactionCount;

        private DailyStats(LocalDate date) {
            this.date = date;
        }
    }
}
