package com.ncba.rdas.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Second-level cache: memoises the results of computed country queries
 * (filter + sort + page) keyed by their parameters. The source dataset is held
 * by {@code ReferenceDataService}; this cache simply avoids recomputing identical
 * queries within a short window. Bounded in size and short-lived so it never
 * serves data older than the underlying snapshot for long.
 */
@Configuration
public class CacheConfig {

    public static final String COUNTRY_QUERY_CACHE = "countryQueries";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(COUNTRY_QUERY_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats());
        return manager;
    }
}
