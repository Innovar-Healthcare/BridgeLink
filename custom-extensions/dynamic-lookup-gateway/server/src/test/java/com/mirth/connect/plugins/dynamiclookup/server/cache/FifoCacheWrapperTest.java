/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1056: JUnit coverage improvement — FifoCacheWrapper and FifoCache.
 */

package com.mirth.connect.plugins.dynamiclookup.server.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Unit tests for {@link FifoCacheWrapper} (which internally uses {@link FifoCache}).
 *
 * Tests exercise the public SimpleCache<K,V> contract: put/get, eviction,
 * remove, clear, size, and constructor guard.
 */
public class FifoCacheWrapperTest {

    @Test
    public void putAndGet_returnsStoredValue() {
        FifoCacheWrapper<String, String> cache = new FifoCacheWrapper<>(10);
        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
    }

    @Test
    public void get_missingKey_returnsNull() {
        FifoCacheWrapper<String, String> cache = new FifoCacheWrapper<>(10);
        assertNull(cache.get("missing"));
    }

    @Test
    public void put_overwritesExistingKey() {
        FifoCacheWrapper<String, String> cache = new FifoCacheWrapper<>(10);
        cache.put("k", "v1");
        cache.put("k", "v2");
        assertEquals("v2", cache.get("k"));
        assertEquals(1, cache.size());
    }

    @Test
    public void evictsOldestWhenCapacityExceeded() {
        FifoCacheWrapper<String, String> cache = new FifoCacheWrapper<>(2);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.put("c", "C"); // evicts "a" (FIFO — insertion order)
        assertNull(cache.get("a"));
        assertEquals("B", cache.get("b"));
        assertEquals("C", cache.get("c"));
        assertEquals(2, cache.size());
    }

    @Test
    public void evictsMultipleOldestEntries() {
        FifoCacheWrapper<String, String> cache = new FifoCacheWrapper<>(2);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.put("c", "C"); // evicts a
        cache.put("d", "D"); // evicts b
        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertEquals("C", cache.get("c"));
        assertEquals("D", cache.get("d"));
        assertEquals(2, cache.size());
    }

    @Test
    public void remove_deletesExistingKey() {
        FifoCacheWrapper<String, String> cache = new FifoCacheWrapper<>(10);
        cache.put("x", "X");
        cache.remove("x");
        assertNull(cache.get("x"));
        assertEquals(0, cache.size());
    }

    @Test
    public void remove_nonExistentKey_isNoOp() {
        FifoCacheWrapper<String, String> cache = new FifoCacheWrapper<>(10);
        cache.remove("nonexistent"); // must not throw
    }

    @Test
    public void clear_removesAllEntries() {
        FifoCacheWrapper<String, String> cache = new FifoCacheWrapper<>(10);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.put("c", "C");
        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("a"));
    }

    @Test
    public void size_reflectsCurrentEntryCount() {
        FifoCacheWrapper<String, String> cache = new FifoCacheWrapper<>(5);
        assertEquals(0, cache.size());
        cache.put("a", "A");
        assertEquals(1, cache.size());
        cache.put("b", "B");
        assertEquals(2, cache.size());
        cache.remove("a");
        assertEquals(1, cache.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_zeroMaxSize_throwsIllegalArgument() {
        new FifoCacheWrapper<>(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_negativeMaxSize_throwsIllegalArgument() {
        new FifoCacheWrapper<>(-1);
    }

    @Test
    public void constructor_maxSizeOne_allowsSingleEntry() {
        FifoCacheWrapper<String, String> cache = new FifoCacheWrapper<>(1);
        cache.put("a", "A");
        assertEquals("A", cache.get("a"));
        assertEquals(1, cache.size());
    }

    @Test
    public void maxSizeOne_evictsOnSecondPut() {
        FifoCacheWrapper<String, String> cache = new FifoCacheWrapper<>(1);
        cache.put("a", "A");
        cache.put("b", "B"); // evicts "a"
        assertNull(cache.get("a"));
        assertEquals("B", cache.get("b"));
    }
}
