package banking.security;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory credential repository for operators.
 */
public class CredentialStore {
    private final Map<String, OperatorCredential> credentials = new ConcurrentHashMap<>();

    public void store(OperatorCredential credential) {
        Objects.requireNonNull(credential, "credential");
        credentials.put(credential.username().toLowerCase(), credential);
    }

    public Optional<OperatorCredential> find(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(credentials.get(username.toLowerCase()));
    }
}
