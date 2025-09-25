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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.dynamiclookup.server.exception.DuplicateGroupNameException;
import com.mirth.connect.plugins.dynamiclookup.server.service.LookupService;
import com.mirth.connect.plugins.dynamiclookup.server.util.LookupGroupConverter;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.CacheStatistics;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupStatistics;
import com.mirth.connect.plugins.dynamiclookup.shared.util.TtlUtils;

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
	 * Retrieves a value from a lookup group without applying TTL validation. Always
	 * returns the best available value from cache or database.
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
	 * Retrieves a value from a lookup group using TTL validation. Returns null if
	 * the cached or database value is older than the specified TTL.
	 *
	 * @param groupName the name of the lookup group
	 * @param key       the lookup key
	 * @param ttlHours  maximum age in hours for the value to be considered valid (0
	 *                  = no TTL enforcement)
	 * @return the value if valid within TTL, otherwise null
	 */
	public static String get(String groupName, String key, long ttlHours) {
		try {
			LookupGroup group = lookupService.getGroupByName(groupName);
			if (group == null) {
				logger.error("Lookup group not found: {}", groupName);
				return null;
			}

			long ttlSeconds = TtlUtils.hoursToSeconds(ttlHours);
			return lookupService.getValue(group.getId(), key, ttlSeconds);
		} catch (Exception e) {
			logger.error("Failed to retrieve lookup value [group='{}', key='{}', ttl={}]: {}", groupName, key, ttlHours, e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Retrieves a value from a lookup group using TTL validation. Returns null if
	 * the value is not found, stale, or if invalid TTL is provided.
	 *
	 * @param groupName  the name of the lookup group
	 * @param key        the lookup key
	 * @param ttlHours   hours part of TTL (>= 0)
	 * @param ttlMinutes minutes part of TTL (>= 0)
	 * @return the value if found and valid within TTL, otherwise null
	 */
	public static String get(String groupName, String key, long ttlHours, long ttlMinutes) {
		try {
			if (ttlHours < 0 || ttlMinutes < 0) {
				logger.error("TTL hours and minutes must be non-negative");
				return null;
			}

			LookupGroup group = lookupService.getGroupByName(groupName);
			if (group == null) {
				logger.error("Lookup group not found: {}", groupName);
				return null;
			}

			// Convert to total seconds
			long ttlSeconds = TtlUtils.hoursMinutesToSeconds(ttlHours, ttlMinutes);

			return lookupService.getValue(group.getId(), key, ttlSeconds);
		} catch (Exception e) {
			logger.error("Failed to retrieve lookup value [group='{}', key='{}', ttlHours={}, ttlMinutes={}]: {}", groupName, key, ttlHours, ttlMinutes, e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Retrieves a value from a lookup group with a fallback default if the key is
	 * not found or null. TTL is not applied — any available value (cached or from
	 * DB) will be returned.
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
	 * Retrieves a value from a lookup group with TTL enforcement and a fallback
	 * default. If the cached or DB value is older than the TTL, or not found, the
	 * default is returned.
	 *
	 * @param groupName    the name of the lookup group
	 * @param key          the lookup key
	 * @param ttlHours     hours part of TTL (must be >= 0)
	 * @param defaultValue the fallback value to return if lookup fails or is stale
	 * @return the value if found and valid within TTL, otherwise the provided
	 *         default
	 */
	public static String get(String groupName, String key, long ttlHours, String defaultValue) {
		String value = get(groupName, key, ttlHours);
		return value != null ? value : defaultValue;
	}

	/**
	 * Retrieves a value from a lookup group with TTL enforcement and a fallback
	 * default. If the cached or database value is not found or is older than the
	 * specified TTL, the provided default value is returned.
	 *
	 * @param groupName    the name of the lookup group
	 * @param key          the lookup key
	 * @param ttlHours     hours part of TTL (must be >= 0)
	 * @param ttlMinutes   minutes part of TTL (must be >= 0)
	 * @param defaultValue the fallback value to return if lookup fails or the value
	 *                     is stale
	 * @return the value if found and valid within TTL, otherwise
	 *         {@code defaultValue}
	 */
	public static String get(String groupName, String key, long ttlHours, long ttlMinutes, String defaultValue) {
		String value = get(groupName, key, ttlHours, ttlMinutes);
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
	 * Retrieves multiple values at once from a lookup group without applying TTL
	 * validation. This method returns all available values found in the cache or
	 * database, regardless of their age or update time.
	 *
	 * @param groupName the name of the lookup group
	 * @param keys      the list of keys to retrieve
	 * @return a map of key-value pairs; keys not found will be excluded from the
	 *         result
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
	 * For each key, if the cached or database value is older than the specified
	 * TTL, it is excluded from the result.
	 *
	 * @param groupName the name of the lookup group
	 * @param keys      the list of keys to retrieve
	 * @param ttlHours  hours part of TTL (must be >= 0)
	 * @return a map of valid key-value pairs; excludes keys with missing or stale
	 *         data
	 */
	public static Map<String, String> getBatch(String groupName, List<String> keys, long ttlHours) {
		try {
			LookupGroup group = lookupService.getGroupByName(groupName);
			if (group == null) {
				logger.error("Lookup group not found: {}", groupName);
				return Collections.emptyMap();
			}

			long ttlSeconds = TtlUtils.hoursToSeconds(ttlHours);
			return lookupService.getBatchValues(group.getId(), keys, ttlSeconds);
		} catch (Exception e) {
			logger.error("Error in TTL-based lookup operation: {}", e.getMessage(), e);
			return Collections.emptyMap();
		}
	}

	/**
	 * Retrieves multiple values at once from a lookup group using TTL validation.
	 * For each key, if the value is not found, stale, or TTL is invalid, that key
	 * is excluded from the result.
	 *
	 * @param groupName  the name of the lookup group
	 * @param keys       the list of keys to retrieve
	 * @param ttlHours   hours part of the TTL (>= 0)
	 * @param ttlMinutes minutes part of the TTL (>= 0)
	 * @return a map of valid key-value pairs; excludes keys with missing or stale
	 *         data, or an empty map if the group does not exist or an error occurs
	 */
	public static Map<String, String> getBatch(String groupName, List<String> keys, long ttlHours, long ttlMinutes) {
		try {
			if (ttlHours < 0 || ttlMinutes < 0) {
				logger.error("TTL hours and minutes must be non-negative");
				return Collections.emptyMap();
			}

			LookupGroup group = lookupService.getGroupByName(groupName);
			if (group == null) {
				logger.error("Lookup group not found: {}", groupName);
				return Collections.emptyMap();
			}

			long ttlSeconds = TtlUtils.hoursMinutesToSeconds(ttlHours, ttlMinutes);
			return lookupService.getBatchValues(group.getId(), keys, ttlSeconds);
		} catch (Exception e) {
			logger.error("Failed to retrieve batch lookup values [group='{}', keys={}, ttlHours={}, ttlMinutes={}]: {}", groupName, keys, ttlHours, ttlMinutes, e.getMessage(), e);
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

	/**
	 * Deletes a lookup value by group name and key.
	 * <p>
	 * Invoked from the Transformer layer in Mirth Connect core. Returns true if the
	 * value was successfully deleted, false if the group was not found or an error
	 * occurred.
	 *
	 * @param groupName the name of the lookup group
	 * @param key       the key to delete within the group
	 * @return true if successful, false otherwise
	 */
	public static boolean deleteValue(String groupName, String key) {
		try {
			LookupGroup group = lookupService.getGroupByName(groupName);
			if (group == null) {
				logger.error("Lookup group not found: {}", groupName);
				return false;
			}

			lookupService.deleteValue(group.getId(), key, SYSTEM_USER_ID);
			return true;
		} catch (Exception e) {
			logger.error("Failed to delete lookup value [group='{}', key='{}']: {}", groupName, key, e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Deletes all lookup values within the specified group.
	 * <p>
	 * Returns {@code true} if the operation completed successfully, or
	 * {@code false} if the group was not found or an error occurred.
	 *
	 * @param groupName the name of the lookup group
	 * @return true if all values were successfully deleted, false otherwise
	 */
	public static boolean deleteAllValues(String groupName) {
		try {
			LookupGroup group = lookupService.getGroupByName(groupName);
			if (group == null) {
				logger.error("Lookup group not found: {}", groupName);
				return false;
			}

			lookupService.deleteAllValues(group.getId(), SYSTEM_USER_ID);
			return true;
		} catch (Exception e) {
			logger.error("Failed to delete all lookup values [group='{}']: {}", groupName, e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Imports multiple key-value pairs into a lookup group.
	 *
	 * @param groupName     the name of the lookup group
	 * @param values        the key-value pairs to import
	 * @param clearExisting whether to clear existing values before import
	 * @return a result map with fields: - ok: 'true' if successful, 'false'
	 *         otherwise - groupId: the id of the group (when successful) -
	 *         importedCount: number of entries imported (when successful) -
	 *         errorCode / errorMessage (when failed)
	 */
	public static Map<String, Object> importValues(String groupName, Map<String, String> values, boolean clearExisting) {
		Map<String, Object> res = new LinkedHashMap<>();
		try {
			LookupGroup group = lookupService.getGroupByName(groupName);
			if (group == null) {
				logger.error("Lookup group not found: {}", groupName);
				res.put("ok", "false");
				res.put("errorCode", "GROUP_NOT_FOUND");
				res.put("errorMessage", "Lookup group not found: " + groupName);
				return res;
			}

			int importedCount = lookupService.importValues(group.getId(), values, clearExisting, SYSTEM_USER_ID);

			res.put("ok", "true");
			res.put("groupId", group.getId());
			res.put("importedCount", importedCount);
			return res;

		} catch (Exception e) {
			logger.error("Failed to import lookup values [group='{}']: {}", groupName, e.getMessage(), e);
			res.put("ok", "false");
			res.put("errorCode", "IMPORT_FAILED");
			res.put("errorMessage", e.getMessage());
			return res;
		}
	}

	/**
	 * Creates a new lookup group from a JS transformer payload.
	 * <p>
	 * Expects a flat map with keys: name, description, version, cacheSize,
	 * cachePolicy. Returns a result map: { ok: 'true', group: {...} } on success {
	 * ok: 'false', errorCode: "...", errorMessage: "..." } on failure
	 *
	 * @param map JS transformer input (Map<String,String>)
	 * @return result map for JS layer
	 */
	public static Map<String, Object> createGroup(Map<String, String> map) {
		Map<String, Object> res = new LinkedHashMap<>();
		try {
			// Convert from Map to LookupGroup
			LookupGroup group = LookupGroupConverter.toLookupGroup(map);

			// Persist using service layer (which handles validation)
			int id = lookupService.createGroup(group);
			LookupGroup full = lookupService.getGroupById(id);

			// Build response payload
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("id", full.getId());
			out.put("name", full.getName());
			out.put("description", full.getDescription());
			out.put("version", full.getVersion());
			out.put("cacheSize", full.getCacheSize());
			out.put("cachePolicy", full.getCachePolicy());

			res.put("ok", "true");
			res.put("group", out);
			return res;

		} catch (DuplicateGroupNameException e) {
			logger.warn("Duplicate group name: {}", map.get("name"));
			res.put("ok", "false");
			res.put("errorCode", "DUPLICATE_GROUP_NAME");
			res.put("errorMessage", "Group name already exists: " + map.get("name"));
			return res;

		} catch (Exception e) {
			logger.error("Failed to create lookup group [name='{}']: {}", map.get("name"), e.getMessage(), e);
			res.put("ok", "false");
			res.put("errorCode", "CREATE_FAILED");
			res.put("errorMessage", e.getMessage());
			return res;
		}
	}

	/**
	 * Deletes an entire lookup group by its name.
	 *
	 * @param groupName the name of the lookup group
	 * @return true if the group was successfully deleted, false otherwise
	 */
	public static boolean deleteGroup(String groupName) {
		try {
			LookupGroup group = lookupService.getGroupByName(groupName);
			if (group == null) {
				logger.error("Lookup group not found: {}", groupName);
				return false;
			}

			lookupService.deleteGroup(group.getId());
			return true;
		} catch (Exception e) {
			logger.error("Failed to delete lookup group [group='{}']: {}", groupName, e.getMessage(), e);
			return false;
		}
	}

}
