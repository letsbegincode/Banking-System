package banking.ui.presenter;

import banking.report.AccountStatement;
import banking.ui.console.ConsoleIO;

import java.text.NumberFormat;
import java.util.Locale;

public class StatementPresenter {
    private final ConsoleIO io;
    private final TransactionPresenter transactionPresenter;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);

    public StatementPresenter(ConsoleIO io, TransactionPresenter transactionPresenter) {
        this.io = io;
        this.transactionPresenter = transactionPresenter;
    }

    public void show(AccountStatement statement) {
        io.subHeading("Account Statement");
        io.printlnBold("Account: ", statement.getAccountNumber() + " - " + statement.getAccountHolder());
        io.printlnBold("Period: ", statement.getStartDate() + " to " + statement.getEndDate());
        io.printlnBold("Opening Balance: ", format(statement.getOpeningBalance()));
        io.printlnBold("Closing Balance: ", format(statement.getClosingBalance()));
        io.printlnBold("Net Activity: ", format(statement.getNetActivity()));
        io.printlnBold("Total Credits: ", format(statement.getTotalCredits()));
        io.printlnBold("Total Debits: ", format(statement.getTotalDebits()));

        io.subHeading("Transactions");
        if (statement.getTransactions().isEmpty()) {
            io.warning("No transactions recorded for the selected period.");
        } else {
            transactionPresenter.showTransactions(statement.getTransactions());
        }
    }

    public void showPreview(AccountStatement statement) {
        io.info("Statement ready for account " + statement.getAccountNumber() +
            " with " + statement.getTransactions().size() + " transactions.");
    }

    private String format(double value) {
        return currencyFormat.format(value);
    }
}
