/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.jdbc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Falsifiable negative test for CVE-2026-82583: DatabaseConnectorServlet.getTables() interpolated
 * a fully caller-controlled selectLimit template and unvalidated schema/tableName identifiers
 * into a plain java.sql.Statement, allowing an authenticated user to execute arbitrary SQL.
 * <p>
 * Drives the extracted package-private validation helpers (isSafeIdentifier / isSafeSelectLimit)
 * directly, and drives the extracted retrieveColumns query-build helper against Mockito-mocked
 * JDBC objects (the established pattern in this package - see DatabaseReceiverTest,
 * DatabaseDispatcherTest) rather than a live embedded Derby engine: Derby 10.17 (vendored in this
 * repo, server/lib/database/derby-10.17.1.0.jar) is compiled for Java 21+ and this module's build
 * runs on the pinned JDK 17 toolchain (.sdkmanrc), so a real EmbeddedDriver boot here fails with
 * UnsupportedClassVersionError independent of this fix - a pre-existing, already-documented
 * platform constraint (STATE.md: "embedded-Derby deployments require Java 21+"), not something
 * this CVE-only, Java-17-floor patch should work around by depending on JDK 21.
 * <p>
 * A revert of the validation logic makes the "never executed" assertions below fail against a
 * real Mockito interaction verification, not a mock configured to always agree with the test.
 */
public class DatabaseConnectorServletSqliTest {

    // -----------------------------------------------------------------------------------------
    // Direct validation-helper assertions
    // -----------------------------------------------------------------------------------------

    @Test
    public void isSafeIdentifier_rejectsQuoteSemicolonWhitespaceBackslashAndCommentTokens() {
        assertFalse(DatabaseConnectorServlet.isSafeIdentifier("foo\"; DROP TABLE bar; --"));
        assertFalse(DatabaseConnectorServlet.isSafeIdentifier("foo bar"));
        assertFalse(DatabaseConnectorServlet.isSafeIdentifier("foo\\bar"));
        assertFalse(DatabaseConnectorServlet.isSafeIdentifier("foo/*bar*/"));
        assertFalse(DatabaseConnectorServlet.isSafeIdentifier("foo--bar"));
    }

    @Test
    public void isSafeIdentifier_acceptsOrdinaryIdentifiersAndAbsentSchema() {
        assertTrue(DatabaseConnectorServlet.isSafeIdentifier("SQLI_TABLE"));
        assertTrue(DatabaseConnectorServlet.isSafeIdentifier("my_table$1"));
        assertTrue(DatabaseConnectorServlet.isSafeIdentifier(null));
        assertTrue(DatabaseConnectorServlet.isSafeIdentifier(""));
    }

    @Test
    public void isSafeSelectLimit_rejectsStackedStatementsCommentsAndWrongPlaceholderCount() {
        assertFalse(DatabaseConnectorServlet.isSafeSelectLimit("SELECT * FROM ? ; DROP TABLE x --"));
        assertFalse(DatabaseConnectorServlet.isSafeSelectLimit("SELECT * FROM ? -- inject"));
        assertFalse(DatabaseConnectorServlet.isSafeSelectLimit("SELECT 1 AS INJECTED FROM SYSIBM.SYSDUMMY1"));
        assertFalse(DatabaseConnectorServlet.isSafeSelectLimit("SELECT * FROM ? WHERE ? = 1"));
        assertFalse(DatabaseConnectorServlet.isSafeSelectLimit(null));
        assertFalse(DatabaseConnectorServlet.isSafeSelectLimit("SELECT * FROM ? # inject"));
    }

    @Test
    public void isSafeSelectLimit_acceptsDefaultTemplateAndOneTrailingSemicolon() {
        assertTrue(DatabaseConnectorServlet.isSafeSelectLimit("SELECT * FROM ? LIMIT 1"));
        assertTrue(DatabaseConnectorServlet.isSafeSelectLimit("SELECT * FROM ? LIMIT 1;"));
    }

    // -----------------------------------------------------------------------------------------
    // retrieveColumns() interaction assertions - proves the injection is never executed, not
    // merely that a mock happens to return a plausible-looking result
    // -----------------------------------------------------------------------------------------

    @Test
    public void retrieveColumns_rejectsSelectLimitWithoutThePlaceholderAndNeverExecutesIt() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        // What an unvalidated implementation would actually get back if it ran the attacker's
        // SQL verbatim - a result shaped nothing like the real table.
        ResultSet injectedRs = fakeResultSet("INJECTED");
        when(statement.executeQuery(anyString())).thenReturn(injectedRs);

        DatabaseMetaData dbMetaData = mock(DatabaseMetaData.class);
        ResultSet fallbackRs = fakeResultSet("ID", "NAME");
        when(dbMetaData.getColumns(any(), any(), anyString(), any())).thenReturn(fallbackRs);

        // Zero '?' placeholders: nothing is substituted, so an unvalidated implementation runs
        // this attacker-controlled statement completely independent of the target table.
        List<Column> columns = DatabaseConnectorServlet.retrieveColumns(connection, dbMetaData, null, "SQLI_TABLE", "SELECT 1 AS INJECTED FROM SYSIBM.SYSDUMMY1");

