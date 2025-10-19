package banking.security;

import java.time.Duration;
import java.util.Objects;

/**
 * Authenticates operators and issues tokens.
 */
public class AuthenticationService {
    private final CredentialStore credentialStore;
    private final PasswordHasher hasher;
    private final TokenService tokenService;
    private final Duration defaultTtl;

    public AuthenticationService(CredentialStore credentialStore, PasswordHasher hasher,
                                 TokenService tokenService, Duration defaultTtl) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.defaultTtl = Objects.requireNonNull(defaultTtl, "defaultTtl");
    }

    public AuthenticationToken login(String username, String password) {
        OperatorCredential credential = credentialStore.find(username)
            .orElseThrow(() -> new AuthenticationException("Unknown operator"));
        if (!hasher.verify(password, credential.passwordHash())) {
            throw new AuthenticationException("Invalid credentials");
        }
        return tokenService.issueToken(credential.username(), credential.roles(), defaultTtl);
    }

    public void logout(String token) {
        tokenService.revoke(token);
    }

    public TokenService tokenService() {
        return tokenService;
    }
}
