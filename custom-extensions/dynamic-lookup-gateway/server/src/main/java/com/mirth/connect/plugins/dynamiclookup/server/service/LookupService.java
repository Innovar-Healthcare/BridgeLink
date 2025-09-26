/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.cache.CacheStats;
import com.mirth.connect.plugins.dynamiclookup.server.cache.LookupCacheManager;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupAuditDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupStatisticsDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupValueDao;
import com.mirth.connect.plugins.dynamiclookup.server.exception.DuplicateGroupNameException;
import com.mirth.connect.plugins.dynamiclookup.server.exception.GroupNotFoundException;
import com.mirth.connect.plugins.dynamiclookup.server.exception.ValueOperationException;
import com.mirth.connect.plugins.dynamiclookup.server.exception.ValueTableCreationException;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.CacheStatistics;
import com.mirth.connect.plugins.dynamiclookup.shared.model.HistoryFilterState;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupAudit;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupStatistics;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;
import com.mirth.connect.plugins.dynamiclookup.shared.util.TtlUtils;

public class LookupService {
	private static LookupService instance = null;

	private LookupGroupDao groupDao;
	private LookupValueDao valueDao;
	private LookupAuditDao auditDao;
	private LookupStatisticsDao statisticsDao;
	private LookupCacheManager cacheManager;
	private final Logger logger = LogManager.getLogger(this.getClass());

	public static LookupService getInstance() {
		synchronized (LookupService.class) {
			if (instance == null) {
				instance = new LookupService();
			}
			return instance;
		}
	}

	// Constructor
	public LookupService() {

	}

	// Constructor and dependency injection
	public LookupService(LookupGroupDao groupDao, LookupValueDao valueDao, LookupAuditDao auditDao, LookupStatisticsDao statisticsDao, LookupCacheManager cacheManager) {
		this.groupDao = groupDao;
		this.valueDao = valueDao;
		this.auditDao = auditDao;
		this.statisticsDao = statisticsDao;
		this.cacheManager = cacheManager;
	}

	public void init(LookupGroupDao groupDao, LookupValueDao valueDao, LookupAuditDao auditDao, LookupStatisticsDao statisticsDao, LookupCacheManager cacheManager) {
		this.groupDao = groupDao;
		this.valueDao = valueDao;
		this.auditDao = auditDao;
		this.statisticsDao = statisticsDao;
		this.cacheManager = cacheManager;
	}

	// Group Management

	/**
	 * Retrieves all lookup groups
	 */
	public List<LookupGroup> getAllGroups() {
		return groupDao.getAllGroups();
	}

	/**
	 * Gets a specific group by ID
	 */
	public LookupGroup getGroupById(int id) {
		return groupDao.getGroupById(id);
	}

	/**
	 * Gets a specific group by name
	 */
	public LookupGroup getGroupByName(String name) {
		return groupDao.getGroupByName(name);
	}

	/**
	 * Creates a new lookup group and its corresponding value table
	 */
	public int createGroup(LookupGroup group) {
		// Validate group data
		validateGroup(group);

		// Check for duplicate name
		if (groupDao.getGroupByName(group.getName()) != null) {
			throw new DuplicateGroupNameException("Group name already exists: " + group.getName());
		}

		if (group.getCachePolicy() == null || group.getCachePolicy().isEmpty()) {
			group.setCachePolicy(LookupCacheManager.POLICY_LRU); // Default cache policy
		}

		// Insert group to get ID
		int groupId = groupDao.insertGroup(group);
		group.setId(groupId);

		try {
			// Create value table
			String tableName = getTableNameForGroup(groupId);
			groupDao.createValueTable(tableName);

			// Initialize statistics
			statisticsDao.insertStatistics(groupId);

			// Build cache instance for this group
			cacheManager.createOrRebuildGroupCache(group);

			logger.info("Created lookup group: {} (ID: {})", group.getName(), groupId);
			return groupId;
		} catch (Exception e) {
			// If table creation fails, delete the group
			try {
				groupDao.deleteGroup(groupId);
			} catch (Exception ex) {
				logger.error("Failed to rollback group creation after table creation error", ex);
			}
			throw new ValueTableCreationException("Failed to create value table for group: " + e.getMessage(), e);
		}
	}

