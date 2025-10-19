package banking.api.middleware;

/**
 * Exception thrown when rate limits are exceeded.
 */
public final class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
