package com.mirth.connect.plugins.dynamiclookup.server.cache;

import java.util.Collections;
import java.util.Map;

public class FifoCacheWrapper<K, V> implements SimpleCache<K, V> {
    private final Map<K, V> map;

    public FifoCacheWrapper(int maxSize) {
        this.map = Collections.synchronizedMap(new FifoCache<>(maxSize));
    }

    @Override
    public V get(K key) {
        return map.get(key);
    }

    @Override
    public void put(K key, V value) {
        map.put(key, value);
    }

    @Override
    public void remove(K key) {
        map.remove(key);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public int size() {
        return map.size();
    }
}

