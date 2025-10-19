package banking.api.middleware;

import com.sun.net.httpserver.HttpExchange;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Captures contextual data for a single HTTP exchange so middleware components can
 * collaborate without relying on concrete handler implementations.
 */
public final class RequestContext {
    private static final String ATTRIBUTE_KEY = RequestContext.class.getName();

    private final HttpExchange exchange;
    private final Instant startedAt;
    private final Clock clock;
    private int statusCode;
    private int responseBytes;
    private Throwable error;

    private RequestContext(HttpExchange exchange, Clock clock) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startedAt = clock.instant();
        this.statusCode = -1;
    }

    public static RequestContext attach(HttpExchange exchange) {
        RequestContext context = new RequestContext(exchange, Clock.systemUTC());
        exchange.setAttribute(ATTRIBUTE_KEY, context);
        return context;
    }

    public static Optional<RequestContext> from(HttpExchange exchange) {
        Object value = exchange.getAttribute(ATTRIBUTE_KEY);
        if (value instanceof RequestContext context) {
            return Optional.of(context);
        }
        return Optional.empty();
    }

    public HttpExchange exchange() {
        return exchange;
    }

    public String method() {
        return exchange.getRequestMethod();
    }

    public String path() {
        return exchange.getRequestURI() == null ? "" : exchange.getRequestURI().getPath();
    }

    public void recordResponse(int statusCode, int responseBytes) {
        this.statusCode = statusCode;
        this.responseBytes = responseBytes;
    }

    public int statusCode() {
        return statusCode;
    }

    public int responseBytes() {
        return responseBytes;
    }

    public void recordError(Throwable error) {
        this.error = error;
    }

    public Optional<Throwable> error() {
        return Optional.ofNullable(error);
    }

    public long durationMillis() {
        return Duration.between(startedAt, clock.instant()).toMillis();
    }

    public Instant startedAt() {
        return startedAt;
    }
}
