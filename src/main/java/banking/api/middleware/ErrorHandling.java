package banking.api.middleware;

/**
 * Maps exceptions produced by the gateway into structured error responses.
 */
public final class ErrorHandling {
    private ErrorHandling() {
    }

    public static ErrorResponse resolve(Throwable throwable) {
        if (throwable instanceof TooManyRequestsException tooManyRequests) {
            return new ErrorResponse(429, tooManyRequests.getMessage());
        }
        if (throwable instanceof IllegalArgumentException illegalArgument) {
            return new ErrorResponse(400, illegalArgument.getMessage());
        }
        return new ErrorResponse(500, "Internal server error");
    }
}
