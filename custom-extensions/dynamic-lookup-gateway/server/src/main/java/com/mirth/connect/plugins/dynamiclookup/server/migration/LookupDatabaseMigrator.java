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

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.dynamiclookup.server.util.LookupDbUtil;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo.DatabaseType;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.LookupJsonCapability;

public final class LookupDatabaseMigrator {
    private final Logger logger = LogManager.getLogger(this.getClass());

    private final SqlSessionManager sqlSessionManager;

    public LookupDatabaseMigrator(SqlSessionManager sqlSessionManager) {
        this.sqlSessionManager = sqlSessionManager;
    }

    public void initializeDatabase() throws Exception {
        logger.info("Initializing database schema...");

        SqlSession checkSession = sqlSessionManager.openSession();
        boolean tablesExist = false;
        try {
            tablesExist = LookupDbUtil.tableExists(checkSession.getConnection(), "LOOKUP_GROUP");
        } finally {
            checkSession.close();
        }

        // Check if tables already exist
        if (!tablesExist) {
            // Fresh install - create tables with latest schema
            DatabaseType dbType = LookupJsonCapability.getInstance().getDatabaseInfo().getType();
            String sqlScript = SqlScriptRunner.loadScript(getCreateSchemaScriptPath(dbType));
            SqlScriptRunner.runWithSemicolon(sqlSessionManager, sqlScript);
            logger.info("Database schema initialized successfully");
        }

        // Use the migration manager to handle both fresh installs and migrations
        logger.info("Running schema migrations (including fresh install)...");
        (new LookupSchemaMigrator(sqlSessionManager)).migrate();
        logger.info("Database schema migration completed");
    }

    /**
     * Get migration script based on database type
     */
    private String getCreateSchemaScriptPath(DatabaseType dbType) {
        // Load appropriate script based on database type
        String scriptPath;
        switch (dbType) {
        case POSTGRESQL:
            scriptPath = "/sql/postgres/create_lookup_tables.sql";
            break;
        case MYSQL:
            scriptPath = "/sql/mysql/create_lookup_tables.sql";
            break;
        case SQLSERVER:
            scriptPath = "/sql/sqlserver/create_lookup_tables.sql";
            break;
        case ORACLE:
            scriptPath = "/sql/oracle/create_lookup_tables.sql";
            break;
        case DERBY:
        default:
            scriptPath = "/sql/derby/create_lookup_tables.sql";
            break;
        }

        return scriptPath;
    }
}
