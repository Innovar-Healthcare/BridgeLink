/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.plugin;

import static com.mirth.connect.plugins.dynamiclookup.shared.interfaces.LookupTableServletInterface.PERMISSION_ACCESS;

import com.mirth.connect.client.core.api.util.OperationUtil;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.ServicePlugin;
import com.mirth.connect.plugins.dynamiclookup.server.cache.LookupCacheManager;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupAuditDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupStatisticsDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupValueDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.impl.MyBatisLookupAuditDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.impl.MyBatisLookupGroupDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.impl.MyBatisLookupStatisticsDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.impl.MyBatisLookupValueDao;
import com.mirth.connect.plugins.dynamiclookup.server.migration.LookupTableMigrationManager;
import com.mirth.connect.plugins.dynamiclookup.server.service.LookupService;
import com.mirth.connect.plugins.dynamiclookup.server.userutil.LookupHelper;
import com.mirth.connect.plugins.dynamiclookup.server.util.SqlSessionManagerProvider;
import com.mirth.connect.plugins.dynamiclookup.shared.interfaces.LookupTableServletInterface;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;
import com.mirth.connect.server.util.DatabaseUtil;

import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-05-13 10:25 AM
 */

public class LookupTableServicePlugin implements ServicePlugin {
    private final Logger logger = LogManager.getLogger(this.getClass());
    private final LookupService lookupService = LookupService.getInstance();
    private LookupCacheManager cacheManager;

