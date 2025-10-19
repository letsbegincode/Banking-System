package banking;

import banking.persistence.BankDAO;
import banking.security.AuthService;
import banking.security.CredentialStore;
import banking.security.Role;
import banking.security.TokenService;
import banking.service.Bank;
import banking.ui.ConsoleUI;

import java.time.Duration;

public final class BankingApplication {
    private BankingApplication() {
    }

    public static void main(String[] args) {
        Bank bank = BankDAO.loadBank();
        CredentialStore credentialStore = new CredentialStore();
        credentialStore.register("operator", "operator123", Role.OPERATOR);
        credentialStore.register("customer", "customer123", Role.CUSTOMER);
        AuthService authService = new AuthService(credentialStore, new TokenService(Duration.ofMinutes(30)));
        ConsoleUI ui = new ConsoleUI(bank, authService);
        ui.start();
    }
}
