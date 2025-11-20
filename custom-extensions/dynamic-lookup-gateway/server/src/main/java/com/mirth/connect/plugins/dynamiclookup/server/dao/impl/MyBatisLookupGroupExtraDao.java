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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;

import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupExtraDao;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;

public class MyBatisLookupGroupExtraDao implements LookupGroupExtraDao {
    private SqlSessionManager sqlSessionManager;

    public MyBatisLookupGroupExtraDao(SqlSessionManager sqlSessionManager) {
        this.sqlSessionManager = sqlSessionManager;
    }

    @Override
    public LookupGroupExtra getByGroupId(int groupId) {
        SqlSession session = sqlSessionManager.openSession();
        try {
            return session.selectOne("LookupGroupExtra.getByGroupId", groupId);
        } finally {
            session.close();
        }
    }

    @Override
    public List<LookupGroupExtra> getAllGroupExtras() {
        SqlSession session = sqlSessionManager.openSession();
        try {
            return session.selectList("LookupGroupExtra.getAllGroupExtras");
        } finally {
            session.close();
        }
    }

    @Override
    public int insert(LookupGroupExtra extra) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("groupId", extra.getGroupId());
            params.put("jsonIndexMode", extra.getJsonIndexMode());
            params.put("indexedJsonFields", extra.getIndexedJsonFields());

            int rows = session.insert("LookupGroupExtra.insert", params);

            session.commit();
            commitSuccess = true;

            return rows;
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
    public void update(LookupGroupExtra extra) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();

            params.put("groupId", extra.getGroupId());
            params.put("jsonIndexMode", extra.getJsonIndexMode());
            params.put("indexedJsonFields", extra.getIndexedJsonFields());

            session.update("LookupGroupExtra.update", params);
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
    public boolean extraExists(int groupId) {
        SqlSession session = sqlSessionManager.openSession();

        try {
            Integer result = session.selectOne("LookupGroupExtra.extraExists", groupId);
            return result != null && result == 1;
        } finally {
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
    }
}
