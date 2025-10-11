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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;

import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupAuditDao;
import com.mirth.connect.plugins.dynamiclookup.shared.model.HistoryFilterState;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupAudit;

public class MyBatisLookupAuditDao implements LookupAuditDao {
	private SqlSessionManager sqlSessionManager;

	public MyBatisLookupAuditDao(SqlSessionManager sqlSessionManager) {
		this.sqlSessionManager = sqlSessionManager;
	}

	@Override
	public void insertAuditEntry(LookupAudit audit) {
		SqlSession session = sqlSessionManager.openSession();
		boolean commitSuccess = false;

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("groupId", audit.getGroupId());
			params.put("tableName", audit.getTableName());
			params.put("keyValue", audit.getKeyValue());
			params.put("action", audit.getAction());
			params.put("oldValue", audit.getOldValue());
			params.put("newValue", audit.getNewValue());
			params.put("userId", audit.getUserId());
			session.insert("Lookup.insertAuditEntry", params);
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
	public List<LookupAudit> getAuditEntriesByGroup(int groupId, int offset, int limit) {
		SqlSession session = sqlSessionManager.openSession();

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("groupId", groupId);
			params.put("offset", offset);
			params.put("limit", limit);
			return session.selectList("Lookup.getAuditEntriesByGroup", params);
		} finally {
			session.close();
		}
	}

	@Override
	public List<LookupAudit> searchAuditEntriesByGroup(int groupId, int offset, int limit, HistoryFilterState filter) {
		SqlSession session = sqlSessionManager.openSession();

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("groupId", groupId);
			params.put("offset", offset);
			params.put("limit", limit);

			// Add filters from HistoryFilterState
			params.put("keyValue", filter.getKeyValue());
			params.put("action", filter.getAction());
			params.put("userId", filter.getUserId());
			params.put("startDate", filter.getStartDate());
			params.put("endDate", filter.getEndDate());
			return session.selectList("Lookup.searchAuditEntriesByGroup", params);
		} finally {
			session.close();
		}
	}

	@Override
	public List<LookupAudit> getAuditEntriesByKey(int groupId, String keyValue, int limit) {
		return new java.util.ArrayList<>();
	}

	@Override
	public long getAuditEntryCount(int groupId) {
		SqlSession session = sqlSessionManager.openSession();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("groupId", groupId);
			return session.selectOne("Lookup.getAuditEntryCount", params);
		} finally {
			session.close(); // Ensure session is always closed
		}
	}

	@Override
	public long searchAuditEntryCount(int groupId, HistoryFilterState filter) {
		SqlSession session = sqlSessionManager.openSession();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("groupId", groupId);

			// Add filters from HistoryFilterState
			params.put("keyValue", filter.getKeyValue());
			params.put("action", filter.getAction());
			params.put("userId", filter.getUserId());
			params.put("startDate", filter.getStartDate());
			params.put("endDate", filter.getEndDate());
			return session.selectOne("Lookup.searchAuditEntryCount", params);
		} finally {
			session.close(); // Ensure session is always closed
		}
	}

	@Override
	public long deleteAuditEntriesBefore(Date cutoff) {
		SqlSession session = sqlSessionManager.openSession();
		boolean commitSuccess = false;
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("cutoff", cutoff);

			int deleted = session.delete("Lookup.deleteAuditEntriesBefore", params);
			session.commit();
			commitSuccess = true;
			return deleted;
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
}
