package banking.api;

import banking.persistence.BankDAO;
import banking.service.Bank;

import java.io.IOException;

public final class ApiApplication {
    private static final int DEFAULT_PORT = 8080;

    private ApiApplication() {
    }

    public static void main(String[] args) throws IOException {
        Bank bank = BankDAO.loadBank();
        int port = resolvePort();
        BankHttpServer server = new BankHttpServer(bank, port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

    private static int resolvePort() {
        String override = System.getenv("BANKING_API_PORT");
        if (override == null || override.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(override);
        } catch (NumberFormatException ex) {
            return DEFAULT_PORT;
        }
    }
}
