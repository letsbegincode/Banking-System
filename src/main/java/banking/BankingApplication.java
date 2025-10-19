package banking;

import banking.api.BankHttpServer;
import banking.persistence.BankDAO;
import banking.security.AuthenticationService;
import banking.security.AuthorizationService;
import banking.security.CredentialStore;
import banking.security.OperatorCredential;
import banking.security.PasswordHasher;
import banking.security.Role;
import banking.security.TokenService;
import banking.service.Bank;
import banking.ui.ConsoleUI;

import java.time.Duration;
import java.util.Set;

public final class BankingApplication {
    private BankingApplication() {
    }

    public static void main(String[] args) {
        Bank bank = BankDAO.loadBank();
        PasswordHasher hasher = new PasswordHasher();
        CredentialStore credentialStore = bootstrapCredentials(hasher);
        TokenService tokenService = new TokenService();
        AuthorizationService authorizationService = new AuthorizationService();
        AuthenticationService authenticationService = new AuthenticationService(
            credentialStore, hasher, tokenService, Duration.ofHours(2));
        BankHttpServer httpServer = new BankHttpServer(bank, 8080, tokenService, authorizationService);
        ConsoleUI ui = new ConsoleUI(bank, authenticationService, tokenService, httpServer);
        ui.start();
    }

    private static CredentialStore bootstrapCredentials(PasswordHasher hasher) {
        CredentialStore store = new CredentialStore();
        store.store(new OperatorCredential("admin", hasher.hash("admin123!"), Set.of(Role.ADMIN)));
        store.store(new OperatorCredential("teller", hasher.hash("teller123!"), Set.of(Role.TELLER)));
        store.store(new OperatorCredential("auditor", hasher.hash("auditor123!"), Set.of(Role.AUDITOR)));
        return store;
    }
}
