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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;

import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupDao;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

public class MyBatisLookupGroupDao implements LookupGroupDao {
    private SqlSessionManager sqlSessionManager;

    public MyBatisLookupGroupDao(SqlSessionManager sqlSessionManager) {
        this.sqlSessionManager = sqlSessionManager;
    }

    @Override
    public LookupGroup getGroupById(int id) {
        SqlSession session = sqlSessionManager.openSession();
        try {
            return session.selectOne("Lookup.getGroupById", id);
        } finally {
            session.close();
        }
    }

    @Override
    public LookupGroup getGroupByName(String name) {
        SqlSession session = sqlSessionManager.openSession();
        try {
            return session.selectOne("Lookup.getGroupByName", name);
        } finally {
            session.close();
        }
    }

    @Override
    public List<LookupGroup> getAllGroups() {
        SqlSession session = sqlSessionManager.openSession();
        try {
            return session.selectList("Lookup.getAllGroups");
        } finally {
            session.close();
        }
    }

    @Override
    public int insertGroup(LookupGroup group) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("name", group.getName());
            params.put("description", group.getDescription());
            params.put("version", group.getVersion());
            params.put("cacheSize", group.getCacheSize());
            params.put("cachePolicy", group.getCachePolicy());
            session.insert("Lookup.insertGroup", params);
            session.commit();
            commitSuccess = true;

            Object idObj = params.get("id");
            if (idObj instanceof BigDecimal) {
                return ((BigDecimal) idObj).intValue();
            } else if (idObj instanceof BigInteger) {
                return ((BigInteger) idObj).intValue();
            } else if (idObj instanceof Integer) {
                return (Integer) idObj;
            } else {
                throw new IllegalStateException("Unexpected ID type: " + idObj.getClass());
            }
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
    public void updateGroup(LookupGroup group) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("id", group.getId());
            params.put("name", group.getName());
            params.put("description", group.getDescription());
            params.put("version", group.getVersion());
            params.put("cacheSize", group.getCacheSize());
            params.put("cachePolicy", group.getCachePolicy());

            session.update("Lookup.updateGroup", params);
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
    public void deleteGroup(int id) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            session.delete("Lookup.deleteGroup", id);
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
    public void createValueTable(String tableName) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            session.update("LookupValue.createLookupValueTable", params);
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
    public void createValueJsonTable(String tableName) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            session.update("LookupValue.createLookupValueJsonTable", params);
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
    public void dropValueTable(String tableName) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            session.update("LookupValue.dropLookupValueTable", params);
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
    public boolean tableExists(String tableName) {
        return false;
    }
}
