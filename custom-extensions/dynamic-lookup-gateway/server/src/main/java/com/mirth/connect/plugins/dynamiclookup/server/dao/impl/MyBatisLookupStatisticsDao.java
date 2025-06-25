package com.mirth.connect.plugins.dynamiclookup.server.dao.impl;

import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupStatisticsDao;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupStatistics;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionManager;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyBatisLookupStatisticsDao implements LookupStatisticsDao {
    private SqlSessionManager sqlSessionManager;

    public MyBatisLookupStatisticsDao(SqlSessionManager sqlSessionManager) {
        this.sqlSessionManager = sqlSessionManager;
    }

    @Override
    public void insertStatistics(int groupId) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("groupId", groupId);
            session.insert("Lookup.insertStatistics", params);
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
    public void updateStatistics(int groupId, boolean cacheHit) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("groupId", groupId);
            params.put("cacheHit", cacheHit);
            params.put("lastAccessed", new Date());

            session.update("Lookup.updateStatistics", params);
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
    public LookupStatistics getStatistics(int groupId) {
        SqlSession session = sqlSessionManager.openSession();
        try {
            return session.selectOne("Lookup.getStatistics", groupId);
        } finally {
            session.close();
        }
    }

    @Override
    public void resetStatistics(int groupId) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("groupId", groupId);
            params.put("resetDate", new Date());

            session.update("Lookup.resetStatistics", params);
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
    public List<LookupStatistics> getAllStatistics() {
        return new java.util.ArrayList<>();
    }
}
