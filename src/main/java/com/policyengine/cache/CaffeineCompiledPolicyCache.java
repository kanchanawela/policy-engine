package com.policyengine.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Caffeine-backed compiled policy cache.
 * Thread-safe; Caffeine handles internal locking.
 */
public class CaffeineCompiledPolicyCache implements PolicyCache {

    private final Cache<String, CompiledPolicy> cache;

    public CaffeineCompiledPolicyCache(int maxSize, Duration expiry) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expiry)
                .recordStats()
                .build();
    }

    @Override
    public CompiledPolicy get(String policyId, Supplier<CompiledPolicy> loader) {
        return cache.get(policyId, k -> loader.get());
    }

    @Override
    public void put(String policyId, CompiledPolicy compiled) {
        cache.put(policyId, compiled);
    }

    @Override
    public void invalidate(String policyId) {
        cache.invalidate(policyId);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }
}
