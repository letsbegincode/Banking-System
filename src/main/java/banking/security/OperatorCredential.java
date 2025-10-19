package banking.security;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable value representing stored operator credentials.
 */
public final class OperatorCredential {
    private final String username;
    private final String passwordHash;
    private final Set<Role> roles;

    public OperatorCredential(String username, String passwordHash, Set<Role> roles) {
        this.username = Objects.requireNonNull(username, "username");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.roles = Collections.unmodifiableSet(Set.copyOf(roles));
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Set<Role> roles() {
        return roles;
    }
}