        verify(statement, never()).executeQuery(anyString());
        List<String> columnNames = columnNames(columns);
        assertTrue("must fall back to the real table's columns", columnNames.contains("ID"));
        assertTrue("must fall back to the real table's columns", columnNames.contains("NAME"));
    }

    @Test
    public void retrieveColumns_rejectsUnsafeSchemaIdentifierAndNeverExecutesIt() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        ResultSet injectedRs = fakeResultSet("INJECTED");
        when(statement.executeQuery(anyString())).thenReturn(injectedRs);

        DatabaseMetaData dbMetaData = mock(DatabaseMetaData.class);
        ResultSet fallbackRs = fakeResultSet("ID", "NAME");
        when(dbMetaData.getColumns(any(), any(), anyString(), any())).thenReturn(fallbackRs);

        // A safe selectLimit template, but a schema identifier carrying a quote/semicolon/comment
        // - must be rejected on the identifier alone.
        List<Column> columns = DatabaseConnectorServlet.retrieveColumns(connection, dbMetaData, "foo\"; DROP TABLE bar; --", "SQLI_TABLE", "SELECT * FROM ? LIMIT 1");

        verify(statement, never()).executeQuery(anyString());
        List<String> columnNames = columnNames(columns);
        assertTrue(columnNames.contains("ID"));
        assertTrue(columnNames.contains("NAME"));
    }

    @Test
    public void retrieveColumns_safeSelectLimitExecutesDirectlyAndReturnsQueriedColumns() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        ResultSet queriedRs = fakeResultSet("ID", "NAME");
        when(statement.executeQuery(anyString())).thenReturn(queriedRs);

        DatabaseMetaData dbMetaData = mock(DatabaseMetaData.class);

        // The servlet interface's literal @DefaultValue selectLimit, with safe identifiers -
        // must be executed directly (not routed to the metadata fallback).
        List<Column> columns = DatabaseConnectorServlet.retrieveColumns(connection, dbMetaData, null, "SQLI_TABLE", "SELECT * FROM ? LIMIT 1");

        verify(statement).executeQuery(anyString());
        verify(dbMetaData, never()).getColumns(any(), any(), anyString(), any());
        List<String> columnNames = columnNames(columns);
        assertTrue(columnNames.contains("ID"));
        assertTrue(columnNames.contains("NAME"));
    }

    @Test
    public void retrieveColumns_emptySelectLimitUsesGenericMetadataPathUnchanged() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData dbMetaData = mock(DatabaseMetaData.class);
        ResultSet fallbackRs = fakeResultSet("ID", "NAME");
        when(dbMetaData.getColumns(any(), any(), anyString(), any())).thenReturn(fallbackRs);

        List<Column> columns = DatabaseConnectorServlet.retrieveColumns(connection, dbMetaData, null, "SQLI_TABLE", "");

        List<String> columnNames = columnNames(columns);
        assertTrue(columnNames.contains("ID"));
        assertTrue(columnNames.contains("NAME"));
    }

    // -----------------------------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------------------------

    /**
     * A mocked {@link ResultSet} that yields one row per given column name and also answers
     * {@link ResultSet#getMetaData()} with matching {@link ResultSetMetaData}, so the same fixture
     * works for both the DatabaseMetaData#getColumns() row-per-column shape and the
     * Statement#executeQuery() ResultSetMetaData shape retrieveColumns() reads from.
     */
    private static ResultSet fakeResultSet(final String... columnNames) throws Exception {
        ResultSet rs = mock(ResultSet.class);

        // A stateful row cursor: next() advances it and reports whether a row remains;
        // getString("COLUMN_NAME") reads whichever column name the cursor currently sits on.
        final int[] rowIndex = { -1 };
        when(rs.next()).thenAnswer(invocation -> {
            rowIndex[0]++;
            return rowIndex[0] < columnNames.length;
        });
        when(rs.getString("COLUMN_NAME")).thenAnswer(invocation -> columnNames[rowIndex[0]]);
        when(rs.getString("TYPE_NAME")).thenReturn("VARCHAR");
        when(rs.getInt("COLUMN_SIZE")).thenReturn(50);

        ResultSetMetaData rsmd = mock(ResultSetMetaData.class);
        when(rsmd.getColumnCount()).thenReturn(columnNames.length);
        for (int i = 0; i < columnNames.length; i++) {
            int oneBased = i + 1;
            when(rsmd.getColumnName(oneBased)).thenReturn(columnNames[i]);
            when(rsmd.getColumnTypeName(oneBased)).thenReturn("VARCHAR");
            when(rsmd.getPrecision(oneBased)).thenReturn(50);
        }
        when(rs.getMetaData()).thenReturn(rsmd);

        return rs;
    }

    private static List<String> columnNames(List<Column> columns) {
        List<String> names = new ArrayList<String>();
        for (Column column : columns) {
            names.add(column.getName());
        }
        return names;
    }
}
