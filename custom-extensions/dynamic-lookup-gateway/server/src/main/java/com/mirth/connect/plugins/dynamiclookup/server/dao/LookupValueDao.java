/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.dao;

import java.util.List;
import java.util.Map;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;

public interface LookupValueDao {
	// Value operations
	LookupValue getLookupValue(String tableName, String keyValue);

	String getValue(String tableName, String keyValue);

	List<LookupValue> getAllValues(String tableName);

	List<LookupValue> searchLookupValues(String tableName, Integer offset, Integer limit, String pattern);

	List<LookupValue> getMatchingValues(String tableName, String keyPattern);

	List<String> getKeys(String tableName, String keyPattern);

	void insertValue(String tableName, String keyValue, String valueData);

	void updateValue(String tableName, String keyValue, String valueData);

	void deleteValue(String tableName, String keyValue);

	void deleteAllValues(String tableName);

	int importValues(String tableName, Map<String, String> values);

	long getValueCount(String tableName);

	long searchLookupValuesCount(String tableName, String pattern);

	boolean putIfAbsent(String tableName, String keyValue, String valueData);

	boolean compareAndSwap(String tableName, String keyValue, String expectedValue, String newValue);

	boolean updateValueByDelta(String tableName, String keyValue, Long delta);
}
