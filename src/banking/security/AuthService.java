package banking.security;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Coordinates credential validation, token issuance, and role checks.
 */
public final class AuthService {
    private final CredentialStore credentialStore;
    private final TokenService tokenService;

    public AuthService(CredentialStore credentialStore, TokenService tokenService) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
    }

    /**
     * Authenticates the supplied username/password pair.
     *
     * @param username user identifier
     * @param password raw password
     * @return issued auth token when credentials are valid
     */
    public Optional<AuthToken> authenticate(String username, String password) {
        return credentialStore.find(username)
            .filter(record -> CredentialHasher.verifyPassword(password, record.hashedPassword()))
            .map(record -> new UserPrincipal(username, record.role()))
            .map(tokenService::issueToken);
    }

    /**
     * Resolves a principal for the bearer token when it remains valid.
     *
     * @param token bearer token
     * @return authenticated principal if the token is active
     */
    public Optional<UserPrincipal> verifyToken(String token) {
        return tokenService.resolve(token);
    }

    /**
     * Checks that the principal grants at least one of the required roles.
     *
     * @param principal authenticated user
     * @param requiredRoles roles allowed for the resource
     * @return {@code true} when the principal satisfies the requirement
     */
    public boolean hasAnyRole(UserPrincipal principal, Role... requiredRoles) {
        if (requiredRoles == null || requiredRoles.length == 0) {
            return true;
        }
        return Arrays.stream(requiredRoles)
            .anyMatch(required -> principal.role().grants(required));
    }
}
