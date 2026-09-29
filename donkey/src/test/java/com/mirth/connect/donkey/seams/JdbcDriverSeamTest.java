/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.seams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * NET-03 dependency seam (D-09/D-10/D-12): connect/CRUD/metadata characterization that runs
 * against embedded Derby by default (CI's only reachable target) and accepts {@code -Ddb=mysql},
 * {@code -Ddb=postgres}, or {@code -Ddb=mssql} to run the IDENTICAL assertion bodies against a
 * live local database -- only connection acquisition switches per D-12.
 * <p>
 * {@code -Ddb=mssql} is Phase 25's pre-bump rehearsal vehicle: {@link #mssqlEncryptDefaultCharacterized()}
 * characterizes mssql-jdbc 8.4.1's own {@code encrypt=false} default (no explicit encrypt property
 * anywhere in that test's connection setup) against a plain, non-TLS local SQL Server. mssql-jdbc
 * 12.x flips that default to {@code encrypt=true}, which makes an encrypt-less connection to a
 * non-TLS server FAIL -- re-running this suite under 12.x catches the bump loudly, and this
 * assertion must then be revised deliberately (not silently deleted).
 */
public class JdbcDriverSeamTest {

    private static final String db = System.getProperty("db", "derby");

    private static Path derbyTempDir;
    private static String derbyDbPath;
    private static Properties dbProperties;

    @BeforeClass
    public static void beforeClass() throws Exception {
        switch (db) {
            case "derby":
                derbyTempDir = Files.createTempDirectory("jdbc-driver-seam-");
                derbyDbPath = new File(derbyTempDir.toFile(), "jdbcseamdb").getAbsolutePath();
                break;
            case "mysql":
            case "postgres":
            case "mssql":
                dbProperties = loadDbProperties(propertiesFileSuffix(db));
                Class.forName(dbProperties.getProperty("database.driver"));
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported -Ddb value: " + db + " (expected derby|mysql|postgres|mssql)");
        }
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if ("derby".equals(db)) {
            shutdownDerbyQuietly(derbyDbPath);
            deleteRecursively(derbyTempDir.toFile());
        }
    }

    /**
     * Test 1: resolves the driver + URL for the selected db (default derby -> embedded temp
     * path), opens a Connection, and asserts it reports valid.
     */
    @Test
    public void driverConnects() throws Exception {
        try (Connection connection = openConnection()) {
            assertTrue("driver-reported connection validity for db=" + db, connection.isValid(5));
        }
    }

    /**
     * Test 2: CREATE TABLE, parameterized INSERT, SELECT-back, UPDATE, DELETE -- identical across
     * every -Ddb value (D-10). SQL is ANSI-portable so the same DDL/DML runs on all four engines.
     */
    @Test
    public void crudRoundTrips() throws Exception {
        String tableName = "SEAM_JDBC_CRUD";

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(true);

            try (Statement statement = connection.createStatement()) {
                dropTableQuietly(statement, tableName);
                statement.execute("CREATE TABLE " + tableName + " (ID INTEGER, NAME VARCHAR(50))");
            }

            try {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO " + tableName + " (ID, NAME) VALUES (?, ?)")) {
                    insert.setInt(1, 1);
                    insert.setString(2, "seam-insert");
                    assertEquals(1, insert.executeUpdate());
                }

                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT NAME FROM " + tableName + " WHERE ID = ?")) {
                    select.setInt(1, 1);
                    try (ResultSet result = select.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals("seam-insert", result.getString("NAME"));
                    }
                }

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE " + tableName + " SET NAME = ? WHERE ID = ?")) {
                    update.setString(1, "seam-update");
                    update.setInt(2, 1);
                    assertEquals(1, update.executeUpdate());
                }

                try (PreparedStatement verifyUpdate = connection.prepareStatement(
                        "SELECT NAME FROM " + tableName + " WHERE ID = ?")) {
                    verifyUpdate.setInt(1, 1);
                    try (ResultSet result = verifyUpdate.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals("seam-update", result.getString("NAME"));
                    }
                }

                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM " + tableName + " WHERE ID = ?")) {
                    delete.setInt(1, 1);
                    assertEquals(1, delete.executeUpdate());
                }

                try (PreparedStatement verifyDelete = connection.prepareStatement(
                        "SELECT NAME FROM " + tableName + " WHERE ID = ?")) {
                    verifyDelete.setInt(1, 1);
                    try (ResultSet result = verifyDelete.executeQuery()) {
                        assertFalse(result.next());
                    }
                }
            } finally {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TABLE " + tableName);
                }
            }
        }
    }

    /**
     * Test 3: {@link DatabaseMetaData} surface migrations rely on -- product name/driver version
     * are populated, and a just-created table is discoverable via getTables().
     */
    @Test
    public void metadataReadable() throws Exception {
        String tableName = "SEAM_JDBC_METADATA";

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(true);

            try (Statement statement = connection.createStatement()) {
                dropTableQuietly(statement, tableName);
                statement.execute("CREATE TABLE " + tableName + " (ID INTEGER, NAME VARCHAR(50))");
            }

            try {
                DatabaseMetaData metaData = connection.getMetaData();
                assertTrue("getDatabaseProductName() must be populated",
                        StringUtils.isNotBlank(metaData.getDatabaseProductName()));
                assertTrue("getDriverVersion() must be populated",
                        StringUtils.isNotBlank(metaData.getDriverVersion()));

                boolean tableFound = false;
                try (ResultSet tables = metaData.getTables(null, null, "%", new String[] {"TABLE"})) {
                    while (tables.next()) {
                        if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                            tableFound = true;
                            break;
                        }
                    }
                }
                assertTrue("created table must appear in DatabaseMetaData#getTables()", tableFound);
            } finally {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TABLE " + tableName);
                }
            }
        }
    }

    /**
     * Test 4 (mssql-only, gated with Assume so it's skipped on the CI derby default): opens a
     * connection to the local SQL Server using the donkey-testing-sqlserver.properties host/port/
     * databaseName, but with NO explicit "encrypt" property set anywhere (URL or Properties) --
     * characterizing mssql-jdbc 8.4.1's OWN default rather than an override. Asserts the driver
     * version is still pinned to the 8.x line, since Phase 25's 12.x bump is the one that flips
     * this default and must revise this assertion deliberately.
     */
    @Test
    public void mssqlEncryptDefaultCharacterized() throws Exception {
        Assume.assumeTrue("mssql".equals(db));

        Properties mssqlProperties = loadDbProperties("sqlserver");
        Class.forName(mssqlProperties.getProperty("database.driver"));

        String urlWithNoEncryptParam = stripEncryptParams(mssqlProperties.getProperty("database.url"));
        String username = mssqlProperties.getProperty("database.username");
        String password = mssqlProperties.getProperty("database.password");

        try (Connection connection = DriverManager.getConnection(urlWithNoEncryptParam, username, password)) {
            assertTrue("mssql connection with no explicit encrypt property must succeed against a "
                    + "plain non-TLS local SQL Server under the mssql-jdbc 8.4.1 encrypt=false default",
                    connection.isValid(5));

            String driverVersion = connection.getMetaData().getDriverVersion();
            assertTrue("mssql-jdbc driver version must be pinned to the 8.x line (pre mssql-jdbc "
                    + "12.x encrypt=true default bump): was " + driverVersion, driverVersion.startsWith("8."));
        }
    }

    // -------------------------------------------------------------------------------------------
    // Connection acquisition (only this switches across -Ddb values per D-12)
    // -------------------------------------------------------------------------------------------

    private static Connection openConnection() throws SQLException {
        if ("derby".equals(db)) {
            return DriverManager.getConnection("jdbc:derby:" + derbyDbPath + ";create=true");
        }

        String url = dbProperties.getProperty("database.url");
        String username = dbProperties.getProperty("database.username");
        String password = dbProperties.getProperty("database.password");
        return DriverManager.getConnection(url, username, password);
    }

    private static String propertiesFileSuffix(String dbValue) {
        // donkey/conf/ names the SQL Server test-config file "sqlserver", not "mssql"
        return "mssql".equals(dbValue) ? "sqlserver" : dbValue;
    }

    private static Properties loadDbProperties(String fileSuffix) throws IOException {
        String resourceName = "conf/donkey-testing-" + fileSuffix + ".properties";

        try (InputStream is = JdbcDriverSeamTest.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new IllegalStateException("Missing test properties resource on classpath: " + resourceName);
            }
            Properties properties = new Properties();
            properties.load(is);
            return properties;
        }
    }

    /**
     * Strips any {@code encrypt=} / {@code trustServerCertificate=} segment from a JDBC URL so a
     * connection can be opened with NO explicit encrypt property anywhere -- used only by
     * {@link #mssqlEncryptDefaultCharacterized()} to characterize the driver's own default.
     */
    private static String stripEncryptParams(String url) {
        String[] segments = url.split(";");
        StringBuilder result = new StringBuilder(segments[0]);

        for (int i = 1; i < segments.length; i++) {
            String lower = segments[i].toLowerCase();
            if (lower.startsWith("encrypt=") || lower.startsWith("trustservercertificate=")) {
                continue;
            }
            result.append(";").append(segments[i]);
        }

        return result.toString();
    }

    private static void dropTableQuietly(Statement statement, String tableName) {
        try {
            statement.execute("DROP TABLE " + tableName);
        } catch (SQLException e) {
            // Table did not exist yet -- expected on a fresh embedded-derby run; harmless on a
            // persistent local mysql/postgres/mssql instance too.
        }
    }

    private static void shutdownDerbyQuietly(String path) {
        try {
            DriverManager.getConnection("jdbc:derby:" + path + ";shutdown=true");
        } catch (SQLException e) {
            // Expected: Derby signals successful per-database shutdown via SQLException.
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
