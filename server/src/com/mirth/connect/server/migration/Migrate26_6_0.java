/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.model.util.MigrationException;

public class Migrate26_6_0 extends Migrator {

    private static final Logger logger = LogManager.getLogger(Migrate26_6_0.class);

    @Override
    public void migrate() throws MigrationException {
        // Only applies to MySQL
        if (!"mysql".equals(getDatabaseType())) {
            return;
        }

        try {
            Connection conn = getConnection();

            // Only run on case-sensitive MySQL (lower_case_table_names=0, Linux default).
            // lower_case_table_names=1 (Windows) and =2 (macOS) are case-insensitive — skip.
            int lowerCaseTableNames = getLowerCaseTableNames(conn);
            if (lowerCaseTableNames != 0) {
                logger.debug("Skipping IRT-953 table rename migration: lower_case_table_names=" + lowerCaseTableNames);
                return;
            }

            // Check if lowercase tables exist. On case-sensitive MySQL, information_schema
            // TABLES uses exact-case matching, so this only finds 'd_channels' (not 'D_CHANNELS').
            if (!lowercaseTablesExist(conn)) {
                logger.debug("Skipping IRT-953 table rename migration: no lowercase d_channels table found (tables are already uppercase or not yet created).");
                return;
            }

            logger.info("IRT-953: Renaming lowercase Donkey tables to uppercase on case-sensitive MySQL...");
            int renamed = renameTables(conn);
            logger.info("IRT-953: Renamed " + renamed + " table(s) to uppercase.");

        } catch (Exception e) {
            throw new MigrationException("IRT-953 migration failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void migrateSerializedData() throws MigrationException {
        // No serialized data changes
    }

    private int getLowerCaseTableNames(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT @@lower_case_table_names")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return -1;
        }
    }

    private boolean lowercaseTablesExist(Connection conn) throws Exception {
        // Detects both the initial state (lowercase d_channels present) and a partial-rename state
        // where a prior run renamed d_channels but failed before finishing the per-channel tables.
        String sql = "SELECT COUNT(*) FROM information_schema.TABLES " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND (BINARY TABLE_NAME = 'd_channels' OR BINARY TABLE_NAME REGEXP '^d_m')";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private int renameTables(Connection conn) throws Exception {
        List<String> clauses = new ArrayList<>();

        // Only include d_channels if it is still lowercase (a prior partial run may have renamed it already).
        String checkChannelsSql = "SELECT COUNT(*) FROM information_schema.TABLES " +
                                  "WHERE TABLE_SCHEMA = DATABASE() AND BINARY TABLE_NAME = 'd_channels'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkChannelsSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                clauses.add("`d_channels` TO `D_CHANNELS`");
            }
        }

        // Discover all lowercase d_m* per-channel tables (D_M*, D_MM*, D_MC*, D_MCM*, D_MA*, D_MS*, D_MSQ*).
        String discoverSql = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                             "WHERE TABLE_SCHEMA = DATABASE() AND BINARY TABLE_NAME REGEXP '^d_m'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(discoverSql)) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                clauses.add("`" + tableName + "` TO `" + tableName.toUpperCase() + "`");
            }
        }

        if (clauses.isEmpty()) {
            return 0;
        }

        String renameSql = "RENAME TABLE " + String.join(", ", clauses);
        logger.debug("IRT-953: Executing atomic rename: " + renameSql);

        try (Statement stmt = conn.createStatement()) {
            // Disable FK checks for this session to avoid constraint ordering issues during rename.
            // SET FOREIGN_KEY_CHECKS is session-scoped — other connections are unaffected.
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                stmt.execute(renameSql);
            } finally {
                stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }

        return clauses.size();
    }
}
