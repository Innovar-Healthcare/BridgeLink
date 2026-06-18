/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1056: JUnit coverage improvement — LookupCacheManager.
 */

package com.mirth.connect.plugins.dynamiclookup.server.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.Test;

import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupDao;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

/**
 * Unit tests for {@link LookupCacheManager}.
 *
 * Tests verify cache policy selection (FIFO vs LRU vs None),
 * getValue TTL logic, putValue, removeValue, clearGroupCache, clearAllCaches,
 * and getCacheStats.
 */
public class LookupCacheManagerTest {

    // ------------------------------------------------------------------
    // buildCacheForGroup — policy selection
    // ------------------------------------------------------------------

    @Test
    public void buildCacheForGroup_sizeZero_returnsNoCacheWrapper() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(0, "LRU");
        SimpleCache<String, CachedValue> cache = manager.buildCacheForGroup(group);
        assertTrue(cache instanceof NoCacheWrapper);
    }

    @Test
    public void buildCacheForGroup_negativeSize_returnsNoCacheWrapper() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(-1, "LRU");
        SimpleCache<String, CachedValue> cache = manager.buildCacheForGroup(group);
        assertTrue(cache instanceof NoCacheWrapper);
    }

    @Test
    public void buildCacheForGroup_fifoPolicy_returnsFifoCacheWrapper() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "FIFO");
        SimpleCache<String, CachedValue> cache = manager.buildCacheForGroup(group);
        assertTrue(cache instanceof FifoCacheWrapper);
    }

    @Test
    public void buildCacheForGroup_fifoPolicy_caseInsensitive() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(50, "fifo");
        SimpleCache<String, CachedValue> cache = manager.buildCacheForGroup(group);
        assertTrue(cache instanceof FifoCacheWrapper);
    }

    @Test
    public void buildCacheForGroup_lruPolicy_returnsGuavaCacheWrapper() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "LRU");
        SimpleCache<String, CachedValue> cache = manager.buildCacheForGroup(group);
        assertTrue(cache instanceof GuavaCacheWrapper);
    }

    @Test
    public void buildCacheForGroup_unknownPolicy_defaultsToGuavaLru() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "UNKNOWN");
        SimpleCache<String, CachedValue> cache = manager.buildCacheForGroup(group);
        assertTrue(cache instanceof GuavaCacheWrapper);
    }

    // ------------------------------------------------------------------
    // createOrRebuildGroupCache / getValue / putValue
    // ------------------------------------------------------------------

    @Test
    public void getValue_uninitializedGroup_returnsNull() {
        LookupCacheManager manager = new LookupCacheManager(null);
        assertNull(manager.getValue(999, "key", 60));
    }

    @Test
    public void putAndGetValue_withinTtl_returnsValue() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "LRU");
        group.setId(1);
        manager.createOrRebuildGroupCache(group);

        Date now = new Date();
        manager.putValue(1, "key", "result", now);

        // ttlSeconds=0 means no TTL check
        String val = manager.getValue(1, "key", 0);
        assertEquals("result", val);
    }

    @Test
    public void getValue_noEntryInCache_returnsNull() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "LRU");
        group.setId(2);
        manager.createOrRebuildGroupCache(group);

        assertNull(manager.getValue(2, "nonexistent", 0));
    }

    @Test
    public void putValue_uninitializedGroup_isNoOp() {
        LookupCacheManager manager = new LookupCacheManager(null);
        // Should not throw even though group cache was never initialized
        manager.putValue(999, "key", "value", new Date());
        assertEquals(0, manager.getCacheSize(999));
    }

    @Test
    public void getValue_expiredTtl_returnsNull() throws Exception {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "LRU");
        group.setId(3);
        manager.createOrRebuildGroupCache(group);

        // Use a timestamp 10 minutes in the past
        Date tenMinutesAgo = new Date(System.currentTimeMillis() - 10 * 60 * 1000L);
        manager.putValue(3, "key", "stale", tenMinutesAgo);

        // TTL of 5 seconds — the value is 10 minutes old, so it's expired
        assertNull(manager.getValue(3, "key", 5));
    }

    // ------------------------------------------------------------------
    // removeValue
    // ------------------------------------------------------------------

    @Test
    public void removeValue_removesExistingEntry() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "LRU");
        group.setId(4);
        manager.createOrRebuildGroupCache(group);

        manager.putValue(4, "k", "v", new Date());
        manager.removeValue(4, "k");
        assertNull(manager.getValue(4, "k", 0));
    }

    @Test
    public void removeValue_uninitializedGroup_isNoOp() {
        LookupCacheManager manager = new LookupCacheManager(null);
        manager.removeValue(999, "key"); // must not throw
    }

    // ------------------------------------------------------------------
    // clearGroupCache / clearAllCaches
    // ------------------------------------------------------------------

    @Test
    public void clearGroupCache_removesAllEntriesForGroup() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "LRU");
        group.setId(5);
        manager.createOrRebuildGroupCache(group);

        manager.putValue(5, "a", "A", new Date());
        manager.putValue(5, "b", "B", new Date());
        manager.clearGroupCache(5);

        assertNull(manager.getValue(5, "a", 0));
        assertNull(manager.getValue(5, "b", 0));
    }

    @Test
    public void clearGroupCache_uninitializedGroup_isNoOp() {
        LookupCacheManager manager = new LookupCacheManager(null);
        manager.clearGroupCache(999); // must not throw
    }

    @Test
    public void clearAllCaches_clearsAllGroups() {
        LookupCacheManager manager = new LookupCacheManager(null);

        LookupGroup g1 = makeGroup(100, "LRU");
        g1.setId(6);
        LookupGroup g2 = makeGroup(100, "FIFO");
        g2.setId(7);
        manager.createOrRebuildGroupCache(g1);
        manager.createOrRebuildGroupCache(g2);

        manager.putValue(6, "x", "X", new Date());
        manager.putValue(7, "y", "Y", new Date());
        manager.clearAllCaches();

        assertNull(manager.getValue(6, "x", 0));
        assertNull(manager.getValue(7, "y", 0));
    }

    // ------------------------------------------------------------------
    // getCacheSize / getCacheMaxSize
    // ------------------------------------------------------------------

    @Test
    public void getCacheSize_uninitializedGroup_returnsZero() {
        LookupCacheManager manager = new LookupCacheManager(null);
        assertEquals(0, manager.getCacheSize(999));
    }

    @Test
    public void getCacheSize_reflectsCurrentEntries() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "LRU");
        group.setId(8);
        manager.createOrRebuildGroupCache(group);

        assertEquals(0, manager.getCacheSize(8));
        manager.putValue(8, "k1", "v1", new Date());
        assertEquals(1, manager.getCacheSize(8));
    }

    @Test
    public void getCacheMaxSize_nullDao_returnsMinusOne() {
        LookupCacheManager manager = new LookupCacheManager(null);
        assertEquals(-1, manager.getCacheMaxSize(1));
    }

    @Test
    public void getCacheMaxSize_withDao_returnsGroupCacheSize() {
        LookupGroupDao dao = mock(LookupGroupDao.class);
        LookupGroup group = makeGroup(500, "LRU");
        group.setId(9);
        when(dao.getGroupById(9)).thenReturn(group);

        LookupCacheManager manager = new LookupCacheManager(dao);
        assertEquals(500, manager.getCacheMaxSize(9));
    }

    @Test
    public void getCacheMaxSize_groupNotFoundInDao_returnsMinusOne() {
        LookupGroupDao dao = mock(LookupGroupDao.class);
        when(dao.getGroupById(999)).thenReturn(null);

        LookupCacheManager manager = new LookupCacheManager(dao);
        assertEquals(-1, manager.getCacheMaxSize(999));
    }

    // ------------------------------------------------------------------
    // getCacheStats — LRU returns stats, FIFO returns null
    // ------------------------------------------------------------------

    @Test
    public void getCacheStats_lruCache_returnsNonNull() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "LRU");
        group.setId(10);
        manager.createOrRebuildGroupCache(group);

        assertNotNull(manager.getCacheStats(10));
    }

    @Test
    public void getCacheStats_fifoCache_returnsNull() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "FIFO");
        group.setId(11);
        manager.createOrRebuildGroupCache(group);

        assertNull(manager.getCacheStats(11));
    }

    @Test
    public void getCacheStats_uninitializedGroup_returnsNull() {
        LookupCacheManager manager = new LookupCacheManager(null);
        assertNull(manager.getCacheStats(999));
    }

    // ------------------------------------------------------------------
    // createOrRebuildGroupCache — null guard
    // ------------------------------------------------------------------

    @Test
    public void createOrRebuildGroupCache_nullGroup_isNoOp() {
        LookupCacheManager manager = new LookupCacheManager(null);
        manager.createOrRebuildGroupCache(null); // must not throw
    }

    @Test
    public void removeGroupCache_removesCache() {
        LookupCacheManager manager = new LookupCacheManager(null);
        LookupGroup group = makeGroup(100, "LRU");
        group.setId(12);
        manager.createOrRebuildGroupCache(group);
        manager.putValue(12, "k", "v", new Date());

        manager.removeGroupCache(12);

        // After removal, getValue returns null (cache gone)
        assertNull(manager.getValue(12, "k", 0));
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private LookupGroup makeGroup(int cacheSize, String cachePolicy) {
        LookupGroup g = new LookupGroup();
        g.setCacheSize(cacheSize);
        g.setCachePolicy(cachePolicy);
        g.setName("test-group");
        return g;
    }
}
