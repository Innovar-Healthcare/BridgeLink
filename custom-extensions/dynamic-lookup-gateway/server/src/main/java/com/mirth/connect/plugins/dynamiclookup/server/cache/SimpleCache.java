package com.mirth.connect.plugins.dynamiclookup.server.cache;

public interface SimpleCache<K, V> {
    V get(K key);

    void put(K key, V value);

    void remove(K key);

    void clear();

    int size();
}

