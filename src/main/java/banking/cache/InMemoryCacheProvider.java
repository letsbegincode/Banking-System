package banking.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache implementation that keeps entries in memory. The cache
 * supports per-entry expirations and a configurable default time-to-live.
 * Expired entries are evicted lazily upon access.
 */
public final class InMemoryCacheProvider<K, V> implements CacheProvider<K, V> {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final Map<K, CacheEntry<V>> entries = new ConcurrentHashMap<>();
    private final Duration defaultTtl;
    private final Clock clock;

    public InMemoryCacheProvider() {
        this(DEFAULT_TTL, Clock.systemUTC());
    }

    public InMemoryCacheProvider(Duration defaultTtl) {
        this(defaultTtl, Clock.systemUTC());
    }

    InMemoryCacheProvider(Duration defaultTtl, Clock clock) {
        this.defaultTtl = normalize(defaultTtl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "key");
        CacheEntry<V> entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(clock.instant())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void put(K key, V value) {
        put(key, value, defaultTtl);
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Duration normalized = normalize(ttl);
        Instant expiresAt = normalized.isZero() ? null : clock.instant().plus(normalized);
        entries.put(key, new CacheEntry<>(value, expiresAt));
    }

    @Override
    public void invalidate(K key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    @Override
    public void clear() {
        entries.clear();
    }

    private static Duration normalize(Duration ttl) {
        if (ttl == null || ttl.isNegative()) {
            return Duration.ZERO;
        }
        return ttl;
    }

    private record CacheEntry<V>(V value, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return expiresAt != null && expiresAt.isBefore(now);
        }
    }
}
