/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-2458: the gateway's default JDBC drivers, after 26.9 removed jTDS.
 */

package com.mirth.connect.plugins.dynamiclookup.server.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link DatabaseSettings}' driver defaulting and its password-safe toString().
 */
public class DatabaseSettingsTest {

    private static final String[] DATABASES = { "derby", "mysql", "oracle", "postgres", "sqlserver" };

    @Test
    public void sqlServerWithNoDriver_defaultsToMssqlJdbc() {
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver", driverFor("sqlserver", null));
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver", driverFor("sqlserver", "  "));
    }

    @Test
    public void explicitDriver_winsOverDefault() {
        assertEquals("com.example.CustomDriver", driverFor("sqlserver", "com.example.CustomDriver"));
    }

    @Test
    public void noDatabaseDefaultsToJtds() {
        // jtds-1.3.1.jar was removed in 26.9, so a jTDS default fails with ClassNotFoundException.
        for (String database : DATABASES) {
            String driver = driverFor(database, null);
            assertNotNull("no default driver for " + database, driver);
            assertFalse(database + " defaults to jTDS: " + driver, driver.startsWith("net.sourceforge.jtds"));
        }
    }

    @Test
    public void toString_masksPassword() {
        DatabaseSettings settings = new DatabaseSettings();
        settings.setPassword("s3cr3t-Value");
        String text = settings.toString();
        assertFalse(text, text.contains("s3cr3t-Value"));
        assertTrue(text, text.contains("password='<set>'"));
    }

    @Test
    public void toString_reportsUnsetPassword() {
        assertTrue(new DatabaseSettings().toString().contains("password='<unset>'"));
    }

    private static String driverFor(String database, String driver) {
        DatabaseSettings settings = new DatabaseSettings();
        settings.setDatabase(database);
        settings.setDriver(driver);
        return settings.getProperties().getProperty("driver");
    }
}
