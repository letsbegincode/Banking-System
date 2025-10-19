package banking.report;

import banking.account.Account;
import banking.transaction.BaseTransaction;

import java.util.List;
import java.util.Objects;

public record AccountSnapshot(int accountNumber,
                              String accountHolder,
                              String accountType,
                              double balance,
                              List<BaseTransaction> transactions) {

    public AccountSnapshot {
        Objects.requireNonNull(accountHolder, "accountHolder");
        Objects.requireNonNull(accountType, "accountType");
        transactions = List.copyOf(transactions);
    }

    public static AccountSnapshot fromAccount(Account account) {
        Objects.requireNonNull(account, "account");
        return new AccountSnapshot(
                account.getAccountNumber(),
                account.getUserName(),
                account.getAccountType(),
                account.getBalance(),
                account.getTransactions());
    }
}
