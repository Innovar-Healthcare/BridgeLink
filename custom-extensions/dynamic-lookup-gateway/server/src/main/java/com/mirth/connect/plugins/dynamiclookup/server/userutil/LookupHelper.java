/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.userutil;

import com.mirth.connect.plugins.dynamiclookup.server.service.LookupService;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.CacheStatistics;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupStatistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides LookupHelper utility methods.
 */
public class LookupHelper {
    private static final String SYSTEM_USER_ID = "0";
    private static final Logger logger = LogManager.getLogger(LookupHelper.class);
    private static LookupService lookupService;

    /**
     * Initializes the helper with a reference to the lookup service
     */
    public static void initialize(LookupService service) {
        lookupService = service;
    }

    /**
     * Retrieves a value from a lookup group without applying TTL validation.
     * Always returns the best available value from cache or database.
     *
     * @param groupName the name of the lookup group
     * @param key       the lookup key
     * @return the value, or null if not found or error occurs
     */
    public static String get(String groupName, String key) {
        try {
            LookupGroup group = lookupService.getGroupByName(groupName);
            if (group == null) {
                logger.error("Lookup group not found: {}", groupName);
                return null;
            }

            return lookupService.getValue(group.getId(), key);
        } catch (Exception e) {
            logger.error("Failed to retrieve lookup value [group='{}', key='{}']: {}", groupName, key, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retrieves a value from a lookup group using TTL validation.
     * Returns null if the cached or database value is older than the specified TTL.
     *
     * @param groupName the name of the lookup group
     * @param key       the lookup key
     * @param ttlHours  maximum age in hours for the value to be considered valid (0 = no TTL enforcement)
     * @return the value if valid within TTL, otherwise null
     */
    public static String get(String groupName, String key, long ttlHours) {
        try {
            LookupGroup group = lookupService.getGroupByName(groupName);
            if (group == null) {
                logger.error("Lookup group not found: {}", groupName);
                return null;
            }

            return lookupService.getValue(group.getId(), key, ttlHours);
        } catch (Exception e) {
            logger.error("Failed to retrieve lookup value [group='{}', key='{}', ttl={}]: {}", groupName, key, ttlHours, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retrieves a value from a lookup group with a fallback default if the key is not found or null.
     * TTL is not applied — any available value (cached or from DB) will be returned.
     *
     * @param groupName    the name of the lookup group
     * @param key          the lookup key
     * @param defaultValue the fallback value to return if the lookup fails
     * @return the lookup value if found, otherwise the provided default
     */
    public static String get(String groupName, String key, String defaultValue) {
        String value = get(groupName, key);
        return value != null ? value : defaultValue;
    }

    /**
     * Retrieves a value from a lookup group with TTL enforcement and a fallback default.
     * If the cached or DB value is older than the TTL, or not found, the default is returned.
     *
     * @param groupName    the name of the lookup group
     * @param key          the lookup key
     * @param ttlHours     the TTL in hours; 0 or less means TTL is ignored
     * @param defaultValue the fallback value to return if lookup fails or is stale
     * @return the value if found and valid within TTL, otherwise the provided default
     */
    public static String get(String groupName, String key, long ttlHours, String defaultValue) {
        String value = get(groupName, key, ttlHours);
        return value != null ? value : defaultValue;
    }

    /**
     * Retrieves values matching a pattern
     */
    public static Map<String, String> getMatching(String groupName, String keyPattern) {
        try {
            LookupGroup group = lookupService.getGroupByName(groupName);
            if (group == null) {
                logger.error("Lookup group not found: {}", groupName);
                return Collections.emptyMap();
            }

            return lookupService.getMatchingValues(group.getId(), keyPattern);
        } catch (Exception e) {
            logger.error("Failed to retrieve matching lookup values [group='{}', pattern='{}']: {}", groupName, keyPattern, e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Retrieves multiple values at once from a lookup group without applying TTL validation.
     * This method returns all available values found in the cache or database,
     * regardless of their age or update time.
     *
     * @param groupName the name of the lookup group
     * @param keys      the list of keys to retrieve
     * @return a map of key-value pairs; keys not found will be excluded from the result
     */
    public static Map<String, String> getBatch(String groupName, List<String> keys) {
        try {
            LookupGroup group = lookupService.getGroupByName(groupName);
            if (group == null) {
                logger.error("Lookup group not found: {}", groupName);
                return Collections.emptyMap();
            }

            return lookupService.getBatchValues(group.getId(), keys);
        } catch (Exception e) {
            logger.error("Error in lookup operation: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Retrieves multiple values at once from a lookup group using TTL validation.
     * For each key, if the cached or database value is older than the specified TTL, it is excluded from the result.
     *
     * @param groupName the name of the lookup group
     * @param keys      the list of keys to retrieve
     * @param ttlHours  TTL in hours (0 = no TTL applied)
     * @return a map of valid key-value pairs; excludes keys with missing or stale data
     */
    public static Map<String, String> getBatch(String groupName, List<String> keys, long ttlHours) {
        try {
            LookupGroup group = lookupService.getGroupByName(groupName);
            if (group == null) {
                logger.error("Lookup group not found: {}", groupName);
                return Collections.emptyMap();
            }

            return lookupService.getBatchValues(group.getId(), keys, ttlHours);
        } catch (Exception e) {
            logger.error("Error in TTL-based lookup operation: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Checks if a key exists in a group
     */
    public static boolean exists(String groupName, String key) {
        try {
            return get(groupName, key) != null;
        } catch (Exception e) {
            logger.error("Error checking key existence: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gets cache statistics for a group
     */
    public static Map<String, Object> getCacheStats(String groupName) {
        try {
            LookupGroup group = lookupService.getGroupByName(groupName);
            if (group == null) {
                logger.error("Lookup group not found: {}", groupName);
                return Collections.emptyMap();
            }

            CacheStatistics cacheStatistics = lookupService.getCacheStatistics(group.getId());
            LookupStatistics dbStats = lookupService.getStatistics(group.getId());

            Map<String, Object> result = new HashMap<>();
            if (cacheStatistics != null) {
                result.put("hitCount", cacheStatistics.getHitCount());
                result.put("missCount", cacheStatistics.getMissCount());
                result.put("hitRatio", cacheStatistics.getHitRatio());
                result.put("missRatio", cacheStatistics.getMissRatio());
                result.put("evictionCount", cacheStatistics.getEvictionCount());
            }

            if (dbStats != null) {
                result.put("totalLookups", dbStats.getTotalLookups());
                result.put("cacheHits", dbStats.getCacheHits());
                result.put("lastAccessed", dbStats.getLastAccessed());
            }

            return result;
        } catch (Exception e) {
            logger.error("Error getting cache stats: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Sets a lookup value in the specified group
     */
    public static boolean set(String groupName, String key, String value) {
        try {
            LookupGroup group = lookupService.getGroupByName(groupName);
            if (group == null) {
                logger.error("Lookup group not found: {}", groupName);
                return false;
            }

            lookupService.setValue(group.getId(), key, value, SYSTEM_USER_ID);

            return true;
        } catch (Exception e) {
            logger.error("Failed to set lookup value [group='{}', key='{}']: {}", groupName, key, e.getMessage(), e);
            return false;
        }
    }
}
