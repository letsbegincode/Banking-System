package banking.api.middleware;

import com.sun.net.httpserver.HttpExchange;

/**
 * Simple middleware contract that allows executing logic before and after the
 * request-specific handler runs.
 */
public interface HttpRequestFilter {
    void before(HttpExchange exchange, RequestContext context) throws Exception;

    default void after(HttpExchange exchange, RequestContext context) throws Exception {
        // default no-op
    }
}
