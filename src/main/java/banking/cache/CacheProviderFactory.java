package banking.cache;

import banking.account.Account;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Factory that constructs cache providers for the application based on runtime
 * configuration. The provider and TTL can be supplied via either Java system
 * properties or environment variables, making it simple to switch providers in
 * different deployments.
 */
public final class CacheProviderFactory {

    private static final String PROVIDER_PROPERTY = "banking.cache.provider";
    private static final String PROVIDER_ENV = "CACHE_PROVIDER";
    private static final String TTL_PROPERTY_TEMPLATE = "banking.cache.%s.ttlSeconds";
    private static final String TTL_ENV_TEMPLATE = "CACHE_%s_TTL_SECONDS";
    private static final String GLOBAL_TTL_PROPERTY = "banking.cache.ttlSeconds";
    private static final String GLOBAL_TTL_ENV = "CACHE_TTL_SECONDS";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private CacheProviderFactory() {
    }

    public static CacheProvider<Integer, Account> accountCache() {
        return create("account");
    }

    public static CacheProvider<Integer, Double> balanceCache() {
        return create("balance");
    }

    public static <K, V> CacheProvider<K, V> create(String cacheName) {
        String provider = resolveProvider();
        if ("none".equals(provider)) {
            return new NoOpCacheProvider<>();
        }
        Duration ttl = resolveTtl(cacheName);
        return new InMemoryCacheProvider<>(ttl);
    }

    private static String resolveProvider() {
        return Optional.ofNullable(System.getProperty(PROVIDER_PROPERTY))
                .or(() -> Optional.ofNullable(System.getenv(PROVIDER_ENV)))
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .filter(value -> !value.isEmpty())
                .orElse("memory");
    }

    private static Duration resolveTtl(String cacheName) {
        String propertyKey = String.format(Locale.ROOT, TTL_PROPERTY_TEMPLATE, cacheName);
        String envKey = String.format(Locale.ROOT, TTL_ENV_TEMPLATE, cacheName.toUpperCase(Locale.ROOT));

        String ttlValue = Optional.ofNullable(System.getProperty(propertyKey))
                .or(() -> Optional.ofNullable(System.getenv(envKey)))
                .or(() -> Optional.ofNullable(System.getProperty(GLOBAL_TTL_PROPERTY)))
                .or(() -> Optional.ofNullable(System.getenv(GLOBAL_TTL_ENV)))
                .orElse(null);

        if (ttlValue == null || ttlValue.isBlank()) {
            return DEFAULT_TTL;
        }

        try {
            long seconds = Long.parseLong(ttlValue.trim());
            if (seconds <= 0) {
                return Duration.ZERO;
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            System.err.printf("Invalid TTL value '%s' for cache '%s'; defaulting to %s seconds.%n", ttlValue, cacheName,
                    DEFAULT_TTL.getSeconds());
            return DEFAULT_TTL;
        }
    }
}
