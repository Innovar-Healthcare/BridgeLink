/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.migration;

import com.mirth.connect.plugins.dynamiclookup.server.util.SqlSessionManagerProvider;
import com.mirth.connect.server.util.DatabaseUtil;
import com.mirth.connect.plugins.dynamiclookup.server.migration.LookupTableMigrator.MigrationException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Migration manager for Lookup Table Management System
 * Handles discovery and execution of available migrations
 * 
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 */
public class LookupTableMigrationManager {
    private static final Logger logger = LogManager.getLogger(LookupTableMigrationManager.class);
    
    private final SqlSessionManager sqlSessionManager;
    private final List<LookupTableMigrator> availableMigrators;
    
    public LookupTableMigrationManager() {
        this.sqlSessionManager = SqlSessionManagerProvider.get();
        this.availableMigrators = new ArrayList<>();
        initializeMigrators();
    }
    
    public LookupTableMigrationManager(SqlSessionManager sqlSessionManager) {
        this.sqlSessionManager = sqlSessionManager;
        this.availableMigrators = new ArrayList<>();
        initializeMigrators();
    }
    
    /**
     * Initialize available migrators
     */
    private void initializeMigrators() {
        // Add available migrators in order
        availableMigrators.add(new DLGMigrate1_0_1(sqlSessionManager));
        
        // Future migrators can be added here:
        // availableMigrators.add(new DLGMigrate1_0_2(sqlSessionManager));
        // availableMigrators.add(new DLGMigrate1_1_0(sqlSessionManager));
    }
    
    /**
     * Run all necessary migrations
     */
    public void migrate() throws MigrationException {
        logger.info("Starting Lookup Table Management System database migration...");
        
        try {
            String currentVersion = getCurrentSchemaVersion();
            logger.info("Current schema version: {}", currentVersion != null ? currentVersion : "none");
            
            // Find and execute applicable migrations
            boolean migrationExecuted = false;
            for (LookupTableMigrator migrator : availableMigrators) {
                if (migrator.shouldApply(currentVersion)) {
                    logger.info("Applying migration to version: {}", migrator.getTargetVersion());
                    migrator.migrate();
                    migrationExecuted = true;
                    
                    // Update current version for next migration check
                    currentVersion = migrator.getTargetVersion();
                }
            }
            
            if (migrationExecuted) {
                logger.info("Lookup Table Management System database migration completed successfully");
            } else {
                logger.info("No migrations needed - schema is up to date");
            }
            
        } catch (Exception e) {
            logger.error("Error during database migration", e);
            throw new MigrationException("Failed to migrate Lookup Table Management System database: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get current schema version by analyzing the database structure
     */
    public String getCurrentSchemaVersion() throws SQLException {
        SqlSession session = sqlSessionManager.openSession();
        try {
            Connection conn = session.getConnection();
            
            // Check if tables exist at all
            if (!DatabaseUtil.tableExists(conn, "LOOKUP_GROUP")) {
                return null; // No schema exists
            }
            
            // Check against each migrator's version detection logic
            // Start from newest and work backwards
            for (int i = availableMigrators.size() - 1; i >= 0; i--) {
                LookupTableMigrator migrator = availableMigrators.get(i);
                if (migrator instanceof DLGMigrate1_0_1) {
                    DLGMigrate1_0_1 dlgMigrator = (DLGMigrate1_0_1) migrator;
                    String version = dlgMigrator.getCurrentVersion(session);
                    if (version != null && version.equals(dlgMigrator.getTargetVersion())) {
                        return version;
                    }
                }
            }
            
            // Default to older version if tables exist but no version-specific features found
            return "1.0.0";
            
        } finally {
            session.close();
        }
    }
    
    /**
     * Get list of available migrators
     */
    public List<LookupTableMigrator> getAvailableMigrators() {
        return new ArrayList<>(availableMigrators);
    }
    
    /**
     * Check if any migrations are pending
     */
    public boolean hasPendingMigrations() throws SQLException {
        String currentVersion = getCurrentSchemaVersion();
        
        for (LookupTableMigrator migrator : availableMigrators) {
            if (migrator.shouldApply(currentVersion)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get information about pending migrations
     */
    public List<String> getPendingMigrations() throws SQLException {
        String currentVersion = getCurrentSchemaVersion();
        List<String> pendingVersions = new ArrayList<>();
        
        for (LookupTableMigrator migrator : availableMigrators) {
            if (migrator.shouldApply(currentVersion)) {
                pendingVersions.add(migrator.getTargetVersion());
            }
        }
        
        return pendingVersions;
    }
}
