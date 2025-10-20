package banking.account;

import banking.snapshot.AccountSnapshot;
import banking.transaction.BaseTransaction;
import banking.transaction.TransactionFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import banking.util.DateTimeParsers;

public final class AccountFactory {
    private AccountFactory() {
    }

    public static Account createAccount(String accountType, String userName, int accountNumber, double initialDeposit) {
        switch (accountType.toLowerCase()) {
            case "savings":
                Account savings = new SavingsAccount(userName, accountNumber);
                if (initialDeposit > 0) {
                    savings.deposit(initialDeposit);
                }
                return savings;
            case "current":
                Account current = new CurrentAccount(userName, accountNumber);
                if (initialDeposit > 0) {
                    current.deposit(initialDeposit);
                }
                return current;
            case "fixed":
            case "fd":
                return new FixedDepositAccount(userName, accountNumber, initialDeposit, 12);
            default:
                throw new IllegalArgumentException("Unknown account type: " + accountType);
        }
    }

    public static Account restoreAccount(AccountSnapshot snapshot) {
        List<BaseTransaction> transactions = new ArrayList<>(snapshot.transactions().size());
        for (var transactionSnapshot : snapshot.transactions()) {
            transactions.add(TransactionFactory.fromSnapshot(transactionSnapshot));
        }

        String accountType = snapshot.accountType().toUpperCase(Locale.ROOT);
        switch (accountType) {
            case "SAVINGS" -> {
                double minimumBalance = snapshot.minimumBalance() != null ? snapshot.minimumBalance() : 1000;
                return new SavingsAccount(snapshot.userName(), snapshot.accountNumber(), snapshot.creationDate(),
                        snapshot.balance(), minimumBalance, transactions);
            }
            case "CURRENT" -> {
                double overdraftLimit = snapshot.overdraftLimit() != null ? snapshot.overdraftLimit() : 10000;
                return new CurrentAccount(snapshot.userName(), snapshot.accountNumber(), snapshot.creationDate(),
                        snapshot.balance(), overdraftLimit, transactions);
            }
            case "FIXED_DEPOSIT" -> {
                int termMonths = snapshot.termMonths() != null ? snapshot.termMonths() : 12;
                String maturityDate = snapshot.maturityDate();
                LocalDateTime maturity = maturityDate != null ? DateTimeParsers.parseTransactionTimestamp(maturityDate)
                        : LocalDateTime.now().plusMonths(termMonths);
                return new FixedDepositAccount(snapshot.userName(), snapshot.accountNumber(), snapshot.creationDate(),
                        snapshot.balance(), termMonths, maturity, transactions);
            }
            default -> throw new IllegalArgumentException("Unknown account type in snapshot: " + snapshot.accountType());
        }
    }
}
