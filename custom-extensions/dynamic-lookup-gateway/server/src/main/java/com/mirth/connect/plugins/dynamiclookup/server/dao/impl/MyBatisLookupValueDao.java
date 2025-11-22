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
import com.mirth.connect.plugins.dynamiclookup.server.dao.support.JsonFieldCriterion;
import com.mirth.connect.plugins.dynamiclookup.server.dao.support.JsonFieldIndexDefinition;
import com.mirth.connect.plugins.dynamiclookup.server.dao.support.JsonFieldIndexDefinitionBuilder;
import com.mirth.connect.plugins.dynamiclookup.server.exception.ValueOperationException;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo.DatabaseType;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.LookupJsonCapability;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;

public class MyBatisLookupValueDao implements LookupValueDao {
    private SqlSessionManager sqlSessionManager;
    DatabaseType dbType;

    public MyBatisLookupValueDao(SqlSessionManager sqlSessionManager, DatabaseType dbType) {
        this.sqlSessionManager = sqlSessionManager;
        this.dbType = dbType;
    }

    @Override
    public LookupValue getLookupValue(String tableName, String keyValue) {
        SqlSession session = sqlSessionManager.openSession();

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("keyValue", keyValue);
            return session.selectOne("LookupValue.getLookupValue", params);
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
            return session.selectOne("LookupValue.getValue", params);
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
            return session.selectList("LookupValue.getLookupValues", params);
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
            return session.selectList("LookupValue.searchLookupValues", params);
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
            return session.selectList("LookupValue.getMatchingLookupValues", params);
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
            session.insert("LookupValue.insertValue", params);
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
    public void insertValueJson(String tableName, String keyValue, String valueData) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("keyValue", keyValue);
            params.put("valueData", valueData);

            session.insert("LookupValue.insertValueJson", params); // ← mapper JSONB
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
            session.insert("LookupValue.updateValue", params);
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
    public void updateValueJson(String tableName, String keyValue, String valueData) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("keyValue", keyValue);
            params.put("valueData", valueData);
            session.insert("LookupValue.updateValueJson", params);
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
            session.delete("LookupValue.deleteValue", params);
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
            session.delete("LookupValue.deleteAllValues", params);
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
                    int cnt = session.insert("LookupValue.insertValue", params);
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
    public int importValuesJson(String tableName, Map<String, String> values) {
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
                    int cnt = session.insert("LookupValue.insertValueJson", params);
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
            return session.selectOne("LookupValue.getValueCount", params);
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
            return session.selectOne("LookupValue.searchLookupValuesCount", params);
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

            affectedRows = session.insert("LookupValue.putIfAbsent", params);

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

    @Override
    public boolean putIfAbsentJson(String tableName, String keyValue, String valueData) {
        int affectedRows = 0;
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("keyValue", keyValue);
            params.put("valueData", valueData);

            affectedRows = session.insert("LookupValue.putIfAbsentJson", params);

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

    @Override
    public boolean compareAndSwap(String tableName, String keyValue, String expectedValue, String newValue) {
        int affectedRows = 0;
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("keyValue", keyValue);
            params.put("expectedValue", expectedValue);
            params.put("newValue", newValue);
            affectedRows = session.insert("LookupValue.compareAndSwap", params);

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

    @Override
    public boolean compareAndSwapJson(String tableName, String keyValue, String expectedValue, String newValue) {
        int affectedRows = 0;
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("keyValue", keyValue);
            params.put("expectedValue", expectedValue);
            params.put("newValue", newValue);
            affectedRows = session.insert("LookupValue.compareAndSwapJson", params);

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

    @Override
    public boolean updateValueByDelta(String tableName, String keyValue, Long delta) {
        if (dbType == DatabaseType.DERBY) {
            throw new ValueOperationException("Atomic delta update is not supported on Derby (CLOB field).");
        }

        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("keyValue", keyValue);
            params.put("delta", delta);

            int update = session.update("LookupValue.updateValueByDelta", params);

            session.commit();
            commitSuccess = true;

            return update > 0;
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
    public void createJsonGinIndex(String tableName) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;
        String indexName = buildGinIndexName(tableName);

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("indexName", indexName);

            session.update("LookupValue.createJsonGinIndex", params);
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
    public void dropJsonGinIndex(String tableName) {
        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;
        String indexName = buildGinIndexName(tableName);

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("indexName", indexName);

            session.update("LookupValue.dropJsonGinIndex", params);
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
    public void createJsonFieldIndexes(String tableName, Set<String> fields) {
        List<JsonFieldIndexDefinition> fieldIndexes = JsonFieldIndexDefinitionBuilder.build(LookupJsonCapability.getInstance(), tableName, fields);

        if (fieldIndexes.isEmpty()) {
            return;
        }

        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("fieldIndexes", fieldIndexes);

            session.update("LookupValue.createJsonFieldIndexes", params);
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
    public void dropJsonFieldIndexes(String tableName, Set<String> fields) {
        List<JsonFieldIndexDefinition> fieldIndexes = JsonFieldIndexDefinitionBuilder.build(LookupJsonCapability.getInstance(), tableName, fields);

        if (fieldIndexes.isEmpty()) {
            return;
        }

        SqlSession session = sqlSessionManager.openSession();
        boolean commitSuccess = false;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("fieldIndexes", fieldIndexes);

            session.update("LookupValue.dropJsonFieldIndexes", params);
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
    public List<LookupValue> searchByJsonFieldsGin(String tableName, Integer offset, Integer limit, String filterJson) {
        SqlSession session = sqlSessionManager.openSession();

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("offset", offset);
            params.put("limit", limit);
            params.put("filterJson", filterJson);
            return session.selectList("LookupValue.searchByJsonFieldsGin", params);
        } finally {
            session.close();
        }
    }

    @Override
    public List<LookupValue> searchByJsonFieldsField(String tableName, Integer offset, Integer limit, List<JsonFieldCriterion> criteria) {
        SqlSession session = sqlSessionManager.openSession();

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            params.put("offset", offset);
            params.put("limit", limit);
            params.put("criteria", criteria);
            return session.selectList("LookupValue.searchByJsonFieldsField", params);
        } finally {
            session.close();
        }
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
        return session.selectList("LookupValue.selectExistingKeys", params);
    }

    private String buildGinIndexName(String tableName) {
        // Example: dl_group_12_values → idx_dl_group_12_values_json_gin
        String sanitized = tableName.toLowerCase().replaceAll("[^a-z0-9_]+", "_");
        return "idx_" + sanitized + "_json_gin";
    }
}