    @Override
    public void init(Properties properties) {
        logger.info("Initializing Lookup Table Management System plugin...");
        try {
            // Initialize database if needed
            initializeDatabase();

            // Create DAO instances
            SqlSessionManager sqlSessionManager = getSqlSessionManager();
            LookupGroupDao groupDao = new MyBatisLookupGroupDao(sqlSessionManager);
            LookupValueDao valueDao = new MyBatisLookupValueDao(sqlSessionManager);
            LookupAuditDao auditDao = new MyBatisLookupAuditDao(sqlSessionManager);
            LookupStatisticsDao statisticsDao = new MyBatisLookupStatisticsDao(sqlSessionManager);

            // Create cache manager
            cacheManager = new LookupCacheManager(groupDao);
            // Init lookup service
            lookupService.init(groupDao, valueDao, auditDao, statisticsDao, cacheManager);
            // Initialize helper methods for transformers
            LookupHelper.initialize(lookupService);
            // Register transformer functions
            registerScriptingFunctions();
            logger.info("Lookup Table Management System plugin initialized successfully");
        } catch (Exception e) {
            logger.error("Error initializing Lookup Table Management System plugin", e);
            throw new RuntimeException("Failed to initialize plugin: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Properties properties) {

    }

    @Override
    public Properties getDefaultProperties() {
        return new Properties();
    }

    @Override
    public ExtensionPermission[] getExtensionPermissions() {
        ExtensionPermission viewPermission = new ExtensionPermission(
                "Lookup Table Management System",
                PERMISSION_ACCESS,
                "Allows to accessing Lookup Table",
                OperationUtil.getOperationNamesForPermission(PERMISSION_ACCESS, LookupTableServletInterface.class),
                new String[]{}
        );

        return new ExtensionPermission[]{viewPermission};
    }

    @Override
    public String getPluginPointName() {
        return "Lookup Table Management System";
    }

    @Override
    public void start() {
        logger.info("Starting Lookup Table Management System plugin...");
        try {
            // Preload lookup tables for better performance
            preloadLookupTables();
            logger.info("Lookup Table Management System plugin started successfully");
        } catch (Exception e) {
            logger.error("Error starting Lookup Table Management System plugin", e);
        }
    }

    @Override
    public void stop() {
        logger.info("Stopping Lookup Table Management System plugin...");
        try {
            // Clear all caches
            if (cacheManager != null) {
                cacheManager.clearAllCaches();
            }
            logger.info("Lookup Table Management System plugin stopped successfully");
        } catch (Exception e) {
            logger.error("Error stopping Lookup Table Management System plugin", e);
        }
    }

    /**
     * Initialize database schema if needed
     */
    private void initializeDatabase() throws Exception {
        logger.info("Initializing database schema...");
        
        // Use the migration manager to handle both fresh installs and migrations
        LookupTableMigrationManager migrationManager = new LookupTableMigrationManager();
        
        SqlSessionManager sqlSessionManager = getSqlSessionManager();
        SqlSession session = sqlSessionManager.openSession();
        try {
            // Check if tables already exist
            if (!DatabaseUtil.tableExists(session.getConnection(), "LOOKUP_GROUP")) {
                // Fresh install - create tables with latest schema
                DatabaseType dbType = determineDatabaseType(sqlSessionManager);
                String migrationScript = getMigrationScript(dbType);
                executeSqlScript(session, migrationScript);
                logger.info("Database schema initialized successfully");
            } else {
                // Tables exist - run migration manager to handle any needed updates
                logger.info("Tables exist, checking for schema migrations...");
                migrationManager.migrate();
                logger.info("Database schema migration check completed");
            }
        } finally {
            session.close();
        }
    }

    /**
     * Determine database type from connection
     */
    private DatabaseType determineDatabaseType(SqlSessionManager sqlSessionManager) throws SQLException {
        SqlSession session = sqlSessionManager.openSession();

        try {
            Connection conn = session.getConnection();
            String productName = conn.getMetaData().getDatabaseProductName().toLowerCase();
            if (productName.contains("postgresql")) {
                return DatabaseType.POSTGRESQL;
            } else if (productName.contains("mysql")) {
                return DatabaseType.MYSQL;
            } else if (productName.contains("microsoft") || productName.contains("sql server")) {
                return DatabaseType.SQLSERVER;
            } else if (productName.contains("oracle")) {
                return DatabaseType.ORACLE;
            } else {
                return DatabaseType.DERBY; // Default/fallback is Derby (Mirth's embedded DB)
            }
        } finally {
            session.close();
        }
    }

    /**
     * Get migration script based on database type
     */
    private String getMigrationScript(DatabaseType dbType) {
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
        try (InputStream is = getClass().getResourceAsStream(scriptPath)) {
            if (is == null) {
                throw new RuntimeException("Failed to load migration script: script not found");
            }

            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load migration script: " + scriptPath, e);
        }
    }

    /**
     * Execute SQL script
     */
    private void executeSqlScript(SqlSession session, String script) {
        String[] statements = script.split(";");

        try {
            Statement statement = session.getConnection().createStatement();

            for (String statementString : statements) {
                statementString = statementString.trim();
                if (!statementString.isEmpty()) {
                    statement.execute(statementString);
                }
            }
            session.commit();
        } catch (Exception e) {
            session.rollback();
            throw new RuntimeException("Failed to execute SQL script: " + e.getMessage(), e);
        }
    }

    /**
     * Get SqlSessionManager from Mirth's context
     */
    private SqlSessionManager getSqlSessionManager() {
        return SqlSessionManagerProvider.get();
    }

    /**
     * Register JavaScript functions for transformers
     */
    private void registerScriptingFunctions() {
    }

    /**
     * Preload frequently used lookup tables
     */
    private void preloadLookupTables() {
        logger.info("Preloading lookup tables...");
        try {
            List<LookupGroup> groups = lookupService.getAllGroups();
            int count = 0;
            for (LookupGroup group : groups) {
                if (group.getCacheSize() > 0) {
                    // Load values into cache
                    int valueCount = preloadGroupValues(group);
                    if (valueCount > 0) {
                        count++;
                        logger.info("Preloaded {} values for group: {} (ID: {})",
                                valueCount, group.getName(), group.getId());
                    }
                }
            }
            logger.info("Completed preloading {} lookup groups", count);
        } catch (Exception e) {
            logger.error("Error preloading lookup tables", e);
        }
    }

    /**
     * Preload values for a specific group
     */
    private int preloadGroupValues(LookupGroup group) {
        try {
            int limit = group.getCacheSize() * 2;
            List<LookupValue> values = lookupService.searchLookupValues(group.getId(), 0, limit, null);

            // Skip if the group is empty or too large for caching
            if (values.isEmpty()) {
                return 0;
            }
            // Load values into cache
            for (LookupValue value : values) {
                cacheManager.putValue(group.getId(), value.getKeyValue(), value.getValueData(), value.getUpdatedDate());
            }
            return values.size();
        } catch (Exception e) {
            logger.warn("Failed to preload values for group: {} (ID: {}): {}",
                    group.getName(), group.getId(), e.getMessage());
            return 0;
        }
    }

    /**
     * Enum for supported database types
     */
    private enum DatabaseType {
        DERBY, POSTGRESQL, MYSQL, SQLSERVER, ORACLE
    }

}
