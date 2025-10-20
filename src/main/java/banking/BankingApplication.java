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
import java.util.Optional;
import java.util.Set;

/**
 * Application entrypoint wiring together persistence, security, console UI and
 * the optional HTTP gateway.
 */
public final class BankingApplication {
    private static final String PORT_PROPERTY = "banking.http.port";
    private static final String PORT_ENV = "BANKING_HTTP_PORT";

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

        int port = resolvePort().orElse(8080);
        BankHttpServer httpServer = new BankHttpServer(bank, port, authenticationService, tokenService,
                authorizationService);

        ConsoleUI ui = new ConsoleUI(bank, authenticationService, tokenService, httpServer);
        ui.start();
    }

    private static Optional<Integer> resolvePort() {
        String system = System.getProperty(PORT_PROPERTY);
        if (system != null && !system.isBlank()) {
            try {
                return Optional.of(Integer.parseInt(system.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        String env = System.getenv(PORT_ENV);
        if (env != null && !env.isBlank()) {
            try {
                return Optional.of(Integer.parseInt(env.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return Optional.empty();
    }

    private static CredentialStore bootstrapCredentials(PasswordHasher hasher) {
        CredentialStore store = new CredentialStore();
        store.store(new OperatorCredential("admin", hasher.hash("admin123!"), Set.of(Role.ADMIN)));
        store.store(new OperatorCredential("teller", hasher.hash("teller123!"), Set.of(Role.TELLER)));
        store.store(new OperatorCredential("auditor", hasher.hash("auditor123!"), Set.of(Role.AUDITOR)));
        return store;
    }
}
