package banking.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Minimal cache abstraction used by the banking domain to decouple the
 * application from the underlying caching technology. Implementations may
 * choose to provide local in-memory caching, distributed caching (e.g. Redis),
 * or no-op behaviour for testing.
 *
 * @param <K> cache key type
 * @param <V> cache value type
 */
public interface CacheProvider<K, V> {

    /**
     * Retrieves a cached value for the supplied key if the entry exists and has
     * not expired.
     *
     * @param key the lookup key
     * @return an optional containing the cached value when present
     */
    Optional<V> get(K key);

    /**
     * Stores a value in the cache using the provider's default time-to-live
     * configuration.
     *
     * @param key   the cache key
     * @param value the value to cache
     */
    void put(K key, V value);

    /**
     * Stores a value in the cache with a custom time-to-live.
     *
     * @param key   the cache key
     * @param value the value to cache
     * @param ttl   the desired time-to-live; {@code null} or non-positive values
     *              indicate the entry should not expire
     */
    default void put(K key, V value, Duration ttl) {
        put(key, value);
    }

    /**
     * Removes any cached value associated with the supplied key.
     *
     * @param key the cache key to invalidate
     */
    void invalidate(K key);

    /**
     * Clears all cached entries maintained by this provider.
     */
    void clear();
}
