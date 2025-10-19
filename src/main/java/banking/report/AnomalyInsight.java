package banking.report;

import java.util.Objects;

public record AnomalyInsight(int accountNumber,
                             String accountHolder,
                             String description,
                             double amount) {

    public AnomalyInsight {
        Objects.requireNonNull(accountHolder, "accountHolder");
        Objects.requireNonNull(description, "description");
    }
}
