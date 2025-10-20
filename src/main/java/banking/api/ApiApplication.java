package banking.api;

import banking.persistence.BankDAO;
import banking.security.AuthenticationService;
import banking.security.AuthorizationService;
import banking.security.CredentialStore;
import banking.security.OperatorCredential;
import banking.security.PasswordHasher;
import banking.security.Role;
import banking.security.TokenService;
import banking.service.Bank;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

public final class ApiApplication {
    private static final int DEFAULT_PORT = 8080;

    private ApiApplication() {
    }

    public static void main(String[] args) {
        Bank bank = BankDAO.loadBank();
        PasswordHasher hasher = new PasswordHasher();
        CredentialStore credentialStore = bootstrapCredentials(hasher);
        TokenService tokenService = new TokenService();
        AuthorizationService authorizationService = new AuthorizationService();
        AuthenticationService authenticationService = new AuthenticationService(
                credentialStore, hasher, tokenService, Duration.ofHours(2));

        int port = resolvePort();
        BankHttpServer server = new BankHttpServer(bank, port, authenticationService, tokenService, authorizationService);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

    private static CredentialStore bootstrapCredentials(PasswordHasher hasher) {
        CredentialStore store = new CredentialStore();
        store.store(new OperatorCredential("admin", hasher.hash("admin123!"), Set.of(Role.ADMIN)));
        store.store(new OperatorCredential("teller", hasher.hash("teller123!"), Set.of(Role.TELLER)));
        store.store(new OperatorCredential("auditor", hasher.hash("auditor123!"), Set.of(Role.AUDITOR)));
        return store;
    }

    private static int resolvePort() {
        return Optional.ofNullable(System.getenv("BANKING_API_PORT"))
                .filter(value -> !value.isBlank())
                .map(String::trim)
                .map(ApiApplication::parsePort)
                .orElse(DEFAULT_PORT);
    }

    private static int parsePort(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 && parsed <= 65535 ? parsed : DEFAULT_PORT;
        } catch (NumberFormatException ex) {
            return DEFAULT_PORT;
        }
    }
}
