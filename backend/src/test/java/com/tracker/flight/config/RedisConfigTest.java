package com.tracker.flight.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("#58: настройки Redis-кэша рейсов")
class RedisConfigTest {

    private RedisCacheConfiguration flightsCacheConfig() {
        // Подключение к Redis не нужно: кэш-менеджер не обращается к серверу, пока кэш не используется.
        RedisCacheManager manager = new RedisConfig().cacheManager(mock(RedisConnectionFactory.class));
        manager.afterPropertiesSet();
        RedisCache cache = (RedisCache) manager.getCache(RedisConfig.FLIGHTS_CACHE);
        assertThat(cache).isNotNull();
        return cache.getCacheConfiguration();
    }

    @Test
    @DisplayName("TTL записи о рейсе — 3 минуты (как в README)")
    void flightsCache_TtlIsThreeMinutes() {
        Duration ttl = flightsCacheConfig().getTtlFunction().getTimeToLive("SU100:2026-10-25", new Object());

        assertThat(ttl).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("null не кэшируется")
    void flightsCache_DoesNotCacheNulls() {
        assertThat(flightsCacheConfig().getAllowCacheNullValues()).isFalse();
    }
}
