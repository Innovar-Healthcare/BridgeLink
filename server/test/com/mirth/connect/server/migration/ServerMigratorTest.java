/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.Test;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.model.util.MigrationException;

/**
 * Proves ServerMigrator.getCurrentVersion() fails loudly on a present-but-unparseable
 * SCHEMA_INFO.VERSION value instead of silently coercing the migration ladder's starting point
 * to V0 and replaying legacy delta scripts against an already-populated schema (IRT-2329
 * criterion 4 / IRT-2295 scope item 3), while preserving the no-row and blank-row null contract
 * that updateVersion() relies on to choose its INSERT-vs-UPDATE branch.
 */
public class ServerMigratorTest {

    @Test
    public void unparseableVersionThrowsMigrationExceptionNamingTheString() throws Exception {
        ServerMigrator migrator = newMigratorReturning(true, "99.99.99");

        try {
            invokeGetCurrentVersion(migrator);
            fail("Expected getCurrentVersion() to throw for a present-but-unparseable version");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertTrue("cause must be a MigrationException", cause instanceof MigrationException);
            assertTrue("message must name the offending string", cause.getMessage().contains("99.99.99"));
        }
    }

    @Test
    public void noRowReturnsNullWithoutThrowing() throws Exception {
        ServerMigrator migrator = newMigratorReturning(false, null);

        Version result = invokeGetCurrentVersion(migrator);

        assertNull("no-row must return null, preserving updateVersion()'s INSERT-vs-UPDATE decision", result);
    }

    @Test
    public void blankRowReturnsNullWithoutThrowing() throws Exception {
        ServerMigrator migrator = newMigratorReturning(true, "");

        Version result = invokeGetCurrentVersion(migrator);

        assertNull("a blank version string must not be treated as unparseable/fail-loud", result);
    }

    @Test
    public void parseableRowResolvesToTheMatchingVersion() throws Exception {
        ServerMigrator migrator = newMigratorReturning(true, "26.6.1");

        Version result = invokeGetCurrentVersion(migrator);

        assertEquals(Version.v26_6_1, result);
    }

    private ServerMigrator newMigratorReturning(boolean hasRow, String rawVersion) throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT VERSION FROM SCHEMA_INFO")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(hasRow);
        when(resultSet.getString(1)).thenReturn(rawVersion);

        ServerMigrator migrator = new ServerMigrator();
        migrator.setConnection(connection);
        return migrator;
    }

    private Version invokeGetCurrentVersion(ServerMigrator migrator) throws Exception {
        Method method = ServerMigrator.class.getDeclaredMethod("getCurrentVersion");
        method.setAccessible(true);
        return (Version) method.invoke(migrator);
    }
}
