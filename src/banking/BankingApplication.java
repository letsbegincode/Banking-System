package banking;

import banking.persistence.BankDAO;
import banking.service.Bank;
import banking.ui.ConsoleUI;

public final class BankingApplication {
    private BankingApplication() {
    }

    public static void main(String[] args) {
        Bank bank = BankDAO.loadBank();
        ConsoleUI ui = new ConsoleUI(bank);
        ui.start();
    }
}
