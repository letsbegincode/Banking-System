package banking.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues bearer tokens and enforces expiration.
 */
public class TokenService {
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random;
    private final Clock clock;
    private final Map<String, AuthenticationToken> tokens = new ConcurrentHashMap<>();

    public TokenService() {
        this(new SecureRandom(), Clock.systemUTC());
    }

    TokenService(SecureRandom random, Clock clock) {
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AuthenticationToken issueToken(String principal, Collection<Role> roles, Duration ttl) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(ttl, "ttl");
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        Instant expiresAt = clock.instant().plus(ttl);
        String tokenValue = generateToken();
        AuthenticationToken token = new AuthenticationToken(tokenValue, principal, Set.copyOf(roles), expiresAt);
        tokens.put(tokenValue, token);
        return token;
    }

    public Optional<AuthenticationToken> validate(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        AuthenticationToken token = tokens.get(tokenValue);
        if (token == null) {
            return Optional.empty();
        }
        if (token.isExpired(clock.instant())) {
            tokens.remove(tokenValue);
            return Optional.empty();
        }
        return Optional.of(token);
    }

    public void revoke(String tokenValue) {
        if (tokenValue != null) {
            tokens.remove(tokenValue);
        }
    }

    public Collection<AuthenticationToken> activeTokens() {
        purgeExpired();
        return List.copyOf(tokens.values());
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        tokens.values().removeIf(token -> token.isExpired(now));
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
