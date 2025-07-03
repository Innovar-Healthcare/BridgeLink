/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.cache;

import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupDao;
import com.mirth.connect.plugins.dynamiclookup.server.exception.GroupNotFoundException;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

import com.google.common.cache.CacheStats;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mirth.connect.plugins.dynamiclookup.shared.util.TtlUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Date;

public class LookupCacheManager {
    private final Map<Integer, SimpleCache<String, CachedValue>> groupCaches = new ConcurrentHashMap<>();
    private final LookupGroupDao groupDao;

    public LookupCacheManager(LookupGroupDao groupDao) {
        this.groupDao = groupDao;
    }

    /**
     * Gets a value from cache if present.
     * If ttlHours > 0, the value must be within the TTL based on updatedAt.
     * If ttlHours == 0, TTL is ignored and any cached value is returned.
     */
    public String getValue(int groupId, String key, long ttlHours) {
        SimpleCache<String, CachedValue> cache = getOrCreateCache(groupId);
        CachedValue cached = cache.get(key);

        if (cached == null) {
            return null;
        }

        // Use shared TTL logic
        if (TtlUtils.isWithinTtl(cached.getUpdatedAt(), ttlHours)) {
            return cached.getValue();
        }

        return null;
    }


    /**
     * Adds or updates a value in cache using updatedAt from the database.
     */
    public void putValue(int groupId, String key, String value, Date updatedAt) {
        SimpleCache<String, CachedValue> cache = getOrCreateCache(groupId);
        cache.put(key, new CachedValue(value, updatedAt));
    }

    /**
     * Removes a specific value from cache
     */
    public void removeValue(int groupId, String key) {
        SimpleCache<String, CachedValue> cache = getOrCreateCache(groupId);
        cache.remove(key);
    }

    /**
     * Clears all values for a specific group
     */
    public void clearGroupCache(int groupId) {
        SimpleCache<String, CachedValue> cache = groupCaches.get(groupId);
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * Clears all caches
     */
    public void clearAllCaches() {
        groupCaches.values().forEach(SimpleCache::clear);
    }

    /**
     * Gets the current number of entries in the group's cache.
     */
    public int getCacheSize(int groupId) {
        SimpleCache<String, CachedValue> cache = groupCaches.get(groupId);
        return (cache != null) ? cache.size() : 0;
    }

    /**
     * Gets the configured maximum size of the group's cache.
     */
    public int getCacheMaxSize(int groupId) {
        LookupGroup group = groupDao.getGroupById(groupId);
        return (group != null) ? group.getCacheSize() : -1;
    }

    /**
     * Gets cache statistics for a group (only available for LRU caches).
     */
    public CacheStats getCacheStats(int groupId) {
        SimpleCache<String, CachedValue> cache = groupCaches.get(groupId);

        if (cache instanceof GuavaCacheWrapper) {
            GuavaCacheWrapper<String, CachedValue> guava = (GuavaCacheWrapper<String, CachedValue>) cache;
            return guava.getGuavaCache().stats();
        }

        // FIFO or unknown cache types do not support stats
        return null;
    }

    /**
     * Creates or retrieves the cache for a group
     */
    private SimpleCache<String, CachedValue> getOrCreateCache(int groupId) {
        return groupCaches.computeIfAbsent(groupId, k -> {
            LookupGroup group = groupDao.getGroupById(groupId);
            if (group == null) {
                throw new GroupNotFoundException("Group not found with ID: " + groupId);
            }
            return createCacheForGroup(group);
        });
    }

    /**
     * Creates a cache with the appropriate eviction policy
     */
    private SimpleCache<String, CachedValue> createCacheForGroup(LookupGroup group) {
        int cacheSize = group.getCacheSize();
        String cachePolicy = group.getCachePolicy();

        if ("FIFO".equalsIgnoreCase(cachePolicy)) {
            return new FifoCacheWrapper<>(cacheSize);
        } else { // Default to LRU
            Cache<String, CachedValue> guavaCache = CacheBuilder.newBuilder()
                    .maximumSize(cacheSize)
//                    .expireAfterWrite(10, TimeUnit.MINUTES) // TTL (optional)
                    .recordStats()
                    .build();
            return new GuavaCacheWrapper<>(guavaCache);
        }
    }
}


