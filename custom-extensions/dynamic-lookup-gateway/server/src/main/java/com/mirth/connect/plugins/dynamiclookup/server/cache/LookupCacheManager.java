package com.mirth.connect.plugins.dynamiclookup.server.cache;

import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupDao;
import com.mirth.connect.plugins.dynamiclookup.server.exception.GroupNotFoundException;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

import com.google.common.cache.CacheStats;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LookupCacheManager {
    private final Map<Integer, SimpleCache<String, String>> groupCaches = new ConcurrentHashMap<>();
    private final LookupGroupDao groupDao;

    public LookupCacheManager(LookupGroupDao groupDao) {
        this.groupDao = groupDao;
    }

    /**
     * Gets a value from cache if present
     */
    public String getValue(int groupId, String key) {
        SimpleCache<String, String> cache = getOrCreateCache(groupId);
        return cache.get(key);
    }

    /**
     * Adds or updates a value in cache
     */
    public void putValue(int groupId, String key, String value) {
        SimpleCache<String, String> cache = getOrCreateCache(groupId);
        cache.put(key, value);
    }

    /**
     * Removes a specific value from cache
     */
    public void removeValue(int groupId, String key) {
        SimpleCache<String, String> cache = getOrCreateCache(groupId);
        cache.remove(key);
    }

    /**
     * Clears all values for a specific group
     */
    public void clearGroupCache(int groupId) {
        SimpleCache<String, String> cache = groupCaches.get(groupId);
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
        SimpleCache<String, String> cache = groupCaches.get(groupId);
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
        SimpleCache<String, String> cache = groupCaches.get(groupId);

        if (cache instanceof GuavaCacheWrapper) {
            GuavaCacheWrapper<String, String> guava = (GuavaCacheWrapper<String, String>) cache;
            return guava.getGuavaCache().stats();
        }

        // FIFO or unknown cache types do not support stats
        return null;
    }

    /**
     * Creates or retrieves the cache for a group
     */
    private SimpleCache<String, String> getOrCreateCache(int groupId) {
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
    private SimpleCache<String, String> createCacheForGroup(LookupGroup group) {
        int cacheSize = group.getCacheSize();
        String cachePolicy = group.getCachePolicy();

        if ("FIFO".equalsIgnoreCase(cachePolicy)) {
            return new FifoCacheWrapper<>(cacheSize);
        } else { // Default to LRU
            Cache<String, String> guavaCache = CacheBuilder.newBuilder()
                    .maximumSize(cacheSize)
//                    .expireAfterWrite(10, TimeUnit.MINUTES) // TTL (optional)
                    .recordStats()
                    .build();
            return new GuavaCacheWrapper<>(guavaCache);
        }
    }
}

