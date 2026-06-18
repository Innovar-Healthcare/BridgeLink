/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: JUnit coverage improvement — Migrate26_6_0 migration logic.
 */

package com.mirth.connect.server.migration;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.Test;

/**
 * Unit tests for {@link Migrate26_6_0}.
 *
 * All JDBC interaction is mocked via Mockito. Tests exercise the three branching
 * paths in {@code migrate()}: non-MySQL skip, LCTN != 0 skip, and the rename path.
 */
public class Migrate26_6_0Test {

    // ------------------------------------------------------------------
    // Non-MySQL: migrate() returns immediately without touching Connection
    // ------------------------------------------------------------------

    @Test
    public void migrate_skipsWhenDatabaseIsDerby() throws Exception {
        Migrate26_6_0 m = new Migrate26_6_0();
        m.setDatabaseType("derby");
        // No connection set — would NPE if any JDBC call were made
        m.migrate();
        // No exception = success
    }

    @Test
    public void migrate_skipsWhenDatabaseTypeIsNull() throws Exception {
        Migrate26_6_0 m = new Migrate26_6_0();
        m.setDatabaseType(null);
        m.migrate();
    }

    @Test
    public void migrate_skipsWhenDatabaseTypeIsPostgres() throws Exception {
        Migrate26_6_0 m = new Migrate26_6_0();
        m.setDatabaseType("postgres");
        m.migrate();
    }

    // ------------------------------------------------------------------
    // MySQL + LCTN = 1: skips rename (Windows / macOS servers)
    // ------------------------------------------------------------------

