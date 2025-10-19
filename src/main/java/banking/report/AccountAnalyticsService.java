package banking.report;

import banking.transaction.BaseTransaction;
import banking.transaction.DepositTransaction;
import banking.transaction.InterestTransaction;
import banking.transaction.TransferReceiveTransaction;
import banking.transaction.TransferTransaction;
import banking.transaction.WithdrawalTransaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class AccountAnalyticsService {

    public AnalyticsReport analyze(List<AccountSnapshot> snapshots, AnalyticsReportRequest request) {
        Objects.requireNonNull(snapshots, "snapshots");
        Objects.requireNonNull(request, "request");
        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();

        Map<Integer, Double> netChangeByAccount = new HashMap<>();
        Map<Integer, Double> closingBalanceByAccount = new HashMap<>();
        Map<LocalDate, Double> netChangeByDate = new TreeMap<>();

        double totalBalance = 0.0;
        double totalInflow = 0.0;
        double totalOutflow = 0.0;

        for (AccountSnapshot snapshot : snapshots) {
            closingBalanceByAccount.put(snapshot.accountNumber(), round(snapshot.balance()));
            totalBalance += snapshot.balance();

            double netChange = 0.0;
            for (BaseTransaction transaction : snapshot.transactions()) {
                LocalDateTime timestamp = transaction.getTimestamp();
                if (timestamp.toLocalDate().isBefore(start) || timestamp.toLocalDate().isAfter(end)) {
                    continue;
                }

                double signedAmount = signedAmount(transaction);
                netChange += signedAmount;
                LocalDate date = timestamp.toLocalDate();
                netChangeByDate.merge(date, signedAmount, Double::sum);
                if (signedAmount >= 0) {
                    totalInflow += signedAmount;
                } else {
                    totalOutflow += Math.abs(signedAmount);
                }

            }
            netChangeByAccount.put(snapshot.accountNumber(), round(netChange));
        }

        List<BalanceSnapshot> balanceSnapshots = new ArrayList<>();
        for (AccountSnapshot snapshot : snapshots) {
            balanceSnapshots.add(new BalanceSnapshot(
                    snapshot.accountNumber(),
                    snapshot.accountHolder(),
                    snapshot.accountType(),
                    round(closingBalanceByAccount.getOrDefault(snapshot.accountNumber(), 0.0)),
                    round(netChangeByAccount.getOrDefault(snapshot.accountNumber(), 0.0))));
        }
        balanceSnapshots.sort(Comparator.comparing(BalanceSnapshot::accountNumber));

        List<TrendPoint> trendPoints = buildTrendPoints(start, end, netChangeByDate, request.getRollingWindowDays());
        List<AnomalyInsight> anomalies = detectAnomalies(balanceSnapshots, snapshots, request);

        DoubleSummaryStatistics statistics = balanceSnapshots.stream()
                .mapToDouble(BalanceSnapshot::closingBalance)
                .summaryStatistics();
        double averageBalance = statistics.getCount() > 0 ? statistics.getAverage() : 0.0;
        double medianBalance = calculateMedian(balanceSnapshots);

        return new AnalyticsReport(
                start,
                end,
                round(totalBalance),
                round(averageBalance),
                round(medianBalance),
                round(totalInflow),
                round(totalOutflow),
                balanceSnapshots,
                trendPoints,
                anomalies);
    }

    private List<TrendPoint> buildTrendPoints(LocalDate start,
            LocalDate end,
            Map<LocalDate, Double> netChangeByDate,
            int rollingWindowDays) {
        List<TrendPoint> points = new ArrayList<>();
        List<Double> window = new ArrayList<>();
        LocalDate date = start;
        while (!date.isAfter(end)) {
            double net = netChangeByDate.getOrDefault(date, 0.0);
            window.add(net);
            if (window.size() > rollingWindowDays) {
                window.remove(0);
            }
            double rollingAverage = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            points.add(new TrendPoint(date, round(net), round(rollingAverage)));
            date = date.plusDays(1);
        }
        return points;
    }

    private List<AnomalyInsight> detectAnomalies(List<BalanceSnapshot> balances,
            List<AccountSnapshot> snapshots,
            AnalyticsReportRequest request) {
        Map<Integer, BalanceSnapshot> balanceByAccount = new HashMap<>();
        for (BalanceSnapshot snapshot : balances) {
            balanceByAccount.put(snapshot.accountNumber(), snapshot);
        }

        List<AnomalyInsight> anomalies = new ArrayList<>();
        for (AccountSnapshot snapshot : snapshots) {
            BalanceSnapshot balanceSnapshot = balanceByAccount.get(snapshot.accountNumber());
            if (balanceSnapshot != null && balanceSnapshot.closingBalance() < 0) {
                anomalies.add(new AnomalyInsight(
                        snapshot.accountNumber(),
                        snapshot.accountHolder(),
                        "Account balance is negative",
                        round(balanceSnapshot.closingBalance())));
            }

            for (BaseTransaction transaction : snapshot.transactions()) {
                LocalDateTime timestamp = transaction.getTimestamp();
                LocalDate date = timestamp.toLocalDate();
                if (date.isBefore(request.getStartDate()) || date.isAfter(request.getEndDate())) {
                    continue;
                }
                double amount = Math.abs(transaction.getAmount());
                if (amount >= request.getLargeTransactionThreshold()) {
                    anomalies.add(new AnomalyInsight(
                            snapshot.accountNumber(),
                            snapshot.accountHolder(),
                            "High value transaction detected: " + transaction.getType(),
                            round(amount)));
                }
            }
        }

        anomalies.sort(Comparator.comparing(AnomalyInsight::accountNumber));
        return anomalies;
    }

    private double calculateMedian(List<BalanceSnapshot> balances) {
        if (balances.isEmpty()) {
            return 0.0;
        }
        List<Double> values = new ArrayList<>();
        for (BalanceSnapshot snapshot : balances) {
            values.add(snapshot.closingBalance());
        }
        Collections.sort(values);
        int middle = values.size() / 2;
        if (values.size() % 2 == 0) {
            return (values.get(middle - 1) + values.get(middle)) / 2.0;
        }
        return values.get(middle);
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
