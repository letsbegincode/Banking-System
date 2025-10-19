package banking.api.middleware;

/**
 * Represents a serialized error payload to be returned to callers.
 */
public record ErrorResponse(int statusCode, String message) {
}
