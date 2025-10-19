package banking.api.middleware;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

/**
 * Validates headers and payload characteristics for mutating requests.
 */
public final class RequestValidationFilter implements HttpRequestFilter {
    private final int maxPayloadBytes;

    public RequestValidationFilter(int maxPayloadBytes) {
        this.maxPayloadBytes = Math.max(0, maxPayloadBytes);
    }

    @Override
    public void before(HttpExchange exchange, RequestContext context) {
        String method = exchange.getRequestMethod();
        if (method == null) {
            throw new IllegalArgumentException("HTTP method missing");
        }

        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            validateContentType(exchange.getRequestHeaders());
            validateContentLength(exchange.getRequestHeaders());
        }
    }

    private void validateContentType(Headers headers) {
        String contentType = headers.getFirst("Content-Type");
        if (contentType == null) {
            throw new IllegalArgumentException("Content-Type header is required");
        }
        String normalized = contentType.toLowerCase();
        if (!normalized.startsWith("application/x-www-form-urlencoded")
                && !normalized.startsWith("application/json")) {
            throw new IllegalArgumentException("Unsupported Content-Type: " + contentType);
        }
    }

    private void validateContentLength(Headers headers) {
        String contentLength = headers.getFirst("Content-Length");
        if (contentLength == null) {
            return; // allow chunked encoding but rely on handler safeguards
        }
        try {
            int length = Integer.parseInt(contentLength.trim());
            if (length < 0) {
                throw new IllegalArgumentException("Content-Length must be positive");
            }
            if (length > maxPayloadBytes) {
                throw new IllegalArgumentException(
                        "Request payload exceeds allowed size of " + maxPayloadBytes + " bytes");
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid Content-Length header");
        }
    }
}
