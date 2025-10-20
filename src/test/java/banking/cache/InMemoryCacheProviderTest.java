package banking.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryCacheProviderTest {

    @Test
    @DisplayName("stores and retrieves values using the default TTL")
    void storesAndRetrievesValues() {
        InMemoryCacheProvider<String, String> cache = new InMemoryCacheProvider<>(Duration.ofMinutes(1));

        cache.put("key", "value");

        assertEquals("value", cache.get("key").orElseThrow());
    }

    @Test
    @DisplayName("evicts expired entries lazily on read")
    void evictsExpiredEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        InMemoryCacheProvider<String, String> cache = new InMemoryCacheProvider<>(Duration.ofSeconds(1), clock);

        cache.put("key", "value");
        assertTrue(cache.get("key").isPresent(), "entry should exist before expiry");

        clock.advance(Duration.ofSeconds(5));

        assertFalse(cache.get("key").isPresent(), "entry should be evicted after TTL has elapsed");
    }

    @Test
    @DisplayName("invalidate removes cached entries")
    void invalidateRemovesEntry() {
        InMemoryCacheProvider<String, String> cache = new InMemoryCacheProvider<>(Duration.ofMinutes(1));

        cache.put("key", "value");
        cache.invalidate("key");

        assertTrue(cache.get("key").isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