    @Test
    public void migrate_skipsRenameWhenLctnIsOne() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery("SELECT @@lower_case_table_names")).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(1)).thenReturn(1); // LCTN=1 → skip

        Migrate26_6_0 m = new Migrate26_6_0();
        m.setDatabaseType("mysql");
        m.setConnection(conn);
        m.migrate();

        // Must never execute any DDL
        verify(stmt, never()).execute(anyString());
    }

    @Test
    public void migrate_skipsRenameWhenLctnIsTwo() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery("SELECT @@lower_case_table_names")).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(1)).thenReturn(2); // LCTN=2 → skip (macOS)

        Migrate26_6_0 m = new Migrate26_6_0();
        m.setDatabaseType("mysql");
        m.setConnection(conn);
        m.migrate();

        verify(stmt, never()).execute(anyString());
    }

    // ------------------------------------------------------------------
    // MySQL + LCTN = 0: no lowercase tables present → skip rename
    // ------------------------------------------------------------------

    @Test
    public void migrate_skipsRenameWhenNoLowercaseTablesExist() throws Exception {
        Connection conn = mock(Connection.class);

        // Statement for LCTN query
        Statement lctnStmt = mock(Statement.class);
        ResultSet lctnRs = mock(ResultSet.class);
        when(lctnRs.next()).thenReturn(true);
        when(lctnRs.getInt(1)).thenReturn(0); // LCTN=0

        // Statement for fetchCurrentSchemaTableNames (called by lowercaseTablesExist)
        Statement tableStmt = mock(Statement.class);
        ResultSet tableRs = mock(ResultSet.class);
        // Return one uppercase table only — no lowercase d_channels or d_m*
        when(tableRs.next()).thenReturn(true, false);
        when(tableRs.getString(1)).thenReturn("D_CHANNELS");

        // conn.createStatement() returns lctnStmt first, then tableStmt
        when(conn.createStatement())
            .thenReturn(lctnStmt)
            .thenReturn(tableStmt);
        when(lctnStmt.executeQuery("SELECT @@lower_case_table_names")).thenReturn(lctnRs);
        when(tableStmt.executeQuery(
            "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()"))
            .thenReturn(tableRs);

        Migrate26_6_0 m = new Migrate26_6_0();
        m.setDatabaseType("mysql");
        m.setConnection(conn);
        m.migrate();

        // No DDL should be executed since no lowercase tables were found
        verify(lctnStmt, never()).execute(anyString());
        verify(tableStmt, never()).execute(anyString());
    }

    // ------------------------------------------------------------------
    // MySQL + LCTN = 0: lowercase d_channels present → rename executed
    // ------------------------------------------------------------------

    @Test
    public void migrate_renamesTablesWhenDChannelsIsLowercase() throws Exception {
        Connection conn = mock(Connection.class);

        // Statement for LCTN query
        Statement lctnStmt = mock(Statement.class);
        ResultSet lctnRs = mock(ResultSet.class);
        when(lctnRs.next()).thenReturn(true);
        when(lctnRs.getInt(1)).thenReturn(0); // LCTN=0 → run migration

        // Statement for fetchCurrentSchemaTableNames — called TWICE:
        // 1st call: by lowercaseTablesExist()
        // 2nd call: by renameTables()
        Statement tableStmt1 = mock(Statement.class);
        ResultSet tableRs1 = mock(ResultSet.class);
        when(tableRs1.next()).thenReturn(true, false);
        when(tableRs1.getString(1)).thenReturn("d_channels");

        Statement tableStmt2 = mock(Statement.class);
        ResultSet tableRs2 = mock(ResultSet.class);
        when(tableRs2.next()).thenReturn(true, false);
        when(tableRs2.getString(1)).thenReturn("d_channels");

        // Statement for FK checks + RENAME TABLE
        Statement renameStmt = mock(Statement.class);

        when(conn.createStatement())
            .thenReturn(lctnStmt)
            .thenReturn(tableStmt1)
            .thenReturn(tableStmt2)
            .thenReturn(renameStmt);

        String tableQuery = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()";
        when(lctnStmt.executeQuery("SELECT @@lower_case_table_names")).thenReturn(lctnRs);
        when(tableStmt1.executeQuery(tableQuery)).thenReturn(tableRs1);
        when(tableStmt2.executeQuery(tableQuery)).thenReturn(tableRs2);

        Migrate26_6_0 m = new Migrate26_6_0();
        m.setDatabaseType("mysql");
        m.setConnection(conn);
        m.migrate();

        // FK checks must bracket the RENAME TABLE
        verify(renameStmt).execute("SET FOREIGN_KEY_CHECKS = 0");
        verify(renameStmt).execute(contains("RENAME TABLE"));
        verify(renameStmt).execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    @Test
    public void migrate_renamesPerChannelTablesWhenDmPrefixPresent() throws Exception {
        Connection conn = mock(Connection.class);

        Statement lctnStmt = mock(Statement.class);
        ResultSet lctnRs = mock(ResultSet.class);
        when(lctnRs.next()).thenReturn(true);
        when(lctnRs.getInt(1)).thenReturn(0);

        // First call: lowercaseTablesExist — returns d_m12345 (triggers migration)
        Statement tableStmt1 = mock(Statement.class);
        ResultSet tableRs1 = mock(ResultSet.class);
        when(tableRs1.next()).thenReturn(true, false);
        when(tableRs1.getString(1)).thenReturn("d_m12345");

        // Second call: renameTables — returns same table
        Statement tableStmt2 = mock(Statement.class);
        ResultSet tableRs2 = mock(ResultSet.class);
        when(tableRs2.next()).thenReturn(true, false);
        when(tableRs2.getString(1)).thenReturn("d_m12345");

        Statement renameStmt = mock(Statement.class);

        when(conn.createStatement())
            .thenReturn(lctnStmt)
            .thenReturn(tableStmt1)
            .thenReturn(tableStmt2)
            .thenReturn(renameStmt);

        String tableQuery = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()";
        when(lctnStmt.executeQuery("SELECT @@lower_case_table_names")).thenReturn(lctnRs);
        when(tableStmt1.executeQuery(tableQuery)).thenReturn(tableRs1);
        when(tableStmt2.executeQuery(tableQuery)).thenReturn(tableRs2);

        Migrate26_6_0 m = new Migrate26_6_0();
        m.setDatabaseType("mysql");
        m.setConnection(conn);
        m.migrate();

        verify(renameStmt).execute(contains("`d_m12345` TO `D_M12345`"));
    }

    // ------------------------------------------------------------------
    // migrateSerializedData: no-op (should not throw)
    // ------------------------------------------------------------------

    @Test
    public void migrateSerializedData_isNoOp() throws Exception {
        Migrate26_6_0 m = new Migrate26_6_0();
        m.migrateSerializedData(); // must not throw
    }

    // ------------------------------------------------------------------
    // getLowerCaseTableNames: returns -1 when ResultSet is empty
    // ------------------------------------------------------------------

    @Test
    public void migrate_handlesEmptyLctnResultSet() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery("SELECT @@lower_case_table_names")).thenReturn(rs);
        when(rs.next()).thenReturn(false); // empty → returns -1 → != 0 → skip

        Migrate26_6_0 m = new Migrate26_6_0();
        m.setDatabaseType("mysql");
        m.setConnection(conn);
        m.migrate(); // must not throw; -1 != 0, so skipped

        verify(stmt, never()).execute(anyString());
    }

    // ------------------------------------------------------------------
    // renameTables: clauses empty when only uppercase tables present (second
    // path — lowercaseTablesExist returned true due to partial rename state
    // but renameTables finds no matching names)
    // ------------------------------------------------------------------

    @Test
    public void migrate_returnsZeroWhenRenameClausesAreEmpty() throws Exception {
        Connection conn = mock(Connection.class);

        Statement lctnStmt = mock(Statement.class);
        ResultSet lctnRs = mock(ResultSet.class);
        when(lctnRs.next()).thenReturn(true);
        when(lctnRs.getInt(1)).thenReturn(0);

        // lowercaseTablesExist() returns true via d_m prefix
        Statement tableStmt1 = mock(Statement.class);
        ResultSet tableRs1 = mock(ResultSet.class);
        when(tableRs1.next()).thenReturn(true, false);
        when(tableRs1.getString(1)).thenReturn("d_m12345");

        // renameTables() fetches again — but this time all tables are uppercase (already renamed)
        Statement tableStmt2 = mock(Statement.class);
        ResultSet tableRs2 = mock(ResultSet.class);
        when(tableRs2.next()).thenReturn(true, false);
        when(tableRs2.getString(1)).thenReturn("D_M12345");

        when(conn.createStatement())
            .thenReturn(lctnStmt)
            .thenReturn(tableStmt1)
            .thenReturn(tableStmt2);

        String tableQuery = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()";
        when(lctnStmt.executeQuery("SELECT @@lower_case_table_names")).thenReturn(lctnRs);
        when(tableStmt1.executeQuery(tableQuery)).thenReturn(tableRs1);
        when(tableStmt2.executeQuery(tableQuery)).thenReturn(tableRs2);

        Migrate26_6_0 m = new Migrate26_6_0();
        m.setDatabaseType("mysql");
        m.setConnection(conn);
        m.migrate(); // clauses empty → returns 0, no RENAME TABLE

        // No execute() calls on any statement (no FK disable/rename)
        verify(lctnStmt, never()).execute(anyString());
        verify(tableStmt1, never()).execute(anyString());
        verify(tableStmt2, never()).execute(anyString());
    }

    // ------------------------------------------------------------------
    // countRenamedTables: returns count equal to number of clauses
    // ------------------------------------------------------------------

    @Test
    public void migrate_renamedCountMatchesClauseCount() throws Exception {
        Connection conn = mock(Connection.class);

        Statement lctnStmt = mock(Statement.class);
        ResultSet lctnRs = mock(ResultSet.class);
        when(lctnRs.next()).thenReturn(true);
        when(lctnRs.getInt(1)).thenReturn(0);

        // Two tables: d_channels + d_m99
        Statement tableStmt1 = mock(Statement.class);
        ResultSet tableRs1 = mock(ResultSet.class);
        when(tableRs1.next()).thenReturn(true, true, false);
        when(tableRs1.getString(1))
            .thenReturn("d_channels")
            .thenReturn("d_m99");

        Statement tableStmt2 = mock(Statement.class);
        ResultSet tableRs2 = mock(ResultSet.class);
        when(tableRs2.next()).thenReturn(true, true, false);
        when(tableRs2.getString(1))
            .thenReturn("d_channels")
            .thenReturn("d_m99");

        Statement renameStmt = mock(Statement.class);

        when(conn.createStatement())
            .thenReturn(lctnStmt)
            .thenReturn(tableStmt1)
            .thenReturn(tableStmt2)
            .thenReturn(renameStmt);

        String tableQuery = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()";
        when(lctnStmt.executeQuery("SELECT @@lower_case_table_names")).thenReturn(lctnRs);
        when(tableStmt1.executeQuery(tableQuery)).thenReturn(tableRs1);
        when(tableStmt2.executeQuery(tableQuery)).thenReturn(tableRs2);

        Migrate26_6_0 m = new Migrate26_6_0();
        m.setDatabaseType("mysql");
        m.setConnection(conn);
        m.migrate();

        // Both d_channels and d_m99 appear in the RENAME TABLE clause
        verify(renameStmt).execute(contains("`d_channels` TO `D_CHANNELS`"));
        verify(renameStmt).execute(contains("`d_m99` TO `D_M99`"));
    }
}
