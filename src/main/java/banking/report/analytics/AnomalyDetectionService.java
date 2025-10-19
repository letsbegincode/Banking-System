package banking.report.analytics;

import banking.account.Account;
import banking.transaction.BaseTransaction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Flags transactions that exceed configured thresholds within the requested window.
 */
public class AnomalyDetectionService {

    public AnomalyReport detect(List<Account> accounts,
            AnalyticsRange range,
            double absoluteThreshold,
            double deviationMultiplier) {
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(range, "range");

        List<AnomalyRecord> anomalies = new ArrayList<>();
        for (Account account : accounts) {
            List<BaseTransaction> transactions = new ArrayList<>();
            for (BaseTransaction transaction : account.getTransactions()) {
                LocalDateTime timestamp = transaction.getTimestamp();
                if (timestamp.isBefore(range.getStartTimestamp()) || timestamp.isAfter(range.getEndTimestamp())) {
                    continue;
                }
                transactions.add(transaction);
            }
            if (transactions.isEmpty()) {
                continue;
            }

            double[] amounts = transactions.stream().mapToDouble(BaseTransaction::getAmount).toArray();
            double mean = mean(amounts);
            double stdDev = standardDeviation(amounts, mean);

            for (BaseTransaction transaction : transactions) {
                double amount = transaction.getAmount();
                double zScore = stdDev > 0 ? (amount - mean) / stdDev : 0.0;
                boolean exceedsAbsolute = absoluteThreshold > 0 && amount >= absoluteThreshold;
                boolean exceedsDeviation = deviationMultiplier > 0 && stdDev > 0
                        && amount >= mean + deviationMultiplier * stdDev;

                if (!exceedsAbsolute && !exceedsDeviation) {
                    continue;
                }

                StringBuilder reason = new StringBuilder();
                if (exceedsAbsolute) {
                    reason.append(String.format(Locale.US,
                            "amount %.2f exceeds threshold %.2f",
                            amount,
                            absoluteThreshold));
                }
                if (exceedsDeviation) {
                    if (reason.length() > 0) {
                        reason.append("; ");
                    }
                    reason.append(String.format(Locale.US,
                            "z-score %.2f beyond %.2f std-dev",
                            zScore,
                            deviationMultiplier));
                }

                anomalies.add(new AnomalyRecord(account.getAccountNumber(),
                        account.getUserName(),
                        transaction.getType(),
                        round(amount),
                        transaction.getTimestamp(),
                        round(zScore),
                        reason.toString()));
            }
        }

        anomalies.sort(Comparator.comparing(AnomalyRecord::getTimestamp).reversed());
        return new AnomalyReport(range, absoluteThreshold, deviationMultiplier, anomalies);
    }

    private double mean(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private double standardDeviation(double[] values, double mean) {
        if (values.length < 2) {
            return 0.0;
        }
        double variance = 0.0;
        for (double value : values) {
            double diff = value - mean;
            variance += diff * diff;
        }
        variance /= values.length;
        return Math.sqrt(variance);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
