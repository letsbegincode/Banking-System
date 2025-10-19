package banking.observer;

import banking.telemetry.TelemetryCollector;

import java.util.Locale;

/**
 * Observer that translates legacy string messages into structured telemetry events.
 */
public final class StructuredTelemetryObserver implements AccountObserver {
    private final TelemetryCollector collector;

    public StructuredTelemetryObserver() {
        this(TelemetryCollector.getInstance());
    }

    public StructuredTelemetryObserver(TelemetryCollector collector) {
        this.collector = collector;
    }

    @Override
    public void update(String message) {
        if (message == null) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        String status = normalized.startsWith("success") || normalized.startsWith("new")
                ? "success"
                : normalized.startsWith("failed")
                        ? "failure"
                        : "info";
        collector.recordOperation(status, message);
    }
}
