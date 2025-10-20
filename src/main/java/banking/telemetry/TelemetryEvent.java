package banking.telemetry;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a structured telemetry event that can be consumed by downstream observers.
 */
public final class TelemetryEvent {
    private final String category;
    private final String name;
    private final String status;
    private final Instant timestamp;
    private final Map<String, Object> attributes;

    private TelemetryEvent(Builder builder) {
        this.category = Objects.requireNonNull(builder.category, "category");
        this.name = Objects.requireNonNull(builder.name, "name");
        this.status = Objects.requireNonNull(builder.status, "status");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp");
        this.attributes = Collections.unmodifiableMap(builder.attributes);
    }

    public String category() {
        return category;
    }

    public String name() {
        return name;
    }

    public String status() {
        return status;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "TelemetryEvent{"
                + "category='" + category + '\''
                + ", name='" + name + '\''
                + ", status='" + status + '\''
                + ", timestamp=" + timestamp
                + ", attributes=" + attributes
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String category;
        private String name;
        private String status = "unknown";
        private Instant timestamp = Instant.now();
        private Map<String, Object> attributes = Collections.emptyMap();

        private Builder() {
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes == null ? Collections.emptyMap() : attributes;
            return this;
        }

        public TelemetryEvent build() {
            return new TelemetryEvent(this);
        }
    }
}
