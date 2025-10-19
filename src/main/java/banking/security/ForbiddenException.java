package banking.security;

/**
 * Thrown when an authenticated user is not allowed to perform the operation.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
