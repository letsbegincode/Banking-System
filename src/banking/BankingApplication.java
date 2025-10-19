package banking;

import banking.api.BankHttpServer;
import banking.persistence.BankDAO;
import banking.service.Bank;
import banking.ui.ConsoleUI;

import java.util.Arrays;

public final class BankingApplication {
    private BankingApplication() {
    }

    public static void main(String[] args) {
        Bank bank = BankDAO.loadBank();
        BankHttpServer apiServer = null;
        if (Arrays.stream(args).anyMatch(arg -> "--api".equalsIgnoreCase(arg))) {
            apiServer = new BankHttpServer(bank, 8080);
            apiServer.start();
            System.out.println("HTTP API available on port " + apiServer.getPort());
        }

        ConsoleUI ui = new ConsoleUI(bank);
        ui.start();

        if (apiServer != null) {
            apiServer.stop();
        }
    }
}
