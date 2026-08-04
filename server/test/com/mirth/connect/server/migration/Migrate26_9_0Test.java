/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.DriverInfo;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;

/**
 * Proves Migrate26_9_0 (CVE-04): the retired jTDS DriverInfo entry is stripped from the
 * persisted driver list, the Microsoft SQL Server entry is retained, the surviving entries keep
 * their original relative order, the migration is idempotent on a second run, and it is safe
 * (no exception, no-op) on an empty list or a list carrying no jTDS entry.
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

        // Second run: feed the already-stripped list back in. Nothing further should change.
        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>(firstRunResult));
        new Migrate26_9_0().migrate();
        List<DriverInfo> secondRunResult = captureSetDatabaseDrivers();

        assertEquals("Re-running the migration must not change an already-stripped list", firstRunResult, secondRunResult);
        assertFalse(containsClass(secondRunResult, JTDS_CLASS));
        assertTrue(containsClass(secondRunResult, MSSQL_CLASS));
    }

    @Test
    public void emptyAndAbsentSafe() throws Exception {
        // Empty list: no-op, no exception.
        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>());
        new Migrate26_9_0().migrate();
        List<DriverInfo> emptyResult = captureSetDatabaseDrivers();
        assertTrue("Empty list must remain empty", emptyResult.isEmpty());

        reset(configurationController);

        // jTDS-absent list: no-op, no exception, list unchanged.
        List<DriverInfo> absentSeed = new ArrayList<DriverInfo>();
        absentSeed.add(mssqlEntry());
        absentSeed.add(new DriverInfo("Oracle", "oracle.jdbc.driver.OracleDriver", "jdbc:oracle:thin:@host:port:dbname", "SELECT * FROM ? WHERE ROWNUM < 2"));
        when(configurationController.getDatabaseDrivers()).thenReturn(new ArrayList<DriverInfo>(absentSeed));
        new Migrate26_9_0().migrate();
        List<DriverInfo> absentResult = captureSetDatabaseDrivers();
        assertEquals("A jTDS-absent list must be unchanged", absentSeed, absentResult);
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
