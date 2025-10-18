package banking.account;

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
}
