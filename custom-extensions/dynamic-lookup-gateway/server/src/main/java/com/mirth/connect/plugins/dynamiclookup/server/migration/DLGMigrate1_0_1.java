/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.migration;

import com.mirth.connect.server.util.DatabaseUtil;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Dynamic Lookup Gateway Migration from version 1.0.0 to 1.0.1
 * Handles schema updates for LOOKUP_GROUP and LOOKUP_AUDIT tables
 * 
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 */
public class DLGMigrate1_0_1 extends BaseLookupTableMigrator {
    private static final Logger logger = LogManager.getLogger(DLGMigrate1_0_1.class);
    
    private static final String TARGET_VERSION = "1.0.1";
    
    public DLGMigrate1_0_1() {
        super();
    }
    
    public DLGMigrate1_0_1(SqlSessionManager sqlSessionManager) {
        super(sqlSessionManager);
    }
    
    @Override
    public void migrate() throws MigrationException {
        logger.info("Starting Dynamic Lookup Gateway migration to version 1.0.1...");
        
        SqlSession session = sqlSessionManager.openSession();
        try {
            // Determine database type
            DatabaseType dbType = determineDatabaseType(session);
            logger.info("Detected database type: {}", dbType);
            
            // Check if this migration should proceed
            if (!shouldApplyMigration(session, dbType)) {
                logger.info("Migration to version 1.0.1 not needed for current database configuration");
                return;
            }
            
            // Load and execute migration script
            String migrationScript = loadMigrationScript(dbType, TARGET_VERSION);
            executeSqlScript(session, migrationScript);
            
            // Verify migration was successful
            if (verifyMigration(session)) {
                logger.info("Successfully migrated Dynamic Lookup Gateway to version 1.0.1");
            } else {
                throw new MigrationException("Migration verification failed for version 1.0.1");
            }
            
        } catch (Exception e) {
            logger.error("Failed to migrate to version 1.0.1", e);
            throw new MigrationException("Failed to migrate to version 1.0.1: " + e.getMessage(), e);
        } finally {
            session.close();
        }
    }
    
    @Override
    public String getTargetVersion() {
        return TARGET_VERSION;
    }
    
    @Override
    public boolean shouldApply(String currentVersion) {
        if (currentVersion == null) {
            return false; // Fresh install, no migration needed (tables will be created with latest schema)
        }
        
        // Apply if current version is 1.0.0
        return "1.0.0".equals(currentVersion);
    }
    
    /**
     * Check if the migration should be applied based on database type and current schema state
     */
    private boolean shouldApplyMigration(SqlSession session, DatabaseType dbType) throws SQLException {
        // Only apply to SQL Server databases
        if (dbType != DatabaseType.SQLSERVER) {
            logger.info("Migration 1.0.1 is only applicable to SQL Server databases. Current database type: {}", dbType);
            return false;
        }
        
        Connection conn = session.getConnection();
        
        // Check if LOOKUP_GROUP table exists
        if (!DatabaseUtil.tableExists(conn, "LOOKUP_GROUP")) {
            logger.info("LOOKUP_GROUP table does not exist, migration not applicable");
            return false;
        }
        
        // Check if LOOKUP_AUDIT table exists
        if (!DatabaseUtil.tableExists(conn, "LOOKUP_AUDIT")) {
            logger.info("LOOKUP_AUDIT table does not exist, migration not applicable");
            return false;
        }
        
        boolean needsMigration = false;
        
        // Check LOOKUP_GROUP columns that should be NVARCHAR
        needsMigration |= checkColumnNeedsMigration(conn, "LOOKUP_GROUP", "NAME");
        needsMigration |= checkColumnNeedsMigration(conn, "LOOKUP_GROUP", "DESCRIPTION");
        needsMigration |= checkColumnNeedsMigration(conn, "LOOKUP_GROUP", "VERSION");
        needsMigration |= checkColumnNeedsMigration(conn, "LOOKUP_GROUP", "CACHE_POLICY");
        
        // Check LOOKUP_AUDIT columns that should be NVARCHAR
        needsMigration |= checkColumnNeedsMigration(conn, "LOOKUP_AUDIT", "TABLE_NAME");
        needsMigration |= checkColumnNeedsMigration(conn, "LOOKUP_AUDIT", "KEY_VALUE");
        needsMigration |= checkColumnNeedsMigration(conn, "LOOKUP_AUDIT", "ACTION");
        needsMigration |= checkColumnNeedsMigration(conn, "LOOKUP_AUDIT", "OLD_VALUE");
        needsMigration |= checkColumnNeedsMigration(conn, "LOOKUP_AUDIT", "NEW_VALUE");
        needsMigration |= checkColumnNeedsMigration(conn, "LOOKUP_AUDIT", "USER_ID");
        
        if (needsMigration) {
            logger.info("Found VARCHAR columns that need to be migrated to NVARCHAR, migration needed");
        } else {
            logger.info("All columns are already NVARCHAR type, migration not needed");
        }
        
        return needsMigration;
    }
    
