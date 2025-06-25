package com.mirth.connect.plugins.dynamiclookup.server.cache;

import com.google.common.cache.Cache;

public class GuavaCacheWrapper<K, V> implements SimpleCache<K, V> {
    private final Cache<K, V> cache;

    public GuavaCacheWrapper(Cache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public V get(K key) {
        return cache.getIfPresent(key);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void remove(K key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public int size() {
        return cache.asMap().size();
    }

    public Cache<K, V> getGuavaCache() {
        return this.cache;
    }
}

