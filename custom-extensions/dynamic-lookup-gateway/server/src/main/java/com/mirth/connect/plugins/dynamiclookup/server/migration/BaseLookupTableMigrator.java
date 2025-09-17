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

/**
 * Base abstract class for Lookup Table migrators
 * Contains common functionality for database migrations
 * 
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 */
public abstract class BaseLookupTableMigrator implements LookupTableMigrator {
    private static final Logger logger = LogManager.getLogger(BaseLookupTableMigrator.class);
    
    protected final SqlSessionManager sqlSessionManager;
    
    public BaseLookupTableMigrator() {
        this.sqlSessionManager = SqlSessionManagerProvider.get();
    }
    
    public BaseLookupTableMigrator(SqlSessionManager sqlSessionManager) {
        this.sqlSessionManager = sqlSessionManager;
    }
    
    /**
     * Determine database type from connection
     */
    protected DatabaseType determineDatabaseType(SqlSession session) throws SQLException {
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
            return DatabaseType.DERBY; // Default/fallback
        }
    }
    
    /**
     * Load migration script based on database type and version
     */
    protected String loadMigrationScript(DatabaseType dbType, String version) throws IOException {
        String scriptPath = getMigrationScriptPath(dbType, version);
        
        try (InputStream is = getClass().getResourceAsStream(scriptPath)) {
            if (is == null) {
                throw new IOException("Migration script not found: " + scriptPath);
            }
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }
    
    /**
     * Get the migration script path for the given database type and version
     */
    protected String getMigrationScriptPath(DatabaseType dbType, String version) {
        String dbPath;
        switch (dbType) {
            case POSTGRESQL:
                dbPath = "postgres";
                break;
            case MYSQL:
                dbPath = "mysql";
                break;
            case SQLSERVER:
                dbPath = "sqlserver";
                break;
            case ORACLE:
                dbPath = "oracle";
                break;
            case DERBY:
            default:
                dbPath = "derby";
                break;
        }
        
        return String.format("/sql/%s/create_lookup_tables_%s.sql", dbPath, version);
    }
    
    /**
     * Execute SQL script with proper transaction handling
     */
    protected void executeSqlScript(SqlSession session, String script) throws SQLException {
        logger.debug("Executing migration script...");
        
        Connection conn = session.getConnection();
        DatabaseType dbType = determineDatabaseType(session);
        
        try {
            Statement statement = conn.createStatement();
            
            // For SQL Server, execute as batches separated by GO statements
            // For other databases, execute as individual statements separated by semicolons
            if (dbType == DatabaseType.SQLSERVER) {
                executeSqlServerScript(statement, script);
            } else {
                executeGenericScript(statement, script);
            }
            
            session.commit();
            logger.debug("Migration script executed successfully");
        } catch (SQLException e) {
            logger.error("Error executing migration script", e);
            session.rollback();
            throw e;
        }
    }
    
    /**
     * Execute SQL Server script as batches (handles DECLARE variables properly)
     */
    private void executeSqlServerScript(Statement statement, String script) throws SQLException {
        // Split by GO statements first (SQL Server batch separator)
        String[] batches = script.split("(?i)\\s*GO\\s*");
        
        for (String batch : batches) {
            batch = batch.trim();
            
            // Skip empty batches
            if (batch.isEmpty()) {
                continue;
            }
            
            // Remove comments and empty lines for cleaner logging
            String cleanBatch = removeCommentsAndEmptyLines(batch);
            if (cleanBatch.isEmpty()) {
                continue;
            }
            
            logger.debug("Executing SQL Server batch: {}", 
                cleanBatch.length() > 200 ? cleanBatch.substring(0, 200) + "..." : cleanBatch);
            
            // Execute entire batch at once to preserve variable scope
            statement.execute(batch);
        }
    }
    
    /**
     * Execute generic script by splitting into individual statements
     */
    private void executeGenericScript(Statement statement, String script) throws SQLException {
        // Split script into individual statements
        String[] statements = script.split(";");
        
        for (String statementString : statements) {
            statementString = statementString.trim();
            
            // Skip empty statements and comments
            if (statementString.isEmpty() || statementString.startsWith("--")) {
                continue;
            }
            
            logger.debug("Executing SQL: {}", statementString);
            statement.execute(statementString);
        }
    }
    
    /**
     * Remove SQL comments and empty lines for cleaner logging
     */
    private String removeCommentsAndEmptyLines(String sql) {
        String[] lines = sql.split("\r?\n");
        StringBuilder clean = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            // Skip empty lines and comment-only lines
            if (!line.isEmpty() && !line.startsWith("--")) {
                if (clean.length() > 0) clean.append(" ");
                clean.append(line);
            }
        }
        
        return clean.toString();
    }
    
    /**
     * Enum for supported database types
     */
    protected enum DatabaseType {
        DERBY, POSTGRESQL, MYSQL, SQLSERVER, ORACLE
    }
}
