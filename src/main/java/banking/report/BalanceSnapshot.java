package banking.report;

import java.util.Objects;

public record BalanceSnapshot(int accountNumber,
                              String accountHolder,
                              String accountType,
                              double closingBalance,
                              double netChange) {

    public BalanceSnapshot {
        Objects.requireNonNull(accountHolder, "accountHolder");
        Objects.requireNonNull(accountType, "accountType");
    }
}
