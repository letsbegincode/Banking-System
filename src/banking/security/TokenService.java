package banking.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and validates opaque bearer tokens with an in-memory backing store.
 */
public final class TokenService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Duration tokenTtl;
    private final Clock clock;

    public TokenService(Duration tokenTtl) {
        this(tokenTtl, Clock.systemUTC());
    }

    public TokenService(Duration tokenTtl, Clock clock) {
        if (tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("Token TTL must be positive");
        }
        this.tokenTtl = tokenTtl;
        this.clock = clock;
    }

    /**
     * Issues a new token for the supplied principal.
     *
     * @param principal authenticated user
     * @return token descriptor containing the value and expiration timestamp
     */
    public AuthToken issueToken(UserPrincipal principal) {
        Instant expiresAt = clock.instant().plus(tokenTtl);
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(principal, expiresAt));
        return new AuthToken(token, expiresAt, principal);
    }

    /**
     * Resolves the principal bound to the token when it is still valid.
     *
     * @param token bearer token value
     * @return authenticated principal if the token has not expired or been revoked
     */
    public Optional<UserPrincipal> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt().isBefore(clock.instant())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.principal());
    }

    /**
     * Revokes the token, preventing further use.
     *
     * @param token bearer token value
     */
    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private record Session(UserPrincipal principal, Instant expiresAt) {
    }
}
