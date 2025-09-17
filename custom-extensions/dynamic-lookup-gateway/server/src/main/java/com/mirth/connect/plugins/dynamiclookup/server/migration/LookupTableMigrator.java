/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.migration;

/**
 * Interface for Lookup Table Management System database migrators
 * Defines the contract for database schema migration and updates
 * 
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 */
public interface LookupTableMigrator {
    
    /**
     * Execute the migration for this specific version
     * @throws MigrationException if migration fails
     */
    void migrate() throws MigrationException;
    
    /**
     * Get the target version this migrator handles
     * @return version string (e.g., "4.6.0")
     */
    String getTargetVersion();
    
    /**
     * Check if this migration should be applied given the current version
     * @param currentVersion the current schema version, null if no schema exists
     * @return true if migration should be applied
     */
    boolean shouldApply(String currentVersion);
    
    /**
     * Custom exception for migration errors
     */
    class MigrationException extends Exception {
        public MigrationException(String message) {
            super(message);
        }
        
        public MigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
