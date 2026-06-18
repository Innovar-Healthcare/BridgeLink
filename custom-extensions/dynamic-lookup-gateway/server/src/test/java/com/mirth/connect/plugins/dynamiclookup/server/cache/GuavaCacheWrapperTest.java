/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1056: JUnit coverage improvement — GuavaCacheWrapper.
 */

package com.mirth.connect.plugins.dynamiclookup.server.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Unit tests for {@link GuavaCacheWrapper}.
 *
 * Uses real Guava Cache instances to verify delegation contract.
 */
public class GuavaCacheWrapperTest {

    private static <K, V> GuavaCacheWrapper<K, V> newLruCache(int maxSize) {
        Cache<K, V> guava = CacheBuilder.newBuilder()
            .maximumSize(maxSize)
            .recordStats()
            .build();
        return new GuavaCacheWrapper<>(guava);
    }

    @Test
    public void put_andGet_returnsValue() {
        GuavaCacheWrapper<String, String> cache = newLruCache(10);
        cache.put("key", "value");
        assertEquals("value", cache.get("key"));
    }

    @Test
    public void get_missingKey_returnsNull() {
        GuavaCacheWrapper<String, String> cache = newLruCache(10);
        assertNull(cache.get("absent"));
    }

    @Test
    public void put_overwritesExistingKey() {
        GuavaCacheWrapper<String, String> cache = newLruCache(10);
        cache.put("k", "v1");
        cache.put("k", "v2");
        assertEquals("v2", cache.get("k"));
    }

    @Test
    public void remove_invalidatesKey() {
        GuavaCacheWrapper<String, String> cache = newLruCache(10);
        cache.put("k", "v");
        cache.remove("k");
        assertNull(cache.get("k"));
    }

    @Test
    public void remove_nonExistentKey_isNoOp() {
        GuavaCacheWrapper<String, String> cache = newLruCache(10);
        cache.remove("nope"); // must not throw
        assertEquals(0, cache.size());
    }

    @Test
    public void clear_removesAllEntries() {
        GuavaCacheWrapper<String, String> cache = newLruCache(10);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
    }

    @Test
    public void size_reflectsEntryCount() {
        GuavaCacheWrapper<String, String> cache = newLruCache(10);
        assertEquals(0, cache.size());
        cache.put("a", "A");
        assertEquals(1, cache.size());
        cache.put("b", "B");
        assertEquals(2, cache.size());
    }

    @Test
    public void getGuavaCache_returnsUnderlyingCache() {
        Cache<String, String> guava = CacheBuilder.newBuilder().maximumSize(5).build();
        GuavaCacheWrapper<String, String> wrapper = new GuavaCacheWrapper<>(guava);
        assertNotNull(wrapper.getGuavaCache());
        assertEquals(guava, wrapper.getGuavaCache());
    }

    @Test
    public void ttlExpiry_expiredEntryIsEvicted() throws Exception {
        // Create a cache with 100ms TTL
        Cache<String, String> guava = CacheBuilder.newBuilder()
            .expireAfterWrite(100, TimeUnit.MILLISECONDS)
            .build();
        GuavaCacheWrapper<String, String> cache = new GuavaCacheWrapper<>(guava);
        cache.put("k", "v");
        assertEquals("v", cache.get("k"));

        Thread.sleep(150); // wait for TTL to expire
        // Guava evicts lazily on access
        assertNull(cache.get("k"));
    }

    @Test
    public void multipleKeys_storedAndRetrievedCorrectly() {
        GuavaCacheWrapper<String, Integer> cache = newLruCache(100);
        for (int i = 0; i < 10; i++) {
            cache.put("key" + i, i);
        }
        assertEquals(10, cache.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(Integer.valueOf(i), cache.get("key" + i));
        }
    }
}
