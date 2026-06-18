/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1056: JUnit coverage improvement — NoCacheWrapper contract verification.
 */

package com.mirth.connect.plugins.dynamiclookup.server.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Unit tests for {@link NoCacheWrapper}.
 *
 * NoCacheWrapper is a null-object implementation of SimpleCache — every
 * operation is a no-op. Tests verify the contract: get always returns null,
 * size is always 0, put/remove/clear don't throw.
 */
public class NoCacheWrapperTest {

    @Test
    public void get_alwaysReturnsNull() {
        NoCacheWrapper<String, String> cache = new NoCacheWrapper<>();
        cache.put("key", "value");
        assertNull(cache.get("key"));
    }

    @Test
    public void size_alwaysReturnsZero() {
        NoCacheWrapper<String, String> cache = new NoCacheWrapper<>();
        cache.put("key1", "v1");
        cache.put("key2", "v2");
        assertEquals(0, cache.size());
    }

    @Test
    public void remove_isNoOp() {
        NoCacheWrapper<String, String> cache = new NoCacheWrapper<>();
        cache.remove("absent"); // must not throw
    }

    @Test
    public void clear_isNoOp() {
        NoCacheWrapper<String, String> cache = new NoCacheWrapper<>();
        cache.clear(); // must not throw
    }

    @Test
    public void putMany_sizeRemainsZero() {
        NoCacheWrapper<Integer, String> cache = new NoCacheWrapper<>();
        for (int i = 0; i < 100; i++) {
            cache.put(i, "value" + i);
        }
        assertEquals(0, cache.size());
        assertNull(cache.get(0));
    }
}
