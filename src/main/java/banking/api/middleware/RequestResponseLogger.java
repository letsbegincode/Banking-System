package banking.api.middleware;

import com.sun.net.httpserver.HttpExchange;

import banking.telemetry.TelemetryCollector;

/**
 * Emits structured telemetry for each HTTP request after the handler has produced a response.
 */
public final class RequestResponseLogger implements HttpRequestFilter {
    private final TelemetryCollector collector;

    public RequestResponseLogger(TelemetryCollector collector) {
        this.collector = collector;
    }

    @Override
    public void before(HttpExchange exchange, RequestContext context) {
        // no-op before logging; metrics calculated on completion
    }

    @Override
    public void after(HttpExchange exchange, RequestContext context) {
        int statusCode = context.statusCode() < 0 ? 500 : context.statusCode();
        collector.recordHttpInteraction(
                context.method(),
                context.path(),
                statusCode,
                context.durationMillis());
    }
}