	/**
	 * Updates an existing lookup group
	 */
	public void updateGroup(LookupGroup group) {
		// Validate group data
		validateGroup(group);

		// Check if group exists
		LookupGroup existingGroup = groupDao.getGroupById(group.getId());
		if (existingGroup == null) {
			throw new GroupNotFoundException("Group not found with ID: " + group.getId());
		}

		// Check if name changed and ensure it's still unique
		if (!existingGroup.getName().equals(group.getName())) {
			LookupGroup otherGroup = groupDao.getGroupByName(group.getName());
			if (otherGroup != null && otherGroup.getId() != group.getId()) {
				throw new DuplicateGroupNameException("Group name already exists: " + group.getName());
			}
		}

		// Update group
		groupDao.updateGroup(group);

		// Detect cache-setting changes
		boolean sizeChanged = existingGroup.getCacheSize() != group.getCacheSize();
		boolean policyChanged = !existingGroup.getCachePolicy().equals(group.getCachePolicy());

		// Clear cache if cache settings changed
		if (sizeChanged || policyChanged) {
			logger.info("Cache settings changed for group {} (ID: {}): size {} -> {}, policy {} -> {}", group.getName(), group.getId(), existingGroup.getCacheSize(), group.getCacheSize(), existingGroup.getCachePolicy(), group.getCachePolicy());

			cacheManager.createOrRebuildGroupCache(group);
		}

		logger.info("Updated lookup group: {} (ID: {})", group.getName(), group.getId());
	}

