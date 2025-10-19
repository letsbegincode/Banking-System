package banking.security;

import java.time.Instant;

/**
 * Represents an issued authentication token.
 */
public record AuthToken(String token, Instant expiresAt, UserPrincipal principal) {
}
