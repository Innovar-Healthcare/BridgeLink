/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.dao.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;

import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupValueDao;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;

public class MyBatisLookupValueDao implements LookupValueDao {
	private SqlSessionManager sqlSessionManager;

	public MyBatisLookupValueDao(SqlSessionManager sqlSessionManager) {
		this.sqlSessionManager = sqlSessionManager;
	}

	@Override
	public LookupValue getLookupValue(String tableName, String keyValue) {
		SqlSession session = sqlSessionManager.openSession();

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			params.put("keyValue", keyValue);
			return session.selectOne("Lookup.getLookupValue", params);
		} finally {
			session.close();
		}
	}

	@Override
	public String getValue(String tableName, String keyValue) {
		SqlSession session = sqlSessionManager.openSession();

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			params.put("keyValue", keyValue);
			return session.selectOne("Lookup.getValue", params);
		} finally {
			session.close();
		}
	}

	@Override
	public List<LookupValue> getAllValues(String tableName) {
		SqlSession session = sqlSessionManager.openSession();

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			return session.selectList("Lookup.getLookupValues", params);
		} finally {
			session.close();
		}
	}

	@Override
	public List<LookupValue> searchLookupValues(String tableName, Integer offset, Integer limit, String pattern) {
		SqlSession session = sqlSessionManager.openSession();

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			params.put("offset", offset);
			params.put("limit", limit);
			params.put("pattern", pattern);
			return session.selectList("Lookup.searchLookupValues", params);
		} finally {
			session.close();
		}
	}

	@Override
	public List<LookupValue> getMatchingValues(String tableName, String keyPattern) {
		SqlSession session = sqlSessionManager.openSession();

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			params.put("keyPattern", keyPattern);
			return session.selectList("Lookup.getMatchingLookupValues", params);
		} finally {
			session.close();
		}
	}

	@Override
	public List<String> getKeys(String tableName, String keyPattern) {
		return new java.util.ArrayList<>();
	}

	@Override
	public void insertValue(String tableName, String keyValue, String valueData) {
		SqlSession session = sqlSessionManager.openSession();
		boolean commitSuccess = false;

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			params.put("keyValue", keyValue);
			params.put("valueData", valueData);
			session.insert("Lookup.insertValue", params);
			session.commit();
			commitSuccess = true;
		} finally {
			if (!commitSuccess) {
				try {
					session.rollback();
				} catch (Exception ignored) {
				}
			}
			session.close();
		}
	}

	@Override
	public void updateValue(String tableName, String keyValue, String valueData) {
		SqlSession session = sqlSessionManager.openSession();
		boolean commitSuccess = false;

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			params.put("keyValue", keyValue);
			params.put("valueData", valueData);
			session.insert("Lookup.updateValue", params);
			session.commit();
			commitSuccess = true;
		} finally {
			if (!commitSuccess) {
				try {
					session.rollback();
				} catch (Exception ignored) {
				}
			}
			session.close();
		}
	}

	@Override
	public void deleteValue(String tableName, String keyValue) {
		SqlSession session = sqlSessionManager.openSession();
		boolean commitSuccess = false;

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			params.put("keyValue", keyValue);
			session.delete("Lookup.deleteValue", params);
			session.commit();
			commitSuccess = true;
		} finally {
			if (!commitSuccess) {
				try {
					session.rollback();
				} catch (Exception ignored) {
				}
			}
			session.close();
		}
	}

	@Override
	public void deleteAllValues(String tableName) {
		SqlSession session = sqlSessionManager.openSession();
		boolean commitSuccess = false;

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			session.delete("Lookup.deleteAllValues", params);
			session.commit();
			commitSuccess = true;
		} finally {
			if (!commitSuccess) {
				try {
					session.rollback();
				} catch (Exception ignored) {
				}
			}
			session.close();
		}
	}

	@Override
	public int importValues(String tableName, Map<String, String> values) {
		int affectedRows = 0;
		SqlSession session = sqlSessionManager.openSession();

		Set<String> existingKeys = findExistingKeys(tableName, values.keySet());
		if (!existingKeys.isEmpty()) {
			for (String key : existingKeys) {
				values.remove(key);
			}
		}

		try {
			for (Map.Entry<String, String> entry : values.entrySet()) {
				Map<String, Object> params = new HashMap<>();
				params.put("tableName", tableName);
				params.put("keyValue", entry.getKey());
				params.put("valueData", entry.getValue());
				try {
					int cnt = session.insert("Lookup.insertValue", params);
					affectedRows += cnt;
				} catch (Exception ignored) {
				}
			}

			session.commit();

			return affectedRows;
		} catch (Exception e) {
			try {
				session.rollback();
			} catch (Exception ignored) {
			}
			throw new RuntimeException("Import failed unexpectedly: " + e.getMessage(), e);
		} finally {
			session.close();
		}
	}

	@Override
	public long getValueCount(String tableName) {
		SqlSession session = sqlSessionManager.openSession();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			return session.selectOne("Lookup.getValueCount", params);
		} finally {
			session.close(); // Ensure session is always closed
		}
	}

	@Override
	public long searchLookupValuesCount(String tableName, String pattern) {
		SqlSession session = sqlSessionManager.openSession();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			params.put("pattern", pattern);
			return session.selectOne("Lookup.searchLookupValuesCount", params);
		} finally {
			session.close(); // Ensure session is always closed
		}
	}

	@Override
	public boolean putIfAbsent(String tableName, String keyValue, String valueData) {
		int affectedRows = 0;
		SqlSession session = sqlSessionManager.openSession();
		boolean commitSuccess = false;

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("tableName", tableName);
			params.put("keyValue", keyValue);
			params.put("valueData", valueData);

			affectedRows = session.insert("Lookup.putIfAbsent", params);

			session.commit();
			commitSuccess = true;
		} finally {
			if (!commitSuccess) {
				try {
					session.rollback();
				} catch (Exception ignored) {
				}
			}
			session.close();
		}

		return affectedRows > 0;
	}

	private Set<String> findExistingKeys(String tableName, Collection<String> keyValues) {
		if (keyValues == null || keyValues.isEmpty()) {
			return Collections.emptySet();
		}

		final int BATCH_SIZE = 1000;
		Set<String> existing = new HashSet<>();
		List<String> batch = new ArrayList<>(BATCH_SIZE);

		SqlSession session = null;
		try {
			session = sqlSessionManager.openSession();

			for (String k : keyValues) {
				batch.add(k);
				if (batch.size() == BATCH_SIZE) {
					existing.addAll(selectExistingBatch(session, tableName, batch));
					batch.clear();
				}
			}
			if (!batch.isEmpty()) {
				existing.addAll(selectExistingBatch(session, tableName, batch));
			}

		} catch (Exception e) {
			throw new RuntimeException("Failed to query existing keys: " + e.getMessage(), e);
		} finally {
			if (session != null) {
				try {
					session.close();
				} catch (Exception ignored) {
				}
			}
		}

		return existing;
	}

	private List<String> selectExistingBatch(SqlSession session, String tableName, List<String> batch) {
		Map<String, Object> params = new HashMap<>();
		params.put("tableName", tableName);
		params.put("keyValues", batch);
		return session.selectList("Lookup.selectExistingKeys", params);
	}

}
