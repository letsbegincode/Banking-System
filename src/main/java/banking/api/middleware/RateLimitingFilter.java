package banking.api.middleware;

import com.sun.net.httpserver.HttpExchange;

import java.net.InetSocketAddress;

/**
 * Middleware filter that enforces rate limits per remote client address.
 */
public final class RateLimitingFilter implements HttpRequestFilter {
    private final RateLimiter rateLimiter;

    public RateLimitingFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void before(HttpExchange exchange, RequestContext context) {
        InetSocketAddress remote = exchange.getRemoteAddress();
        String key = remote == null ? "global" : remote.getAddress().getHostAddress();
        if (!rateLimiter.tryAcquire(key)) {
            throw new TooManyRequestsException("Rate limit exceeded for client " + key);
        }
    }
}
