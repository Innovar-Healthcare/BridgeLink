package com.mirth.connect.plugins.dynamiclookup.server.userutil;

import com.mirth.connect.plugins.dynamiclookup.server.service.LookupService;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.CacheStatistics;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupStatistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.cache.CacheStats;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides LookupHelper utility methods.
 */
public class LookupHelper {
    private static LookupService lookupService;
    private static final Logger logger = LogManager.getLogger(LookupHelper.class);

    /**
     * Initializes the helper with a reference to the lookup service
     */
    public static void initialize(LookupService service) {
        lookupService = service;
    }

    /**
     * Retrieves a value from a lookup group
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
            logger.error("Error in lookup operation: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retrieves a value with a default fallback
     */
    public static String get(String groupName, String key, String defaultValue) {
        String value = get(groupName, key);
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
            logger.error("Error in lookup operation: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Retrieves multiple values at once
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
}
