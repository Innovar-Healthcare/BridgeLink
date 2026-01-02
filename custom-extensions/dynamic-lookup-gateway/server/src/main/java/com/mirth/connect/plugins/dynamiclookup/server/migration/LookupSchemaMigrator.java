/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.migration;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import org.apache.commons.dbutils.DbUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.dynamiclookup.server.util.LookupDbUtil;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo.DatabaseType;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.LookupJsonCapability;

public class LookupSchemaMigrator {
    private final Logger logger = LogManager.getLogger(this.getClass());

    private final SqlSessionManager sqlSessionManager;

    public LookupSchemaMigrator(SqlSessionManager sqlSessionManager) {
        this.sqlSessionManager = sqlSessionManager;
    }

    public void migrate() {
        if (needUpdateV101()) {
            String path = "/sql/sqlserver/fixes/V101_convert_varchar_to_nvarchar.sql";
            String sqlScript = SqlScriptRunner.loadScript(path);

            SqlScriptRunner.runWithGo(sqlSessionManager, sqlScript);
        }

        String folder = dbFolder(LookupJsonCapability.getInstance().getDatabaseInfo().getType());
        if (needUpdateV210_AddGroupValueType()) {
            String path = "/sql/" + folder + "/fixes/V210_add_value_type_to_group_table.sql";
            String sqlScript = SqlScriptRunner.loadScript(path);

            SqlScriptRunner.runWithSemicolon(sqlSessionManager, sqlScript);
        }

        if (needUpdateV210_AddGroupStatisticsEnabled()) {
            String path = "/sql/" + folder + "/fixes/V210_add_statistics_enabled_to_group_table.sql";
            String sqlScript = SqlScriptRunner.loadScript(path);

            SqlScriptRunner.runWithSemicolon(sqlSessionManager, sqlScript);
        }

        if (needUpdateV210_AddGroupExtra()) {
            String path = "/sql/" + folder + "/fixes/V210_add_group_extra_table.sql";
            String sqlScript = SqlScriptRunner.loadScript(path);

            SqlScriptRunner.runWithSemicolon(sqlSessionManager, sqlScript);
        }
    }

    private boolean needUpdateV101() {
        try {
            DatabaseType dbType = LookupJsonCapability.getInstance().getDatabaseInfo().getType();
            if (dbType != DatabaseType.SQLSERVER) {
                return false;
            }

            SqlSession session = sqlSessionManager.openSession();
            ResultSet rs = null;
            try {
                DatabaseMetaData metaData = session.getConnection().getMetaData();

                rs = metaData.getColumns(null, null, "LOOKUP_GROUP", "NAME");
                if (!rs.next()) {
                    DbUtils.closeQuietly(rs);
                    rs = metaData.getColumns(null, null, "lookup_group", "name");
                    if (!rs.next()) {
                        return false;
                    }
                }

                String typeName = rs.getString("TYPE_NAME");
                return typeName == null || !"NVARCHAR".equalsIgnoreCase(typeName);
            } catch (Exception e) {
                logger.warn("needUpdateV101() check failed: {}", e.getMessage());
                return false;
            } finally {
                DbUtils.closeQuietly(rs);
                try {
                    session.close();
                } catch (Exception ignore) {
                }
            }

        } catch (Exception e) {
            // TODO: handle exception
        }

        return false;
    }

    private boolean needUpdateV210_AddGroupValueType() {
        SqlSession session = null;

        try {
            session = sqlSessionManager.openSession();

            return !LookupDbUtil.columnExists(session.getConnection(), "LOOKUP_GROUP", "VALUE_TYPE");
        } catch (Exception e) {
            // TODO: handle exception
            logger.warn("needUpdateV210_AddGroupValueType() check failed: {}", e.getMessage());
            return false;
        } finally {

            try {
                if (session != null) {
                    session.close();
                }
            } catch (Exception ignore) {
            }
        }
    }

    private boolean needUpdateV210_AddGroupStatisticsEnabled() {
        SqlSession session = null;

        try {
            session = sqlSessionManager.openSession();

            return !LookupDbUtil.columnExists(session.getConnection(), "LOOKUP_GROUP", "STATISTICS_ENABLED");
        } catch (Exception e) {
            // TODO: handle exception
            logger.warn("needUpdateV210_AddGroupStatisticsEnabled() check failed: {}", e.getMessage());
            return false;
        } finally {

            try {
                if (session != null) {
                    session.close();
                }
            } catch (Exception ignore) {
            }
        }
    }

    private boolean needUpdateV210_AddGroupExtra() {
        SqlSession session = null;

        try {
            if (!LookupJsonCapability.getInstance().isJsonSupported()) {
                return false;
            }

            session = sqlSessionManager.openSession();
            return !LookupDbUtil.tableExists(session.getConnection(), "LOOKUP_GROUP_EXTRA");
        } catch (Exception e) {
            logger.warn("needUpdateV210_AddGroupExtra() check failed: {}", e.getMessage());
            return false;
        } finally {
            try {
                if (session != null) {
                    session.close();
                }
            } catch (Exception ignore) {
            }
        }
    }

    private static String dbFolder(DatabaseType type) {
        switch (type) {
        case POSTGRESQL:
            return "postgres";
        case MYSQL:
            return "mysql";
        case SQLSERVER:
            return "sqlserver";
        case ORACLE:
            return "oracle";
        case DERBY:
            return "derby";
        default:
            return "derby";
        }
    }
}
