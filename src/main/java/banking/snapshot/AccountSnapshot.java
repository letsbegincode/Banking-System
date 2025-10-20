package banking.snapshot;

import banking.account.Account;
import banking.account.CurrentAccount;
import banking.account.FixedDepositAccount;
import banking.account.SavingsAccount;
import banking.transaction.BaseTransaction;
import banking.transaction.DepositTransaction;
import banking.transaction.InterestTransaction;
import banking.transaction.TransferReceiveTransaction;
import banking.transaction.TransferTransaction;
import banking.transaction.WithdrawalTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Snapshot of an account used for persistence and migration.
 */
public record AccountSnapshot(String accountType, int accountNumber, String userName, double balance,
        String creationDate, Double minimumBalance, Double overdraftLimit, Integer termMonths, String maturityDate,
        List<TransactionSnapshot> transactions) {
    public AccountSnapshot {
        Objects.requireNonNull(accountType, "accountType");
        Objects.requireNonNull(userName, "userName");
        Objects.requireNonNull(creationDate, "creationDate");
        Objects.requireNonNull(transactions, "transactions");
        transactions = List.copyOf(transactions);
    }

    public static AccountSnapshot fromAccount(Account account) {
        Objects.requireNonNull(account, "account");

        List<TransactionSnapshot> transactionSnapshots = new ArrayList<>();
        for (BaseTransaction transaction : account.getTransactions()) {
            transactionSnapshots.add(toSnapshot(transaction));
        }

        Double minimumBalance = null;
        Double overdraftLimit = null;
        Integer termMonths = null;
        String maturityDate = null;

        if (account instanceof SavingsAccount savingsAccount) {
            minimumBalance = savingsAccount.getMinimumBalance();
        } else if (account instanceof CurrentAccount currentAccount) {
            overdraftLimit = currentAccount.getOverdraftLimit();
        } else if (account instanceof FixedDepositAccount fixedDepositAccount) {
            termMonths = fixedDepositAccount.getTermMonths();
            maturityDate = fixedDepositAccount.getMaturityDate().toString();
        }

        return new AccountSnapshot(canonicalAccountType(account), account.getAccountNumber(), account.getUserName(),
                account.getBalance(), account.getCreationDate(), minimumBalance, overdraftLimit, termMonths,
                maturityDate, transactionSnapshots);
    }

    private static TransactionSnapshot toSnapshot(BaseTransaction transaction) {
        TransactionType type;
        Integer sourceAccount = null;
        Integer targetAccount = null;

        if (transaction instanceof DepositTransaction) {
            type = TransactionType.DEPOSIT;
        } else if (transaction instanceof WithdrawalTransaction) {
            type = TransactionType.WITHDRAWAL;
        } else if (transaction instanceof InterestTransaction) {
            type = TransactionType.INTEREST;
        } else if (transaction instanceof TransferTransaction transferTransaction) {
            type = TransactionType.TRANSFER_OUT;
            targetAccount = transferTransaction.getTargetAccountNumber();
        } else if (transaction instanceof TransferReceiveTransaction receiveTransaction) {
            type = TransactionType.TRANSFER_IN;
            sourceAccount = receiveTransaction.getSourceAccountNumber();
        } else {
            throw new IllegalArgumentException("Unsupported transaction type: " + transaction.getClass());
        }

        return new TransactionSnapshot(type, transaction.getAmount(), transaction.getTimestamp().toString(),
                transaction.getTransactionId(), sourceAccount, targetAccount);
    }

    private static String canonicalAccountType(Account account) {
        if (account instanceof SavingsAccount) {
            return "SAVINGS";
        }
        if (account instanceof CurrentAccount) {
            return "CURRENT";
        }
        if (account instanceof FixedDepositAccount) {
            return "FIXED_DEPOSIT";
        }
        return account.getAccountType().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String accountType;
        private Integer accountNumber;
        private String userName;
        private Double balance;
        private String creationDate;
        private Double minimumBalance;
        private Double overdraftLimit;
        private Integer termMonths;
        private String maturityDate;
        private final List<TransactionSnapshot> transactions = new java.util.ArrayList<>();

        private Builder() {
        }

        public Builder accountType(String accountType) {
            this.accountType = accountType;
            return this;
        }

        public Builder accountNumber(int accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder balance(double balance) {
            this.balance = balance;
            return this;
        }

        public Builder creationDate(String creationDate) {
            this.creationDate = creationDate;
            return this;
        }

        public Builder minimumBalance(Double minimumBalance) {
            this.minimumBalance = minimumBalance;
            return this;
        }

        public Builder overdraftLimit(Double overdraftLimit) {
            this.overdraftLimit = overdraftLimit;
            return this;
        }

        public Builder termMonths(Integer termMonths) {
            this.termMonths = termMonths;
            return this;
        }

        public Builder maturityDate(String maturityDate) {
            this.maturityDate = maturityDate;
            return this;
        }

        public Builder addTransaction(TransactionSnapshot snapshot) {
            this.transactions.add(snapshot);
            return this;
        }

        public AccountSnapshot build() {
            if (accountType == null || accountNumber == null || userName == null || balance == null
                    || creationDate == null) {
                throw new IllegalStateException("AccountSnapshot is missing required fields");
            }
            return new AccountSnapshot(accountType, accountNumber, userName, balance, creationDate, minimumBalance,
                    overdraftLimit, termMonths, maturityDate, transactions);
        }
    }
}
