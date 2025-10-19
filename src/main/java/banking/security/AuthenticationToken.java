package banking.security;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an issued authentication token.
 */
public final class AuthenticationToken {
    private final String token;
    private final String principal;
    private final Set<Role> roles;
    private final Instant expiresAt;

    public AuthenticationToken(String token, String principal, Set<Role> roles, Instant expiresAt) {
        this.token = Objects.requireNonNull(token, "token");
        this.principal = Objects.requireNonNull(principal, "principal");
        this.roles = Collections.unmodifiableSet(Set.copyOf(roles));
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public String token() {
        return token;
    }

    public String principal() {
        return principal;
    }

    public Set<Role> roles() {
        return roles;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant clock) {
        return !expiresAt.isAfter(clock);
    }
}
