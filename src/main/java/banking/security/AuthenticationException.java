package banking.security;

/**
 * Signals invalid operator credentials.
 */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }
}
