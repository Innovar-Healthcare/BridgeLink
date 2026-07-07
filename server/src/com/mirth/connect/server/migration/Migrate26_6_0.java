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

    private List<String> fetchCurrentSchemaTableNames(Connection conn) throws Exception {
        String sql = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()";
        List<String> tableNames = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tableNames.add(rs.getString(1));
            }
        }
        return tableNames;
    }

    private boolean lowercaseTablesExist(Connection conn) throws Exception {
        // Detects both the initial state (lowercase d_channels present) and a partial-rename state
        // where a prior run renamed d_channels but failed before finishing the per-channel tables.
        // Java string comparison is always exact-case regardless of MySQL charset/collation.
        List<String> tableNames = fetchCurrentSchemaTableNames(conn);
        return tableNames.stream().anyMatch(name -> name.equals("d_channels") || name.startsWith("d_m"));
    }

    private int renameTables(Connection conn) throws Exception {
        List<String> clauses = new ArrayList<>();

        // Fetch all table names via a single neutral query; filter in Java to avoid
        // MySQL charset/collation incompatibilities (utf8mb3 vs utf8mb4_0900_ai_ci, etc.).
        List<String> tableNames = fetchCurrentSchemaTableNames(conn);

        // Only include d_channels if it is still lowercase (a prior partial run may have renamed it already).
        for (String name : tableNames) {
            if (name.equals("d_channels")) {
                clauses.add("`d_channels` TO `D_CHANNELS`");
                break;
            }
        }

        // Discover all lowercase d_m* per-channel tables (d_m*, d_mm*, d_mc*, d_mcm*, d_ma*, d_ms*, d_msq*).
        for (String name : tableNames) {
            if (name.startsWith("d_m")) {
                clauses.add("`" + name + "` TO `" + name.toUpperCase() + "`");
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
