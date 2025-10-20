package banking.cache;

import java.util.Optional;

/**
 * Cache provider that performs no caching. Useful for disabling caching in
 * environments where it is not desired or for deterministic tests.
 */
public final class NoOpCacheProvider<K, V> implements CacheProvider<K, V> {

    @Override
    public Optional<V> get(K key) {
        return Optional.empty();
    }

    @Override
    public void put(K key, V value) {
        // Intentionally no-op
    }

    @Override
    public void invalidate(K key) {
        // Intentionally no-op
    }

    @Override
    public void clear() {
        // Intentionally no-op
    }
}
