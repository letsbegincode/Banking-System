package banking.cache;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import banking.account.Account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CacheProviderFactoryTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("banking.cache.provider");
        System.clearProperty("banking.cache.ttlSeconds");
        System.clearProperty("banking.cache.account.ttlSeconds");
    }

    @Test
    @DisplayName("defaults to in-memory cache when no configuration is provided")
    void defaultsToInMemory() {
        CacheProvider<Integer, Account> cache = CacheProviderFactory.accountCache();

        assertInstanceOf(InMemoryCacheProvider.class, cache);
    }

    @Test
    @DisplayName("returns no-op cache when provider is set to none")
    void returnsNoOpWhenDisabled() {
        System.setProperty("banking.cache.provider", "none");

        CacheProvider<Integer, Account> cache = CacheProviderFactory.accountCache();

        assertInstanceOf(NoOpCacheProvider.class, cache);
    }

    @Test
    @DisplayName("honours custom TTL configuration")
    void honoursCustomTtl() {
        System.setProperty("banking.cache.account.ttlSeconds", "1");

        CacheProvider<Integer, Account> cache = CacheProviderFactory.accountCache();

        assertInstanceOf(InMemoryCacheProvider.class, cache);
        assertTrue(cache.get(1).isEmpty(), "cache should start empty");
    }
}