    /**
     * Check if a specific column needs migration from VARCHAR to NVARCHAR
     */
    private boolean checkColumnNeedsMigration(Connection conn, String tableName, String columnName) throws SQLException {
        // Check if column exists
        if (!DatabaseUtil.columnExists(conn, tableName, columnName)) {
            logger.debug("Column {}.{} does not exist, will be created by migration", tableName, columnName);
            return true;
        }
        
        // Check if column is already NVARCHAR type
        boolean isNvarchar = DatabaseUtil.isColumnType(conn, tableName, columnName, "NVARCHAR");
        if (isNvarchar) {
            logger.debug("Column {}.{} is already NVARCHAR type", tableName, columnName);
            return false;
        }
        
        // Check if column is VARCHAR type (needs migration)
        boolean isVarchar = DatabaseUtil.isColumnType(conn, tableName, columnName, "VARCHAR");
        if (isVarchar) {
            logger.debug("Column {}.{} is VARCHAR type, needs migration to NVARCHAR", tableName, columnName);
            return true;
        }
        
        // Column exists but is neither VARCHAR nor NVARCHAR - log the actual type
        String actualType = DatabaseUtil.getColumnType(conn, tableName, columnName);
        logger.debug("Column {}.{} is {} type, needs migration to NVARCHAR", tableName, columnName, actualType);
        return true;
    }
    
    /**
     * Verify that the migration was successful by checking for expected schema changes
     */
    private boolean verifyMigration(SqlSession session) {
        try {
            Connection conn = session.getConnection();
            
            // Check that LOOKUP_GROUP table exists with expected structure
            if (!DatabaseUtil.tableExists(conn, "LOOKUP_GROUP")) {
                logger.error("LOOKUP_GROUP table does not exist after migration");
                return false;
            }
            
            // Check that CACHE_POLICY column exists
            if (!DatabaseUtil.columnExists(conn, "LOOKUP_GROUP", "CACHE_POLICY")) {
                logger.error("CACHE_POLICY column does not exist in LOOKUP_GROUP table after migration");
                return false;
            }

            // Check that LOOKUP_AUDIT table exists
            if (!DatabaseUtil.tableExists(conn, "LOOKUP_AUDIT")) {
                logger.error("LOOKUP_AUDIT table does not exist after migration");
                return false;
            }
            
            // For SQL Server, verify that all columns are now NVARCHAR type
            DatabaseType dbType = determineDatabaseType(session);
            if (dbType == DatabaseType.SQLSERVER) {
                // Verify LOOKUP_GROUP columns
                String[] lookupGroupColumns = {"NAME", "DESCRIPTION", "VERSION", "CACHE_POLICY"};
                for (String columnName : lookupGroupColumns) {
                    if (!DatabaseUtil.columnExists(conn, "LOOKUP_GROUP", columnName)) {
                        logger.error("Column LOOKUP_GROUP.{} does not exist after migration", columnName);
                        return false;
                    }
                    
                    boolean isNvarchar = DatabaseUtil.isColumnType(conn, "LOOKUP_GROUP", columnName, "NVARCHAR");
                    if (!isNvarchar) {
                        String actualType = DatabaseUtil.getColumnType(conn, "LOOKUP_GROUP", columnName);
                        logger.error("LOOKUP_GROUP.{} column should be NVARCHAR type but is {} after migration", columnName, actualType);
                        return false;
                    }
                    logger.debug("Verified LOOKUP_GROUP.{} column is NVARCHAR type", columnName);
                }
                
                // Verify LOOKUP_AUDIT columns
                String[] lookupAuditColumns = {"TABLE_NAME", "KEY_VALUE", "ACTION", "OLD_VALUE", "NEW_VALUE", "USER_ID"};
                for (String columnName : lookupAuditColumns) {
                    if (!DatabaseUtil.columnExists(conn, "LOOKUP_AUDIT", columnName)) {
                        logger.error("Column LOOKUP_AUDIT.{} does not exist after migration", columnName);
                        return false;
                    }
                    
                    boolean isNvarchar = DatabaseUtil.isColumnType(conn, "LOOKUP_AUDIT", columnName, "NVARCHAR");
                    if (!isNvarchar) {
                        String actualType = DatabaseUtil.getColumnType(conn, "LOOKUP_AUDIT", columnName);
                        logger.error("LOOKUP_AUDIT.{} column should be NVARCHAR type but is {} after migration", columnName, actualType);
                        return false;
                    }
                    logger.debug("Verified LOOKUP_AUDIT.{} column is NVARCHAR type", columnName);
                }
                
                logger.debug("All columns verified as NVARCHAR type");
            }
            
            logger.debug("Migration verification completed successfully");
            return true;
            
        } catch (SQLException e) {
            logger.error("Error during migration verification", e);
            return false;
        }
    }
    
