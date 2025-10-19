package banking.security;

/**
 * Immutable credential entry used by the in-memory credential store.
 */
public record CredentialRecord(String hashedPassword, Role role) {
}
