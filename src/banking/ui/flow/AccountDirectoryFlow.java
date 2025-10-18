package banking.ui.flow;

import banking.account.Account;
import banking.service.Bank;
import banking.ui.console.ConsoleIO;
import banking.ui.presenter.AccountPresenter;

import java.util.List;

public class AccountDirectoryFlow {
    private final Bank bank;
    private final ConsoleIO io;
    private final AccountPresenter accountPresenter;

    public AccountDirectoryFlow(Bank bank, ConsoleIO io, AccountPresenter accountPresenter) {
        this.bank = bank;
        this.io = io;
        this.accountPresenter = accountPresenter;
    }

    public void showAllAccounts() {
        List<Account> accounts = bank.getAllAccounts();
        accountPresenter.showAccountsTable(accounts);
    }

    public void searchAccounts() {
        String keyword = io.prompt("Enter name or keyword to search: ");
        List<Account> results = bank.searchAccounts(keyword);
        accountPresenter.showSearchResults(keyword, results);
    }
}
