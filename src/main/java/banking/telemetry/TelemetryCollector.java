package banking.telemetry;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central aggregation point for telemetry events emitted by the application.
 */
public final class TelemetryCollector {
    private static final TelemetryCollector INSTANCE = new TelemetryCollector();

    private final ConcurrentLinkedQueue<TelemetryEvent> events;
    private final AtomicInteger successfulOperations;
    private final AtomicInteger failedOperations;
    private final AtomicInteger httpRequests;
    private final AtomicInteger httpFailures;

    private TelemetryCollector() {
        this.events = new ConcurrentLinkedQueue<>();
        this.successfulOperations = new AtomicInteger();
        this.failedOperations = new AtomicInteger();
        this.httpRequests = new AtomicInteger();
        this.httpFailures = new AtomicInteger();
    }

    public static TelemetryCollector getInstance() {
        return INSTANCE;
    }

    public void recordOperation(String status, String message) {
        boolean success = "success".equalsIgnoreCase(status);
        if (success) {
            successfulOperations.incrementAndGet();
        } else {
            failedOperations.incrementAndGet();
        }
        events.add(TelemetryEvent.builder()
                .category("bank-operation")
                .name("operation")
                .status(success ? "success" : "failure")
                .timestamp(Instant.now())
                .attributes(Collections.singletonMap("message", message))
                .build());
    }

    public void recordHttpInteraction(String method, String path, int status, long durationMillis) {
        httpRequests.incrementAndGet();
        if (status >= 400) {
            httpFailures.incrementAndGet();
        }
        events.add(TelemetryEvent.builder()
                .category("http-gateway")
                .name(method + " " + path)
                .status(status < 400 ? "success" : "failure")
                .timestamp(Instant.now())
                .attributes(Map.of(
                        "status", status,
                        "durationMillis", durationMillis,
                        "path", path,
                        "method", method))
                .build());
    }

    public TelemetrySnapshot snapshot() {
        return new TelemetrySnapshot(
                successfulOperations.get(),
                failedOperations.get(),
                httpRequests.get(),
                httpFailures.get(),
                List.copyOf(events));
    }

    public void reset() {
        events.clear();
        successfulOperations.set(0);
        failedOperations.set(0);
        httpRequests.set(0);
        httpFailures.set(0);
    }

    public record TelemetrySnapshot(int successfulOperations,
                                    int failedOperations,
                                    int httpRequests,
                                    int httpFailures,
                                    List<TelemetryEvent> events) {
        public TelemetryEvent latestEvent() {
            if (events.isEmpty()) {
                return null;
            }
            return events.get(events.size() - 1);
        }
    }
}
