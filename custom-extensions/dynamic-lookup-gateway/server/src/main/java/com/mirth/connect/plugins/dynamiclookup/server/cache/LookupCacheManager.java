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

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupDao;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.util.TtlUtils;

public class LookupCacheManager {
	public static final String POLICY_LRU = "LRU";
	public static final String POLICY_FIFO = "FIFO";

	private final Logger logger = LogManager.getLogger(this.getClass());

	private final Map<Integer, SimpleCache<String, CachedValue>> groupCaches = new ConcurrentHashMap<>();
	private final LookupGroupDao groupDao;

	public LookupCacheManager(LookupGroupDao groupDao) {
		this.groupDao = groupDao;
	}

	/**
	 * Create or rebuild the cache instance for the given group (policy/size
	 * applied).
	 */
	public void createOrRebuildGroupCache(LookupGroup group) {
		if (group == null) {
			logger.error("group must not be null");
			return;
		}

		groupCaches.put(group.getId(), buildCacheForGroup(group));

		if (group.getCacheSize() <= 0) {
			logger.info("Cache disabled for group {} (ID: {}, size={})", group.getName(), group.getId(), group.getCacheSize());
		} else {
			logger.info("Cache (policy={}, size={}) initialized for group {} (ID: {})", group.getCachePolicy(), group.getCacheSize(), group.getName(), group.getId());
		}
	}

	/** Remove cache instance for the given group (on group deletion). */
	public void removeGroupCache(int groupId) {
		groupCaches.remove(groupId);
		logger.info("Cache removed for groupId={}", groupId);
	}

	/**
	 * Build a cache instance (helper used by create/rebuild). Public if you need to
	 * unit test separately.
	 */
	public SimpleCache<String, CachedValue> buildCacheForGroup(LookupGroup group) {
		final int size = group.getCacheSize();
		final String policy = group.getCachePolicy();

		if (size <= 0) {
			return new NoCacheWrapper<>();
		}

		if (POLICY_FIFO.equalsIgnoreCase(policy)) {
			return new FifoCacheWrapper<>(size);
		}

		// default LRU
		Cache<String, CachedValue> guava = CacheBuilder.newBuilder().maximumSize(size)
//              .expireAfterWrite(10, TimeUnit.MINUTES) // TTL (optional)
				.recordStats().build();
		return new GuavaCacheWrapper<>(guava);
	}

	/**
	 * Gets a value from cache if present. If ttlSeconds > 0, the value must be
	 * within the TTL based on updatedAt. If ttlSeconds == 0, TTL is ignored and any
	 * cached value is returned.
	 */
	public String getValue(int groupId, String key, long ttlSeconds) {
		SimpleCache<String, CachedValue> cache = getCache(groupId);

		if (cache == null) {
			// Not initialized (or removed). Not an error by itself.
			return null;
		}

		CachedValue cached = cache.get(key);

		if (cached == null) {
			return null;
		}

		// Use shared TTL logic
		if (TtlUtils.isWithinTtlSeconds(cached.getUpdatedAt(), ttlSeconds)) {
			return cached.getValue();
		}

		return null;
	}

	/**
	 * Adds or updates a value in cache using updatedAt from the database.
	 */
	public void putValue(int groupId, String key, String value, Date updatedAt) {
		SimpleCache<String, CachedValue> cache = getCache(groupId);
		if (cache == null) {
			// Keep it quiet or warn, up to you:
			logger.debug("Skip cache put: cache not initialized for groupId={}", groupId);
			return;
		}

		cache.put(key, new CachedValue(value, updatedAt));
	}

	/**
	 * Removes a specific value from cache
	 */
	public void removeValue(int groupId, String key) {
		SimpleCache<String, CachedValue> cache = getCache(groupId);
		if (cache != null) {
			cache.remove(key);
		}
	}

	/**
	 * Clears all values for a specific group
	 */
	public void clearGroupCache(int groupId) {
		SimpleCache<String, CachedValue> cache = getCache(groupId);
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
		SimpleCache<String, CachedValue> cache = getCache(groupId);
		return (cache != null) ? cache.size() : 0;
	}

	/**
	 * Gets the configured maximum size of the group's cache.
	 */
	public int getCacheMaxSize(int groupId) {
		if (groupDao == null) {
			return -1;
		}

		LookupGroup group = groupDao.getGroupById(groupId);
		return (group != null) ? group.getCacheSize() : -1;
	}

	/**
	 * Gets cache statistics for a group (only available for LRU caches).
	 */
	public CacheStats getCacheStats(int groupId) {
		SimpleCache<String, CachedValue> cache = getCache(groupId);

		if (cache instanceof GuavaCacheWrapper) {
			GuavaCacheWrapper<String, CachedValue> guava = (GuavaCacheWrapper<String, CachedValue>) cache;
			return guava.getGuavaCache().stats();
		}

		// FIFO or unknown cache types do not support stats
		return null;
	}

	// ------------------------------------------------------------------------------------
	// Private helpers
	// ------------------------------------------------------------------------------------

	private SimpleCache<String, CachedValue> getCache(int groupId) {
		return groupCaches.get(groupId);
	}
}