    /**
     * Get current schema version by analyzing the database structure
     */
    public String getCurrentVersion(SqlSession session) throws SQLException {
        Connection conn = session.getConnection();
        
        // Check if tables exist at all
        if (!DatabaseUtil.tableExists(conn, "LOOKUP_GROUP")) {
            return null; // No schema exists
        }
        
        // Check for version-specific features to determine current version
        if (hasVersion101Features(conn)) {
            return TARGET_VERSION;
        }
        
        // Default to version 1.0.0 if tables exist but no 1.0.1-specific features found
        return "1.0.0";
    }

    /**
     * Check if version 1.0.1 specific features exist
     */
    private boolean hasVersion101Features(Connection conn) throws SQLException {
        try {
            // Check if both tables exist
            if (!DatabaseUtil.tableExists(conn, "LOOKUP_GROUP") || !DatabaseUtil.tableExists(conn, "LOOKUP_AUDIT")) {
                return false;
            }
            
            // Check LOOKUP_GROUP columns exist and are NVARCHAR type for SQL Server
            String[] lookupGroupColumns = {"NAME", "DESCRIPTION", "VERSION", "CACHE_POLICY"};
            for (String columnName : lookupGroupColumns) {
                if (!DatabaseUtil.columnExists(conn, "LOOKUP_GROUP", columnName)) {
                    return false;
                }
                
                // For SQL Server, verify column is NVARCHAR type (indicates 1.0.1 migration)
                if (DatabaseUtil.isColumnType(conn, "LOOKUP_GROUP", columnName, "NVARCHAR")) {
                    continue; // NVARCHAR found, good sign of 1.0.1
                } else if (DatabaseUtil.isColumnType(conn, "LOOKUP_GROUP", columnName, "VARCHAR")) {
                    return false; // Still VARCHAR, migration not applied
                } else {
                    // Neither NVARCHAR nor VARCHAR - could be other database type, continue checking
                    continue;
                }
            }
            
            // Check LOOKUP_AUDIT columns exist and are NVARCHAR type for SQL Server
            String[] lookupAuditColumns = {"TABLE_NAME", "KEY_VALUE", "ACTION", "OLD_VALUE", "NEW_VALUE", "USER_ID"};
            for (String columnName : lookupAuditColumns) {
                if (!DatabaseUtil.columnExists(conn, "LOOKUP_AUDIT", columnName)) {
                    return false;
                }
                
                // For SQL Server, verify column is NVARCHAR type (indicates 1.0.1 migration)
                if (DatabaseUtil.isColumnType(conn, "LOOKUP_AUDIT", columnName, "NVARCHAR")) {
                    continue; // NVARCHAR found, good sign of 1.0.1
                } else if (DatabaseUtil.isColumnType(conn, "LOOKUP_AUDIT", columnName, "VARCHAR")) {
                    return false; // Still VARCHAR, migration not applied
                } else {
                    // Neither NVARCHAR nor VARCHAR - could be other database type, continue checking
                    continue;
                }
            }
            
            // All required columns exist, indicating 1.0.1 migration has been applied
            return true;
            
        } catch (Exception e) {
            logger.debug("Error checking for 1.0.1 features", e);
            return false;
        }
    }
}
