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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.DonkeyConfiguration;
import com.mirth.connect.donkey.server.DonkeyConnectionPools;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.server.data.DonkeyDao;
import com.mirth.connect.donkey.server.data.DonkeyDaoFactory;
import com.mirth.connect.donkey.test.util.TestUtils;

/**
 * NET-03 dependency seam (D-09/D-10/D-12): characterizes Derby 10.16.1.1 AS SHIPPED, both raw-JDBC
 * fresh-create/upgrade-in-place semantics and the Donkey DAO persistence layer running against the
 * embedded engine.
 * <p>
 * This suite is Phase 24's bump oracle: before/after the 10.17.1.0 upgrade, re-run it unchanged.
 * Per the Phase 20 platform-compatibility decision, Derby's 10.17 line is a <b>one-way</b> database
 * format upgrade (no downgrade path) and requires Java SE 21+ for the embedded engine. That means
 * {@link #test2_upgradeInPlaceFlagConnects()}'s expectations may need to be <i>deliberately revised</i>
 * once the 10.17 engine lands (e.g. asserting the new engine still opens a 10.16-created database
 * directory) -- the test must not be silently deleted at bump time; it is the regression signal.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DerbyEngineSeamTest {

    private static final String TABLE_NAME = "SEAM_DERBY_ENGINE";

    private static Path tempParentDir;
    private static String dbPath;

    @BeforeClass
    public static void beforeClass() throws Exception {
        tempParentDir = Files.createTempDirectory("derby-engine-seam-");
        dbPath = new File(tempParentDir.toFile(), "seamdb").getAbsolutePath();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        shutdownDatabaseQuietly(dbPath);
        deleteRecursively(tempParentDir.toFile());
    }

    /**
     * Characterizes fresh-create (D-10): connecting to a brand-new embedded Derby database
     * directory with {@code create=true} boots the engine, creates the on-disk database, and
     * persists a round-tripped row.
     */
    @Test
    public void test1_freshCreateBootsAndPersists() throws Exception {
        String url = "jdbc:derby:" + dbPath + ";create=true";

        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(true);

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE " + TABLE_NAME + " (ID INTEGER, NAME VARCHAR(50))");
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + TABLE_NAME + " (ID, NAME) VALUES (?, ?)")) {
                insert.setInt(1, 1);
                insert.setString(2, "fresh-create");
                insert.executeUpdate();
            }

            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT NAME FROM " + TABLE_NAME + " WHERE ID = ?")) {
                select.setInt(1, 1);
                try (ResultSet result = select.executeQuery()) {
                    assertTrue("row inserted during fresh-create must be readable back", result.next());
                    assertEquals("fresh-create", result.getString("NAME"));
                }
            }
        }

        // Shut down only this database (not the whole engine) so the upgrade-in-place test can
        // reopen it exclusively -- Derby only allows one open connection set per db per JVM.
        shutdownDatabaseQuietly(dbPath);
    }

    /**
     * Characterizes upgrade-in-place semantics on 10.16.1.1 (D-10): reopening the SAME database
     * directory created by {@link #test1_freshCreateBootsAndPersists()} with {@code upgrade=true}
     * succeeds and the previously-written data survives. This is the exact behavior Phase 24's
     * 10.17 bump changes -- see the class javadoc.
     */
    @Test
    public void test2_upgradeInPlaceFlagConnects() throws Exception {
        String url = "jdbc:derby:" + dbPath + ";upgrade=true";

        try (Connection connection = DriverManager.getConnection(url)) {
            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "SELECT NAME FROM " + TABLE_NAME + " WHERE ID = 1")) {
                assertTrue(
                        "data written during fresh-create must survive an upgrade=true reopen on 10.16.1.1",
                        result.next());
                assertEquals("fresh-create", result.getString("NAME"));
            }
        }

        shutdownDatabaseQuietly(dbPath);
    }

    /**
     * Proves the Donkey persistence layer itself (not just raw JDBC) works on the embedded Derby
     * engine, using the SAME default test config plain {@code ant test-run} uses (no -D flags).
     */
    @Test
    public void test3_donkeyDaoLayerWorksOnDerby() throws Exception {
        Donkey donkey = Donkey.getInstance();
        DonkeyConfiguration config = TestUtils.getDonkeyTestConfiguration();

        // Close any leaked connection pools from a previously-run test class in this JVM.
        TestUtils.shutdownConnectionPools();
        DonkeyConnectionPools.getInstance().init(config.getDonkeyProperties());
        donkey.startEngine(config);

        String channelId = "derbyEngineSeamChannel";

        try {
            TestUtils.initChannel(channelId);

            DonkeyDaoFactory daoFactory = TestUtils.getDaoFactory();
            DonkeyDao dao = daoFactory.getDao();
            try {
                dao.checkAndCreateChannelTables();
                dao.commit();
            } finally {
                dao.close();
            }

            TestUtils.assertChannelExists(channelId, true);

            long localChannelId = ChannelController.getInstance().getLocalChannelId(channelId);
            assertTrue("Donkey DAO layer must register a valid local channel id on Derby",
                    localChannelId > 0);

            try (Connection connection = TestUtils.getConnection()) {
                assertTrue("connection acquired through the Donkey connection pool must be valid",
                        connection.isValid(5));
            }
        } finally {
            donkey.stopEngine();
            TestUtils.shutdownConnectionPools();
        }
    }

    /**
     * Shuts down a single Derby database (not the whole engine, which would disturb other
     * Derby-backed test classes sharing this JVM). Derby signals a successful per-database
     * shutdown by throwing a SQLException (SQLState 08006) -- this is expected and swallowed.
     */
    private static void shutdownDatabaseQuietly(String path) {
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
