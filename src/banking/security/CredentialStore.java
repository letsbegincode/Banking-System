package banking.security;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory credential repository backed by a concurrent map.
 */
public final class CredentialStore {
    private final Map<String, CredentialRecord> credentials = new ConcurrentHashMap<>();

    /**
     * Registers a user by hashing the supplied password and associating it with the requested role.
     * Existing registrations for the same username are replaced.
     *
     * @param username username identifier
     * @param password raw password
     * @param role     resolved role for the principal
     */
    public void register(String username, String password, Role role) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(role, "role");
        credentials.put(username, new CredentialRecord(CredentialHasher.hashPassword(password), role));
    }

    /**
     * Registers a user with a pre-computed hash. Useful for loading secrets from configuration.
     *
     * @param username username identifier
     * @param hashedPassword stored salt and hash
     * @param role role assigned to the principal
     */
    public void registerHashed(String username, String hashedPassword, Role role) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(hashedPassword, "hashedPassword");
        Objects.requireNonNull(role, "role");
        credentials.put(username, new CredentialRecord(hashedPassword, role));
    }

    /**
     * Retrieves the credential record for the supplied username.
     *
     * @param username user identifier
     * @return optional credential record
     */
    public Optional<CredentialRecord> find(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(credentials.get(username));
    }
}
