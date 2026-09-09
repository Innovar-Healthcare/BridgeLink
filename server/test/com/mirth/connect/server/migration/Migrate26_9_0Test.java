/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.Version;
import com.mirth.connect.model.DriverInfo;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;

/**
 * Proves Migrate26_9_0 (CVE-04): the retired jTDS DriverInfo entry is stripped from the
 * persisted driver list when a Microsoft SQL Server entry already survives, or substituted in
 * place with the canonical Microsoft SQL Server entry when it is the list's only SQL Server
 * driver (IRT-1912); the Microsoft SQL Server entry is retained, the surviving entries keep
 * their original relative order, the migration is idempotent on a second run, and it is safe
 * (no exception, true no-op - {@code setDatabaseDrivers} is never invoked) on an empty list or a
 * list carrying no jTDS entry.
 */
public class Migrate26_9_0Test {

    private static final String JTDS_CLASS = "net.sourceforge.jtds.jdbc.Driver";
    private static final String MSSQL_CLASS = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    private static ControllerFactory controllerFactory;
    private static ConfigurationController configurationController;

    @BeforeClass
    public static void setupClass() throws Exception {
        controllerFactory = mock(ControllerFactory.class);

        configurationController = mock(ConfigurationController.class);
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);
    }

    @Before
    public void setup() {
        // The ControllerFactory/ConfigurationController mocks are static (shared across all
        // @Test methods in this class); reset invocation history before each test so
        // per-test verify()/ArgumentCaptor assertions never see a prior test's calls.
        reset(configurationController);
    }

    @Test
    public void stripsJtdsEntry() throws Exception {
        List<DriverInfo> drivers = new ArrayList<DriverInfo>();
        drivers.add(jtdsEntry());
        drivers.add(mssqlEntry());

        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>(drivers));

        new Migrate26_9_0().migrate();

        List<DriverInfo> result = captureSetDatabaseDrivers();

        assertFalse("jTDS entry must be stripped", containsClass(result, JTDS_CLASS));
        assertTrue("Microsoft SQL Server entry must be retained", containsClass(result, MSSQL_CLASS));
    }

    @Test
    public void idempotentSecondRun() throws Exception {
        List<DriverInfo> seed = new ArrayList<DriverInfo>();
        seed.add(jtdsEntry());
        seed.add(mssqlEntry());

        // First run: strips the jTDS entry.
        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>(seed));
        new Migrate26_9_0().migrate();
        List<DriverInfo> firstRunResult = captureSetDatabaseDrivers();

        reset(configurationController);

        // Second run: feed the already-stripped list back in. Nothing changed, so the
        // migrator must be a true no-op - it must NOT re-persist the list.
        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>(firstRunResult));
        new Migrate26_9_0().migrate();

        verify(configurationController, never()).setDatabaseDrivers(any());
        assertFalse(containsClass(firstRunResult, JTDS_CLASS));
        assertTrue(containsClass(firstRunResult, MSSQL_CLASS));
    }

    @Test
    public void emptyAndAbsentSafe() throws Exception {
        // Empty list: no-op, no exception, and no re-persist.
        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>());
        new Migrate26_9_0().migrate();
        verify(configurationController, never()).setDatabaseDrivers(any());

        reset(configurationController);

        // jTDS-absent list: no-op, no exception, no re-persist.
        List<DriverInfo> absentSeed = new ArrayList<DriverInfo>();
        absentSeed.add(mssqlEntry());
        absentSeed.add(new DriverInfo("Oracle", "oracle.jdbc.driver.OracleDriver", "jdbc:oracle:thin:@host:port:dbname", "SELECT * FROM ? WHERE ROWNUM < 2"));
        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>(absentSeed));
        new Migrate26_9_0().migrate();
        verify(configurationController, never()).setDatabaseDrivers(any());
    }

    @Test
    public void orderPreserved() throws Exception {
        DriverInfo oracle = new DriverInfo("Oracle", "oracle.jdbc.driver.OracleDriver", "jdbc:oracle:thin:@host:port:dbname", "SELECT * FROM ? WHERE ROWNUM < 2");
        DriverInfo postgres = new DriverInfo("PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://host:port/dbname", "SELECT * FROM ? LIMIT 1");

        List<DriverInfo> drivers = new ArrayList<DriverInfo>();
        drivers.add(oracle);
        drivers.add(jtdsEntry());
        drivers.add(mssqlEntry());
        drivers.add(postgres);

        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>(drivers));

        new Migrate26_9_0().migrate();

        List<DriverInfo> result = captureSetDatabaseDrivers();

        List<DriverInfo> expected = new ArrayList<DriverInfo>();
        expected.add(oracle);
        expected.add(mssqlEntry());
        expected.add(postgres);

        assertEquals("Surviving entries must keep their original relative order", expected, result);
    }

    @Test
    public void substitutesMssqlWhenAbsent() throws Exception {
        // The real-world IRT-1912 profile: a persisted list carrying jTDS alongside other
        // drivers but NO Microsoft SQL Server entry. Removing jTDS outright would leave the
        // install with no SQL Server driver; the migrator must substitute the Microsoft entry
        // in place instead.
        DriverInfo mysql = new DriverInfo("MySQL", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://host:port/dbname", "SELECT * FROM ? LIMIT 1");
        DriverInfo oracle = new DriverInfo("Oracle", "oracle.jdbc.driver.OracleDriver", "jdbc:oracle:thin:@host:port:dbname", "SELECT * FROM ? WHERE ROWNUM < 2");
        DriverInfo postgres = new DriverInfo("PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://host:port/dbname", "SELECT * FROM ? LIMIT 1");
        DriverInfo sqlite = new DriverInfo("SQLite", "org.sqlite.JDBC", "jdbc:sqlite:dbfile.db", "SELECT * FROM ? LIMIT 1");

        List<DriverInfo> drivers = new ArrayList<DriverInfo>();
        drivers.add(mysql);
        drivers.add(oracle);
        drivers.add(postgres);
        drivers.add(jtdsEntry());
        drivers.add(sqlite);

        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>(drivers));

        new Migrate26_9_0().migrate();

        List<DriverInfo> result = captureSetDatabaseDrivers();

        assertFalse("jTDS entry must be gone", containsClass(result, JTDS_CLASS));
        assertTrue("Microsoft SQL Server must be substituted in when it was absent", containsClass(result, MSSQL_CLASS));

        // Substituted in place: the Microsoft entry takes jTDS's former position (index 3) and
        // the surrounding drivers are untouched, so the whole list matches expected exactly.
        List<DriverInfo> expected = new ArrayList<DriverInfo>();
        expected.add(mysql);
        expected.add(oracle);
        expected.add(postgres);
        expected.add(mssqlEntry());
        expected.add(sqlite);

        assertEquals("Microsoft entry must be substituted in place, preserving list order and size", expected, result);
    }

    @Test
    public void substitutesMssqlWhenJtdsIsOnlyEntry() throws Exception {
        // A list whose sole entry is jTDS must not be emptied; it must become the single
        // Microsoft SQL Server entry, so the install still has a SQL Server driver.
        List<DriverInfo> drivers = new ArrayList<DriverInfo>();
        drivers.add(jtdsEntry());

        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>(drivers));

        new Migrate26_9_0().migrate();

        List<DriverInfo> result = captureSetDatabaseDrivers();

        List<DriverInfo> expected = new ArrayList<DriverInfo>();
        expected.add(mssqlEntry());

        assertEquals("jTDS-only list must substitute to a single Microsoft SQL Server entry", expected, result);
    }

    @Test
    public void substitutesFirstJtdsAndRemovesTheRest() throws Exception {
        // A pathological list carrying more than one jTDS row and no Microsoft entry must
        // yield exactly ONE Microsoft SQL Server entry (the first jTDS is substituted in
        // place, any further jTDS rows are removed), never a duplicate Microsoft row.
        DriverInfo oracle = new DriverInfo("Oracle", "oracle.jdbc.driver.OracleDriver", "jdbc:oracle:thin:@host:port:dbname", "SELECT * FROM ? WHERE ROWNUM < 2");

        List<DriverInfo> drivers = new ArrayList<DriverInfo>();
        drivers.add(jtdsEntry());
        drivers.add(oracle);
        drivers.add(jtdsEntry());

        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>(drivers));

        new Migrate26_9_0().migrate();

        List<DriverInfo> result = captureSetDatabaseDrivers();

        List<DriverInfo> expected = new ArrayList<DriverInfo>();
        expected.add(mssqlEntry());
        expected.add(oracle);

        assertEquals("first jTDS substituted, additional jTDS rows removed, no duplicate Microsoft entry", expected, result);
    }

    /*
     * Criterion 2 (IRT-2217): server.defaultencoding config-migration cases. These operate on a
     * bare PropertiesConfiguration and need no ControllerFactory mock, mirroring
     * Migrate4_3_0Test's spy + stub-accessor + assert pattern for updateSecurityConfiguration.
     */

    @Test
    public void testCharsetMigrationWritesHostEncodingWhenNonUtf8AndAbsent() throws Exception {
        Migrate26_9_0 migrator = spy(new Migrate26_9_0());
        String hostEncoding = Charset.forName("windows-1252").name();
        when(migrator.getHostEncoding()).thenReturn(hostEncoding);
        migrator.setStartingVersion(Version.v26_6_0);

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        migrator.updateConfiguration(configuration);

        assertEquals(hostEncoding, configuration.getString("server.defaultencoding"));
    }

    @Test
    public void testCharsetMigrationNoWriteWhenHostIsUtf8() throws Exception {
        Migrate26_9_0 migrator = spy(new Migrate26_9_0());
        when(migrator.getHostEncoding()).thenReturn(StandardCharsets.UTF_8.name());
        migrator.setStartingVersion(Version.v26_6_0);

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        migrator.updateConfiguration(configuration);

        assertFalse(configuration.containsKey("server.defaultencoding"));
    }

    @Test
    public void testCharsetMigrationDoesNotClobberExistingProperty() throws Exception {
        Migrate26_9_0 migrator = spy(new Migrate26_9_0());
        when(migrator.getHostEncoding()).thenReturn(Charset.forName("windows-1252").name());
        migrator.setStartingVersion(Version.v26_6_0);

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setProperty("server.defaultencoding", "operator-pinned-value");
        migrator.updateConfiguration(configuration);

        assertEquals("An operator-set server.defaultencoding must never be clobbered", "operator-pinned-value", configuration.getString("server.defaultencoding"));
    }

    @Test
    public void testCharsetMigrationNoWriteWhenStartingVersionIsLatest() throws Exception {
        Migrate26_9_0 migrator = spy(new Migrate26_9_0());
        when(migrator.getHostEncoding()).thenReturn(Charset.forName("windows-1252").name());
        migrator.setStartingVersion(Version.getLatest());

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        migrator.updateConfiguration(configuration);

        assertFalse("Idempotency: a config already at the latest version must not be rewritten", configuration.containsKey("server.defaultencoding"));
    }

    @Test
    public void testCharsetMigrationNoWriteWhenHostEncodingBlankOrNull() throws Exception {
        Migrate26_9_0 migratorNull = spy(new Migrate26_9_0());
        when(migratorNull.getHostEncoding()).thenReturn(null);
        migratorNull.setStartingVersion(Version.v26_6_0);

        PropertiesConfiguration configurationNull = new PropertiesConfiguration();
        migratorNull.updateConfiguration(configurationNull);

        assertFalse("A null host encoding must never be written", configurationNull.containsKey("server.defaultencoding"));

        Migrate26_9_0 migratorBlank = spy(new Migrate26_9_0());
        when(migratorBlank.getHostEncoding()).thenReturn("");
        migratorBlank.setStartingVersion(Version.v26_6_0);

        PropertiesConfiguration configurationBlank = new PropertiesConfiguration();
        migratorBlank.updateConfiguration(configurationBlank);

        assertFalse("A blank host encoding must never be written", configurationBlank.containsKey("server.defaultencoding"));
    }

    /**
     * Falsifiable source tripwire (IRT-2217, criterion 2 crux). Reads Migrate26_9_0.java from
     * the working tree and asserts it reads native.encoding for host-charset detection and does
     * NOT read the JVM's own default-charset API. Under JEP 400 (JDK 18+) that API always
     * returns UTF-8 regardless of host, so a future edit that swaps getHostEncoding()'s body
     * back to it would compile, would pass every stubbed unit case above (since they stub the
     * seam directly), and would still never fire on a real non-UTF-8 host on the Java 21
     * target - exactly the silent-corruption failure this phase exists to prevent. This test is
     * the only guard that would catch that specific regression.
     *
     * Falsifiability, proven manually and reverted byte-for-byte: temporarily changing
     * getHostEncoding()'s body in Migrate26_9_0.java to return the JVM's own default-charset
     * value instead of System.getProperty("native.encoding") reddens this test (fails on the
     * assertFalse below), proving it is not a tautology.
     */
    @Test
    public void migrationUsesNativeEncodingNotDefaultCharset() throws Exception {
        File sourceFile = new File("src/com/mirth/connect/server/migration/Migrate26_9_0.java");
        assertTrue("Migrate26_9_0.java source file must be found at " + sourceFile.getAbsolutePath(), sourceFile.isFile());

        String source = new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);

        assertTrue("Migrate26_9_0.java must read native.encoding for host-charset detection", source.contains("native.encoding"));
        assertFalse("Migrate26_9_0.java must not use the JVM default-charset API for host-charset detection (JEP 400 makes it always UTF-8, so it would never fire on a non-UTF-8 Java 21 host)", source.contains("Charset.defaultCharset"));
    }

    private List<DriverInfo> captureSetDatabaseDrivers() throws ControllerException {
        ArgumentCaptor<List<DriverInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(configurationController).setDatabaseDrivers(captor.capture());
        return captor.getValue();
    }

    private boolean containsClass(List<DriverInfo> drivers, String className) {
        for (DriverInfo driver : drivers) {
            if (className.equals(driver.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private DriverInfo jtdsEntry() {
        return new DriverInfo("SQL Server/Sybase (jTDS)", JTDS_CLASS, "jdbc:jtds:sqlserver://host:port/dbname", "SELECT TOP 1 * FROM ?");
    }

    private DriverInfo mssqlEntry() {
        return new DriverInfo("Microsoft SQL Server", MSSQL_CLASS, "jdbc:sqlserver://host:port;databaseName=dbname", "SELECT TOP 1 * FROM ?");
    }
}