	/**
	 * Deletes a lookup group and its value table
	 */
	public void deleteGroup(int groupId) {
		// Get group to verify it exists
		LookupGroup group = groupDao.getGroupById(groupId);
		if (group == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		String groupName = group.getName();

		// Drop value table
		String tableName = getTableNameForGroup(groupId);
		groupDao.dropValueTable(tableName);

		// Delete group metadata
		groupDao.deleteGroup(groupId);

		// Clear cache
		cacheManager.removeGroupCache(groupId);

		logger.info("Deleted lookup group: {} (ID: {})", groupName, groupId);
	}

	public int importGroup(LookupGroup group, Map<String, String> values, boolean updateIfExists, String userId) {
		try {
			// Check if group already exists
			LookupGroup existing = groupDao.getGroupByName(group.getName());
			int groupId;
			String tableName;

			if (existing != null) {
				if (!updateIfExists) {
					throw new DuplicateGroupNameException("Group name already exists: " + group.getName());
				}

				// Update existing group metadata
				groupId = existing.getId();
				group.setId(groupId);

				updateGroup(group);

				// Clear existing values before inserting new ones
				tableName = getTableNameForGroup(groupId);

				// Get count before deletion for audit
				long count = valueDao.getValueCount(tableName);

				// Delete all values
				valueDao.deleteAllValues(tableName);

				// Audit
				recordAudit(groupId, tableName, "*", "DELETE_ALL", count + " values", null, userId);

				// Clear cache for this group
				cacheManager.clearGroupCache(groupId);
			} else {
				// Insert new group and get generated ID
				groupId = createGroup(group);
				group.setId(groupId);
				tableName = getTableNameForGroup(groupId);
			}

			// Import all values
			int count = valueDao.importValues(tableName, values);

			// Audit
			recordAudit(groupId, tableName, "*", "IMPORT", null, count + " values imported", userId);

			logger.info("Imported {} values into group: {} (ID: {})", count, group.getName(), groupId);

			return count;
		} catch (DuplicateGroupNameException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Failed to import lookup group: " + e.getMessage(), e);
			throw new ValueOperationException("Failed to import lookup group: " + e.getMessage(), e);
		}
	}

	// Value Management

	/**
	 * Retrieves a raw lookup value (including its metadata) directly from the
	 * database.
	 * <p>
	 * This method bypasses the cache and is typically used by administrative tools
	 * or the UI to display the current value and its update metadata.
	 * </p>
	 *
	 * @param groupId the ID of the lookup group
	 * @param key     the lookup key
	 * @return a {@link LookupValue} containing the value and updated timestamp, or
	 *         {@code null} if the key is not found in the specified group
	 * @throws GroupNotFoundException if the group does not exist
	 */
	public LookupValue getLookupValue(int groupId, String key) {
		validateKey(key);

		// Verify group exists
		if (groupDao.getGroupById(groupId) == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		String tableName = getTableNameForGroup(groupId);
		return valueDao.getLookupValue(tableName, key);
	}

	/**
	 * Retrieves a value from a lookup group without applying TTL validation.
	 * <p>
	 * This method always returns the value if found, regardless of how old it is.
	 * It is equivalent to calling {@code getValue(groupId, key, 0)}.
	 * </p>
	 *
	 * @param groupId the lookup group ID
	 * @param key     the lookup key
	 * @return the value if found in cache or database, otherwise null
	 */
	public String getValue(int groupId, String key) {
		return getValue(groupId, key, 0);
	}

	/**
	 * Retrieves a value from a lookup group using the following flow:
	 * <ol>
	 * <li>Attempt to retrieve from cache.</li>
	 * <li>If not present or stale (based on TTL), load from the database.</li>
	 * <li>If loaded from the database, cache the value (along with its updatedAt
	 * timestamp).</li>
	 * <li>If the database value is also stale (based on TTL), return null.</li>
	 * </ol>
	 *
	 * @param groupId    the lookup group ID
	 * @param key        the lookup key
	 * @param ttlSeconds if > 0, the cached value must have an updatedAt within this
	 *                   TTL
	 * @return the value if found and valid, otherwise null
	 */
	public String getValue(int groupId, String key, long ttlSeconds) {
		validateKey(key);

		// Verify group exists
		if (groupDao.getGroupById(groupId) == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		// Try to get from cache first
		String value = cacheManager.getValue(groupId, key, ttlSeconds);
		boolean cacheHit = (value != null);

		if (!cacheHit) {
			// Not in cache, get from database
			String tableName = getTableNameForGroup(groupId);
			LookupValue lookupValue = valueDao.getLookupValue(tableName, key);

			if (lookupValue != null) {
				value = lookupValue.getValueData();
				Date updatedAt = lookupValue.getUpdatedDate();

				if (value != null && updatedAt != null) {
					// Add to cache
					cacheManager.putValue(groupId, key, value, updatedAt);

					// Re-validate against TTL
					if (!TtlUtils.isWithinTtlSeconds(updatedAt, ttlSeconds)) {
						value = null; // Reject if data is stale
					}
				}
			}
		}

		// Update statistics
		try {
			statisticsDao.updateStatistics(groupId, cacheHit);
		} catch (Exception e) {
			// Non-critical error, just log it
			logger.warn("Failed to update statistics: {}", e.getMessage());
		}

		return value;
	}

	/**
	 * Retrieves all values from a lookup group
	 */
	public Map<String, String> getAllValues(int groupId) {
		// Verify group exists
		if (groupDao.getGroupById(groupId) == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		String tableName = getTableNameForGroup(groupId);

		List<LookupValue> values = valueDao.getAllValues(tableName);
		Map<String, String> map = new LinkedHashMap<>();
		for (LookupValue value : values) {
			map.put(value.getKeyValue(), value.getValueData());
		}

		return map;
	}

	/**
	 * Returns the total count of lookup values in the specified group that match
	 * the given search pattern on key or value fields.
	 */
	public int searchLookupValuesCount(int groupId, String pattern) {
		if (groupDao.getGroupById(groupId) == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		String tableName = getTableNameForGroup(groupId);

		long count = valueDao.searchLookupValuesCount(tableName, pattern);

		return Math.toIntExact(count);
	}

	/**
	 * Retrieves key-value pairs from the specified lookup group that match the
	 * given pattern.
	 */
	public List<LookupValue> searchLookupValues(Integer groupId, Integer offset, Integer limit, String pattern) {
		// Verify group exists
		if (groupDao.getGroupById(groupId) == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		String tableName = getTableNameForGroup(groupId);

		return valueDao.searchLookupValues(tableName, offset, limit, pattern);
	}

	/**
	 * Retrieves values matching a pattern from a lookup group
	 */
	public Map<String, String> getMatchingValues(int groupId, String keyPattern) {
		// Verify group exists
		if (groupDao.getGroupById(groupId) == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		String tableName = getTableNameForGroup(groupId);
		List<LookupValue> values = valueDao.getMatchingValues(tableName, keyPattern);

		Map<String, String> map = new LinkedHashMap<>();
		for (LookupValue value : values) {
			map.put(value.getKeyValue(), value.getValueData());
		}

		return map;
	}

	/**
	 * Retrieves multiple values from a lookup group without applying TTL
	 * validation.
	 * <p>
	 * This method checks the cache first. Any missing values are retrieved from the
	 * database and added to the cache. Values are returned regardless of how old
	 * they are.
	 * </p>
	 *
	 * @param groupId the lookup group ID
	 * @param keys    the list of lookup keys to retrieve
	 * @return a map of keys to values for those that were found
	 * @throws GroupNotFoundException if the group does not exist
	 */
	public Map<String, String> getBatchValues(int groupId, List<String> keys) {
		return getBatchValues(groupId, keys, 0);
	}

	/**
	 * Retrieves multiple values from a lookup group, applying optional TTL
	 * validation.
	 * <p>
	 * Lookup flow for each key:
	 * <ol>
	 * <li>Try to retrieve from cache.</li>
	 * <li>If not found or stale (based on TTL), load from the database and cache
	 * it.</li>
	 * <li>If the database value is also stale, it is excluded from the result.</li>
	 * </ol>
	 * TTL is based on the value's {@code updatedAt} timestamp.
	 * </p>
	 *
	 * @param groupId    the lookup group ID
	 * @param keys       the list of lookup keys to retrieve
	 * @param ttlSeconds time-to-live in seconds; if 0 or less, TTL is ignored
	 * @return a map of keys to values that passed TTL validation (if applicable)
	 * @throws GroupNotFoundException if the group does not exist
	 */
	public Map<String, String> getBatchValues(int groupId, List<String> keys, long ttlSeconds) {
		// Verify group exists
		if (groupDao.getGroupById(groupId) == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		Map<String, String> result = new HashMap<>();
		String tableName = getTableNameForGroup(groupId);

		// First check cache for all keys
		int cacheHits = 0;
		List<String> keysToFetch = new ArrayList<>();

		for (String key : keys) {
			String value = cacheManager.getValue(groupId, key, ttlSeconds);
			if (value != null) {
				result.put(key, value);
				cacheHits++;
			} else {
				keysToFetch.add(key);
			}
		}

		// Fetch any keys not found in cache
		if (!keysToFetch.isEmpty()) {
			for (String key : keysToFetch) {
				LookupValue lookupValue = valueDao.getLookupValue(tableName, key);
				if (lookupValue != null) {
					String value = lookupValue.getValueData();
					Date updatedAt = lookupValue.getUpdatedDate();

					if (value != null && updatedAt != null) {
						cacheManager.putValue(groupId, key, value, updatedAt);

						if (TtlUtils.isWithinTtlSeconds(updatedAt, ttlSeconds)) {
							result.put(key, value);
						} else {
							logger.debug("DB value for key '{}' excluded due to TTL (updatedAt={}, ttlSeconds={})", key, updatedAt, ttlSeconds);
						}
					}
				}
			}
		}

		// Update statistics
		try {
			for (int i = 0; i < cacheHits; i++) {
				statisticsDao.updateStatistics(groupId, true);
			}
			for (int i = 0; i < keysToFetch.size(); i++) {
				statisticsDao.updateStatistics(groupId, false);
			}
		} catch (Exception e) {
			// Non-critical error, just log it
			logger.warn("Failed to update statistics: {}", e.getMessage());
		}

		return result;
	}

	/**
	 * Creates or updates a value in a lookup group
	 */
	public void setValue(int groupId, String key, String value, String userId) {
		// Validate inputs
		validateKey(key);
		if (value == null) {
			throw new IllegalArgumentException("Value cannot be null");
		}

		// Verify group exists
		LookupGroup group = groupDao.getGroupById(groupId);
		if (group == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		String tableName = getTableNameForGroup(groupId);
		String existingValue = valueDao.getValue(tableName, key);

		try {
			if (existingValue == null) {
				// Insert new value
				valueDao.insertValue(tableName, key, value);

				// Audit
				recordAudit(groupId, tableName, key, "CREATE", null, value, userId);

				logger.debug("Created lookup value - Group: {}, Key: {}", group.getName(), key);
			} else {
				// Update existing value
				valueDao.updateValue(tableName, key, value);

				// Audit
				recordAudit(groupId, tableName, key, "UPDATE", existingValue, value, userId);

				logger.debug("Updated lookup value - Group: {}, Key: {}", group.getName(), key);
			}

			// Update cache
			LookupValue lookupValue = valueDao.getLookupValue(tableName, key);
			cacheManager.putValue(groupId, key, value, lookupValue.getUpdatedDate());
		} catch (Exception e) {
			throw new ValueOperationException("Failed to set value: " + e.getMessage(), e);
		}
	}

	/**
	 * Deletes a value from a lookup group
	 */
	public void deleteValue(int groupId, String key, String userId) {
		validateKey(key);

		// Verify group exists
		LookupGroup group = groupDao.getGroupById(groupId);
		if (group == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		String tableName = getTableNameForGroup(groupId);
		String existingValue = valueDao.getValue(tableName, key);

		if (existingValue != null) {
			try {
				// Delete value
				valueDao.deleteValue(tableName, key);

				// Audit
				recordAudit(groupId, tableName, key, "DELETE", existingValue, null, userId);

				// Remove from cache
				cacheManager.removeValue(groupId, key);

				logger.debug("Deleted lookup value - Group: {}, Key: {}", group.getName(), key);
			} catch (Exception e) {
				throw new ValueOperationException("Failed to delete value: " + e.getMessage(), e);
			}
		} else {
			logger.debug("No value found to delete - Group: {}, Key: {}", group.getName(), key);
		}
	}

	/**
	 * Deletes all values from a lookup group
	 */
	public void deleteAllValues(int groupId, String userId) {
		// Verify group exists
		LookupGroup group = groupDao.getGroupById(groupId);
		if (group == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		String tableName = getTableNameForGroup(groupId);

		try {
			// Get count before deletion for audit
			long count = valueDao.getValueCount(tableName);

			// Delete all values
			valueDao.deleteAllValues(tableName);

			// Audit
			recordAudit(groupId, tableName, "*", "DELETE_ALL", count + " values", null, userId);

			// Clear cache
			cacheManager.clearGroupCache(groupId);

			logger.info("Deleted all values from group: {} (ID: {}), count: {}", group.getName(), groupId, count);
		} catch (Exception e) {
			throw new ValueOperationException("Failed to delete all values: " + e.getMessage(), e);
		}
	}

	/**
	 * Imports values into a lookup group
	 */
	public int importValues(int groupId, Map<String, String> values, boolean clearExisting, String userId) {
		// Verify group exists
		LookupGroup group = groupDao.getGroupById(groupId);
		if (group == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		if (values == null || values.isEmpty()) {
			return 0;
		}

		String tableName = getTableNameForGroup(groupId);

		try {
			// Clear existing values if requested
			if (clearExisting) {
				valueDao.deleteAllValues(tableName);
				// Audit the clearing operation
				recordAudit(groupId, tableName, "*", "CLEAR_ALL", "Before import", null, userId);
			}

			// Import all values
			int count = valueDao.importValues(tableName, values);

			// Audit
			recordAudit(groupId, tableName, "*", "IMPORT", null, count + " values imported", userId);

			// Clear cache for this group
			cacheManager.clearGroupCache(groupId);

			logger.info("Imported {} values into group: {} (ID: {})", count, group.getName(), groupId);
			return count;
		} catch (Exception e) {
			throw new ValueOperationException("Failed to import values: " + e.getMessage(), e);
		}
	}

	// Statistics and Monitoring

	/**
	 * Gets statistics for a lookup group
	 */
	public LookupStatistics getStatistics(int groupId) {
		// Verify group exists
		if (groupDao.getGroupById(groupId) == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		return statisticsDao.getStatistics(groupId);
	}

	/**
	 * Returns a complete summary of cache metrics for the given group, including
	 * Guava stats, current entry count, and configured size limit.
	 */
	public CacheStatistics getCacheStatistics(int groupId) {
		LookupGroup group = groupDao.getGroupById(groupId);
		if (group == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		CacheStats stats = cacheManager.getCacheStats(groupId);
		boolean supported = (stats != null);

		return new CacheStatistics(supported, group.getCachePolicy(), cacheManager.getCacheSize(groupId), group.getCacheSize(), supported ? stats.hitCount() : 0, supported ? stats.missCount() : 0, supported ? stats.loadSuccessCount() : 0, supported ? stats.loadExceptionCount() : 0, supported ? stats.totalLoadTime() : 0, supported ? stats.evictionCount() : 0);
	}

	/**
	 * Resets statistics for a lookup group
	 */
	public void resetStatistics(int groupId) {
		// Verify group exists
		if (groupDao.getGroupById(groupId) == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		statisticsDao.resetStatistics(groupId);
		logger.info("Reset statistics for group ID: {}", groupId);
	}

	/**
	 * Gets audit entries for a lookup group
	 */
	public List<LookupAudit> getAuditEntries(int groupId, int offset, int limit) {
		return auditDao.getAuditEntriesByGroup(groupId, offset, limit);
	}

	/**
	 * Search audit entries for a lookup group
	 */
	public List<LookupAudit> searchAuditEntries(int groupId, int offset, int limit, HistoryFilterState filter) {
		return auditDao.searchAuditEntriesByGroup(groupId, offset, limit, filter);
	}

	/**
	 * Count audit entries for a lookup group
	 */
	public int getAuditEntryCount(int groupId) {
		long count = auditDao.getAuditEntryCount(groupId);

		return Math.toIntExact(count);
	}

	/**
	 * Count audit entries for a lookup group
	 */
	public int searchAuditEntryCount(int groupId, HistoryFilterState filter) {
		long count = auditDao.searchAuditEntryCount(groupId, filter);

		return Math.toIntExact(count);
	}

	/**
	 * Clears the in-memory cache for a specific lookup group.
	 */
	public void clearGroupCache(int groupId) {
		// Verify group exists
		LookupGroup group = groupDao.getGroupById(groupId);
		if (group == null) {
			throw new GroupNotFoundException("Group not found with ID: " + groupId);
		}

		cacheManager.clearGroupCache(groupId);
	}

	/**
	 * Clears the in-memory caches for all lookup groups.
	 */
	public void clearAllCaches() {
		cacheManager.clearAllCaches();
	}

	// Helper methods

	/**
	 * Constructs the table name for a group
	 */
	private String getTableNameForGroup(int groupId) {
		return "LOOKUP_VALUE_" + groupId;
	}

	/**
	 * Records an audit entry
	 */
	private void recordAudit(int groupId, String tableName, String key, String action, String oldValue, String newValue, String userId) {
		try {
			LookupAudit audit = new LookupAudit();
			audit.setGroupId(groupId);
			audit.setTableName(tableName);
			audit.setKeyValue(key);
			audit.setAction(action);
			audit.setOldValue(oldValue);
			audit.setNewValue(newValue);
			audit.setUserId(userId);

			auditDao.insertAuditEntry(audit);
		} catch (Exception e) {
			// Non-critical error, just log it
			logger.warn("Failed to record audit entry: {}", e.getMessage());
		}
	}

	/**
	 * Validates a lookup group
	 */
	private void validateGroup(LookupGroup group) {
		if (group == null) {
			throw new IllegalArgumentException("Group cannot be null");
		}

		if (group.getName() == null || group.getName().trim().isEmpty()) {
			throw new IllegalArgumentException("Group name cannot be empty");
		}

		if (group.getName().length() > 255) {
			throw new IllegalArgumentException("Group name cannot exceed 255 characters");
		}

		// Validate cache policy
		String policy = group.getCachePolicy();
		if (policy != null && !policy.isEmpty() && !policy.equalsIgnoreCase("LRU") && !policy.equalsIgnoreCase("FIFO")) {
			throw new IllegalArgumentException("Invalid cache policy: " + policy + ". Must be LRU or FIFO");
		}

		// Validate cache size
		if (group.getCacheSize() < 0) {
			throw new IllegalArgumentException("Cache size must be >= 0");
		}
	}

	/**
	 * Validates a key
	 */
	private void validateKey(String key) {
		if (key == null || key.trim().isEmpty()) {
			throw new IllegalArgumentException("Key cannot be empty");
		}

		if (key.length() > 255) {
			throw new IllegalArgumentException("Key cannot exceed 255 characters");
		}
	}
}
