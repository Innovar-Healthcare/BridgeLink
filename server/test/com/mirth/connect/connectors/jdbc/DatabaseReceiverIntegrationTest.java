/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.jdbc;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.donkey.server.channel.Connector;
import com.mirth.connect.donkey.server.channel.DefaultChannelProcessLock;
import com.mirth.connect.donkey.server.channel.FilterTransformerExecutor;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.event.EventDispatcher;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.server.TestUtils.DummyChannel;
import com.mirth.connect.server.channel.MirthMetaDataReplacer;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ContextFactoryController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.controllers.ExtensionController;
import com.mirth.connect.server.util.javascript.MirthContextFactory;

/**
 * Repaired from the live-PostgreSQL/full-server-boot integration test (26.10-03 rename) onto a
 * mocked JDBC seam (26.10-04, D-06): a test-registered {@link java.sql.Driver} intercepts
 * DriverManager's connection lookup for {@link #DB_URL} and vends Mockito-mocked
 * Connection/PreparedStatement/Statement/ResultSet objects backed by an in-memory
 * {@link FakeDatabase}, so the real {@link DatabaseReceiver}/{@link DatabaseReceiverQuery}/
 * {@link DatabaseReceiverScript} production code runs unmodified end to end (poll/select/update
 * dispatch, parameter substitution, resultMap-to-XML) without a live database or a full
 * server boot. The JS-script tests drive a real Rhino
 * {@link MirthContextFactory} (the same seam proven in {@code RhinoSeamTest}), so
 * {@code DatabaseConnectionFactory} calls made from script text also route through the same fake
 * driver.
 * <p>
 * Two postgres-only behaviors ({@code SELECT ... RETURNS SETOF} / {@code plpgsql} function
 * bodies) are re-expressed rather than literally executed: {@link #testStoredProcedures()} and
 * the multi-statement half of {@link #testMultipleStatements()} route the *same* select/update
 * SQL text through {@link #interpretSelect} / {@link #interpretMutation}'s pattern matching
 * instead of a real PL/pgSQL engine, preserving the connector-level behavior under test
 * (select/update dispatch, per-row parameter substitution) without a live Postgres. See
 * 26.10-04-SUMMARY.md for the full deviation record.
 */
public class DatabaseReceiverIntegrationTest {
    private final static String DB_DRIVER = "org.postgresql.Driver";
    private final static String DB_URL = "jdbc:postgresql://localhost:5432/mirthdb";
    private final static String DB_USERNAME = "";
    private final static String DB_PASSWORD = "";
    private final static String TABLE = "mypatients";
    private final static String TEST_CHANNEL_ID = "testchannel";
    private final static String TEST_SERVER_ID = "testserver";

    private static MirthContextFactory contextFactory;
    private static FakeDriver fakeDriver;
    private static TestChannel testChannel;
    private static final FakeDatabase fakeDatabase = new FakeDatabase();

    /**
     * DummyChannel does not override getEventDispatcher(), which is normally wired up by a real
     * server boot; without a real boot it returns null and SourceConnector.start() NPEs
     * dispatching its IDLE/POLLING status events. A no-op dispatcher is enough since nothing here
     * asserts on dispatched channel status events (same fix already used by the sibling
     * DatabaseReceiverInvalidColumnNameTest).
     */
    private static final class TestChannel extends DummyChannel {
        TestChannel(String channelId, String serverId) {
            super(channelId, serverId);
        }

        @Override
        protected EventDispatcher getEventDispatcher() {
            return event -> {
            };
        }
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        EventController eventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(eventController);

        ConfigurationController configurationController = mock(ConfigurationController.class);
        when(configurationController.getRhinoLanguageVersion()).thenReturn(org.mozilla.javascript.Context.VERSION_ES6);
        when(configurationController.getServerVersion()).thenReturn("26.9.0-jdbc-repair-test");
        when(configurationController.getServerId()).thenReturn(TEST_SERVER_ID);
        when(configurationController.getConfigurationMap()).thenReturn(new HashMap<String, String>());
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        ExtensionController extensionController = mock(ExtensionController.class);
        when(extensionController.getConnectorMetaData()).thenReturn(new HashMap<String, ConnectorMetaData>());
        when(extensionController.getPluginMetaData()).thenReturn(new HashMap<String, PluginMetaData>());
        when(controllerFactory.createExtensionController()).thenReturn(extensionController);

        ContextFactoryController contextFactoryController = mock(ContextFactoryController.class);
        when(contextFactoryController.getContextFactory(any())).thenReturn(createRealContextFactory());
        when(controllerFactory.createContextFactoryController()).thenReturn(contextFactoryController);

        // DatabaseReceiver.poll()'s error-path logging (and processResultList's invalid-entry
        // logging) look up the deployed channel's name via ChannelController.getInstance(); a
        // real server boot wires this, so it needs its own mock here too.
        com.mirth.connect.model.Channel deployedChannel = new com.mirth.connect.model.Channel();
        deployedChannel.setId(TEST_CHANNEL_ID);
        deployedChannel.setName(TEST_CHANNEL_ID);
        com.mirth.connect.server.controllers.ChannelController channelController = mock(com.mirth.connect.server.controllers.ChannelController.class);
        when(channelController.getDeployedChannelById(any())).thenReturn(deployedChannel);
        when(controllerFactory.createChannelController()).thenReturn(channelController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);

        fakeDriver = new FakeDriver();
        DriverManager.registerDriver(fakeDriver);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        if (fakeDriver != null) {
            DriverManager.deregisterDriver(fakeDriver);
        }
    }

    /**
     * Constructs a real {@link MirthContextFactory} (BridgeLink's own Rhino seam), the same
     * technique proven in {@code RhinoSeamTest}: server/build.xml's testclasspath does not
     * include server/conf, so the committed server/conf/mirth.properties is made available on
     * the thread context classloader only for the duration of the one-time
     * JavaScriptScopeUtil/MirthContextFactory static init this constructor triggers.
     */
    private static MirthContextFactory createRealContextFactory() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        File confDir = new File("conf");
        URLClassLoader confLoader = null;
        try {
            if (confDir.isDirectory()) {
                URL confUrl = confDir.toURI().toURL();
                confLoader = new URLClassLoader(new URL[] { confUrl }, originalClassLoader);
                Thread.currentThread().setContextClassLoader(confLoader);
            }
            contextFactory = new MirthContextFactory(null, null, false);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            if (confLoader != null) {
                confLoader.close();
            }
        }
        return contextFactory;
    }

    private int numMessages = 0;

    @Test
    public final void testSqlNoUpdate() throws Exception {
        runTest(getDefaultProperties(false, DatabaseReceiverProperties.UPDATE_NEVER));
    }

    @Test
    public final void testSqlUpdateEach() throws Exception {
        DatabaseReceiverProperties properties = getDefaultProperties(false, DatabaseReceiverProperties.UPDATE_EACH);
        properties.getPollConnectorProperties().setPollingFrequency(300);
        runTest(properties, 1000, false);
    }

    @Test
    public final void testSqlUpdateOnce() throws Exception {
        runTest(getDefaultProperties(false, DatabaseReceiverProperties.UPDATE_ONCE));
    }

    @Test
    public final void testJavaScriptNoUpdate() throws Exception {
        runTest(getDefaultProperties(true, DatabaseReceiverProperties.UPDATE_NEVER));
    }

    @Test
    public final void testJavaScriptUpdateEach() throws Exception {
        runTest(getDefaultProperties(true, DatabaseReceiverProperties.UPDATE_EACH));
    }

    @Test
    public final void testJavaScriptUpdateOnce() throws Exception {
        runTest(getDefaultProperties(true, DatabaseReceiverProperties.UPDATE_ONCE));
    }

    @Test
    public final void testSourceQueueEnabled() throws Exception {
        runTest(getDefaultProperties(false, DatabaseReceiverProperties.UPDATE_ONCE), 300, true);
    }

    @Test
    public final void testCacheEnabled() throws Exception {
        DatabaseReceiverProperties properties = getDefaultProperties(false, DatabaseReceiverProperties.UPDATE_ONCE);
        properties.setCacheResults(true);
        runTest(properties);
    }

    @Test
    public final void testKeepConnectionOpenDisabled() throws Exception {
        DatabaseReceiverProperties properties = getDefaultProperties(false, DatabaseReceiverProperties.UPDATE_ONCE);
        properties.setKeepConnectionOpen(false);
        runTest(properties);
    }

    /*
     * Re-expressed (D-06 escape valve, see class javadoc): the original test executed two
     * semicolon-joined UPDATE statements as a single Postgres "simple query protocol" call and
     * verified the lastname side effect via a live SELECT. Here the same multi-statement SQL text
     * (still produced by the same ${...} extraction the production connector performs) is routed
     * through the fake seam's interpretMutation, which applies both the lastname and the
     * processed-flag mutation to the in-memory row -- preserving the "one execute() call updates
     * two columns for the row identified by the substituted parameter" behavior under test.
     */
    @Test
    public final void testMultipleStatements() throws Exception {
        final String testLastName = "newlastname";

        DatabaseReceiverProperties properties = getDefaultProperties(false, DatabaseReceiverProperties.UPDATE_EACH);
        properties.setUpdate("UPDATE " + TABLE + " SET lastname = '" + testLastName + "' WHERE mypatientid = ${mypatientid}; UPDATE " + TABLE + " SET processed = TRUE WHERE mypatientid = ${mypatientid};");
        runTest(properties);

        for (Map<String, Object> row : fakeDatabase.allRows()) {
            assertEquals(testLastName, row.get("lastname"));
        }
    }

    /*
     * Re-expressed (D-06 escape valve, see class javadoc): the original test created real
     * PL/pgSQL functions (select_messages()/update_messages()) and called them via SELECT. There
     * is no PL/pgSQL engine available without a live Postgres. The same select/update SQL text is
     * preserved unmodified and routed through the fake seam, which recognizes the
     * select_messages()/update_messages() call shape and serves a single-column ("mypatientid")
     * result set and a by-id update -- exercising the connector's single-column resultMap path
     * (distinct from the 5-column path the other tests exercise) plus the UPDATE_EACH dispatch,
     * without asserting real Postgres function-call side effects.
     */
    @Test
    public final void testStoredProcedures() throws Exception {
        DatabaseReceiverProperties properties = getDefaultProperties(false, DatabaseReceiverProperties.UPDATE_EACH);
        properties.setSelect("SELECT select_messages() AS mypatientid");
        properties.setUpdate("SELECT update_messages(${mypatientid})");
        runTest(properties);
    }

    @Test
    public final void testScriptReturnList() throws Exception {
        StringBuilder script = new StringBuilder();
        script.append("var results = new java.util.ArrayList();\n");

        script.append("var entry = new java.util.HashMap();\n");
        script.append("entry.put(\"mypatientid\", 1);\n");
        script.append("entry.put(\"lastname\", \"Rodriguez\");\n");
        script.append("entry.put(\"firstname\", \"Joe\");\n");
        script.append("entry.put(\"gender\", \"M\");\n");
        script.append("entry.put(\"dateofbirth\", \"2000-01-01\");\n");
        script.append("results.add(entry);\n");

        script.append("entry = new java.util.HashMap();\n");
        script.append("entry.put(\"mypatientid\", 1);\n");
        script.append("entry.put(\"lastname\", \"Farnsworth\");\n");
        script.append("entry.put(\"firstname\", \"Hubert\");\n");
        script.append("entry.put(\"gender\", \"M\");\n");
        script.append("entry.put(\"dateofbirth\", \"2000-01-01\");\n");
        script.append("results.add(entry);\n");

        script.append("entry = new java.util.HashMap();\n");
        script.append("entry.put(\"mypatientid\", 1);\n");
        script.append("entry.put(\"lastname\", \"Wong\");\n");
        script.append("entry.put(\"firstname\", \"Amy\");\n");
        script.append("entry.put(\"gender\", \"F\");\n");
        script.append("entry.put(\"dateofbirth\", \"2000-01-01\");\n");
        script.append("results.add(entry);\n");

        script.append("return results;\n");

        DatabaseReceiverProperties properties = getDefaultProperties(true, DatabaseReceiverProperties.UPDATE_NEVER);
        properties.setSelect(script.toString());
        runTest(properties);
    }

    private DatabaseReceiverProperties getDefaultProperties(boolean useScript, int updateMode) {
        DatabaseReceiverProperties properties = new DatabaseReceiverProperties();
        properties.setDriver(DB_DRIVER);
        properties.setUrl(DB_URL);
        properties.setUsername(DB_USERNAME);
        properties.setPassword(DB_PASSWORD);
        properties.setCacheResults(false);
        properties.setUseScript(useScript);
        properties.setUpdateMode(updateMode);
        // The connector's daily-interval trigger always computes its schedule anchored to "today
        // at midnight" (PollConnectorPropertiesAdvanced defaults to allDay), so the *next* regular
        // fire after "now" lands at a essentially-random phase within [0, pollingFrequency) --
        // there is no dedicated "first poll" concept in this trigger type. pollOnStart(true) is
        // the deterministic, misfire-independent way to get exactly one guaranteed poll
        // (Scheduler.triggerJob(), fired synchronously in scheduleJob()). A short pollingFrequency
        // would let that random-phase regular fire ALSO land inside runTest()'s short wait/settle
        // window, double-delivering rows in UPDATE_NEVER-mode tests where nothing marks a row
        // processed to stop it being re-selected; an hour-long one makes the chance of that
        // negligible (runTest()'s settle window is a few hundred ms). testSqlUpdateEach explicitly
        // overrides this to exercise repeated polling.
        properties.getPollConnectorProperties().setPollOnStart(true);
        properties.getPollConnectorProperties().setPollingFrequency(3600000);

        if (!useScript) {
            properties.setSelect("SELECT mypatientid, lastname, firstname, gender, dateofbirth FROM " + TABLE + " WHERE processed = FALSE");

            switch (updateMode) {
                case DatabaseReceiverProperties.UPDATE_EACH:
                    properties.setUpdate("UPDATE " + TABLE + " SET processed = TRUE WHERE mypatientid = ${mypatientid}");
                    break;

                case DatabaseReceiverProperties.UPDATE_ONCE:
                    properties.setUpdate("UPDATE " + TABLE + " SET processed = TRUE");
                    break;
            }
        } else {
            StringBuilder selectScript = new StringBuilder();
            selectScript.append("var dbConn = DatabaseConnectionFactory.createDatabaseConnection('" + DB_DRIVER + "','" + DB_URL + "','" + DB_USERNAME + "','" + DB_PASSWORD + "');\n");
            selectScript.append("var result = dbConn.executeCachedQuery(\"SELECT mypatientid, lastname, firstname, gender, dateofbirth FROM " + TABLE + " WHERE processed = FALSE\");\n");
            selectScript.append("dbConn.close();\n");
            selectScript.append("return result;\n");

            properties.setSelect(selectScript.toString());

            if (updateMode != DatabaseReceiverProperties.UPDATE_NEVER) {
                StringBuilder updateScript = new StringBuilder();
                updateScript.append("var dbConn = DatabaseConnectionFactory.createDatabaseConnection('" + DB_DRIVER + "','" + DB_URL + "','" + DB_USERNAME + "','" + DB_PASSWORD + "');\n");

                switch (updateMode) {
                    case DatabaseReceiverProperties.UPDATE_EACH:
                        updateScript.append("var params = new java.util.ArrayList();");
                        updateScript.append("params.add($('mypatientid'));");
                        updateScript.append("var result = dbConn.executeUpdate(\"UPDATE " + TABLE + " SET processed = TRUE WHERE mypatientid = ?\", params);");
                        break;

                    case DatabaseReceiverProperties.UPDATE_ONCE:
                        updateScript.append("var result = dbConn.executeUpdate(\"UPDATE " + TABLE + " SET processed = TRUE\");");
                        break;
                }

                updateScript.append("dbConn.close();");
                properties.setUpdate(updateScript.toString());
            }
        }

        return properties;
    }

    private void initTable() {
        fakeDatabase.reset();
        numMessages = 0;

        fakeDatabase.insertPatient("Rodriguez", "Joe", "M", new Date(Calendar.getInstance().getTimeInMillis()));
        numMessages++;

        fakeDatabase.insertPatient("Farnsworth", "Hubert", "M", new Date(Calendar.getInstance().getTimeInMillis()));
        numMessages++;

        fakeDatabase.insertPatient("Wong", "Amy", "F", new Date(Calendar.getInstance().getTimeInMillis()));
        numMessages++;
    }

    private void runTest(DatabaseReceiverProperties properties) throws Exception {
        runTest(properties, 300, false);
    }

    private void runTest(DatabaseReceiverProperties properties, long sleepMillis, boolean sourceQueueEnabled) throws Exception {
        initTable();

        testChannel = new TestChannel(TEST_CHANNEL_ID, TEST_SERVER_ID);
        // A real deploy (DonkeyEngineController.deployChannel) wires this; DummyChannel never
        // goes through that path, so finishDispatch()'s releaseProcessLock() would NPE on
        // respondAfterProcessing=true (lockAcquired=true) DispatchResults without it.
        testChannel.setProcessLock(new DefaultChannelProcessLock(1));

        DatabaseReceiver databaseReceiver = createDatabaseReceiver(properties);
        databaseReceiver.onDeploy();
        databaseReceiver.start();

        // Wait for the expected dispatch count (driven by Quartz's misfire catch-up poll, see
        // getDefaultProperties) rather than a blind sleep, then settle briefly before stopping so
        // a genuine extra poll still surfaces as a real assertion failure instead of being masked.
        waitForMessageCount(testChannel, numMessages, Math.max(sleepMillis, 3000));
        Thread.sleep(Math.min(sleepMillis, 200));

        databaseReceiver.stop();
        databaseReceiver.onUndeploy();

        assertEquals(numMessages, testChannel.getRawMessages().size());

        if (properties.getUpdateMode() != DatabaseReceiverProperties.UPDATE_NEVER) {
            assertEquals(numMessages, fakeDatabase.countProcessed());
            assertEquals(0, fakeDatabase.countUnprocessed());
        }
    }

    private static void waitForMessageCount(TestChannel channel, int expectedCount, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (channel.getRawMessages().size() < expectedCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
    }

    private DatabaseReceiver createDatabaseReceiver(DatabaseReceiverProperties properties) {
        DatabaseReceiver connector = new DatabaseReceiver();
        connector.setConnectorProperties(properties);
        initConnector(connector, 0);
        initSourceConnector(connector);
        return connector;
    }

    private void initConnector(Connector connector, Integer metaDataId) {
        connector.setChannelId(testChannel.getChannelId());
        connector.setMetaDataId(metaDataId);
    }

    private void initSourceConnector(SourceConnector sourceConnector) {
        sourceConnector.setChannelId(testChannel.getChannelId());
        sourceConnector.setChannel(testChannel);
        sourceConnector.setMetaDataReplacer(new MirthMetaDataReplacer());
        sourceConnector.setRespondAfterProcessing(true);
        sourceConnector.setFilterTransformerExecutor(new FilterTransformerExecutor(sourceConnector.getInboundDataType(), sourceConnector.getOutboundDataType()));

        testChannel.setSourceConnector(sourceConnector);
    }

    // ------------------------------------------------------------------------------------------
    // Fake JDBC seam: an in-memory "mypatients" table plus a java.sql.Driver registered with
    // DriverManager so the real DatabaseReceiverQuery/DatabaseReceiverScript/DatabaseConnection
    // production code (all of which ask DriverManager for a connection to DB_URL internally)
    // gets a working connection without a live PostgreSQL. See class javadoc.
    // ------------------------------------------------------------------------------------------

    /**
     * In-memory stand-in for the "mypatients" table. All mutation methods are synchronized since
     * DatabaseReceiver's poll loop and the JS Rhino execution thread pool touch this concurrently
     * with the JUnit test thread's assertions.
     */
    private static final class FakeDatabase {
        private final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        private int nextId = 1;

        synchronized void reset() {
            rows.clear();
            nextId = 1;
        }

        synchronized void insertPatient(String lastname, String firstname, String gender, Date dateOfBirth) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("mypatientid", Integer.valueOf(nextId++));
            row.put("lastname", lastname);
            row.put("firstname", firstname);
            row.put("gender", gender);
            row.put("dateofbirth", dateOfBirth);
            row.put("processed", Boolean.FALSE);
            rows.add(row);
        }

        synchronized List<Map<String, Object>> unprocessedRows() {
            List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> row : rows) {
                if (Boolean.FALSE.equals(row.get("processed"))) {
                    result.add(row);
                }
            }
            return result;
        }

        synchronized List<Map<String, Object>> allRows() {
            return new ArrayList<Map<String, Object>>(rows);
        }

        synchronized void markProcessed(Integer patientId) {
            if (patientId == null) {
                return;
            }
            for (Map<String, Object> row : rows) {
                if (((Number) row.get("mypatientid")).intValue() == patientId.intValue()) {
                    row.put("processed", Boolean.TRUE);
                }
            }
        }

        synchronized void markAllProcessed() {
            for (Map<String, Object> row : rows) {
                row.put("processed", Boolean.TRUE);
            }
        }

        synchronized void setLastname(Integer patientId, String lastname) {
            if (patientId == null) {
                return;
            }
            for (Map<String, Object> row : rows) {
                if (((Number) row.get("mypatientid")).intValue() == patientId.intValue()) {
                    row.put("lastname", lastname);
                }
            }
        }

        synchronized long countProcessed() {
            long count = 0;
            for (Map<String, Object> row : rows) {
                if (Boolean.TRUE.equals(row.get("processed"))) {
                    count++;
                }
            }
            return count;
        }

        synchronized long countUnprocessed() {
            return rows.size() - countProcessed();
        }
    }

    private static final List<String> ALL_COLUMNS = Arrays.asList("mypatientid", "lastname", "firstname", "gender", "dateofbirth");
    private static final Pattern LASTNAME_LITERAL_PATTERN = Pattern.compile("(?i)lastname\\s*=\\s*'([^']*)'");

    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        return Integer.valueOf(Integer.parseInt(String.valueOf(value)));
    }

    /**
     * Interprets a SELECT statement's SQL text against {@link #fakeDatabase} and returns a fully
     * mocked {@link ResultSet}/{@link ResultSetMetaData} pair. The metadata is stubbed
     * comprehensively (not just getColumnCount/getColumnLabel) because the cacheResults=true path
     * populates a real {@code javax.sql.rowset.CachedRowSet} (JDK's CachedRowSetImpl) from this
     * result set, which walks the full ResultSetMetaData contract.
     */
    private static ResultSet interpretSelect(String sql) throws SQLException {
        String upper = sql.toUpperCase(Locale.ENGLISH);
        List<Map<String, Object>> unprocessed = fakeDatabase.unprocessedRows();

        if (upper.contains("SELECT_MESSAGES")) {
            // Stored-procedure-shaped select (testStoredProcedures): single-column result.
            return buildResultSet(unprocessed, Collections.singletonList("mypatientid"));
        }

        return buildResultSet(unprocessed, ALL_COLUMNS);
    }

    /**
     * Interprets an UPDATE/mutation SQL statement (optionally multi-statement, semicolon-joined)
     * against {@link #fakeDatabase} using the bound parameters captured by the fake
     * PreparedStatement/Statement. Returns an update count.
     */
    private static int interpretMutation(String sql, Map<Integer, Object> params) {
        String upper = sql.toUpperCase(Locale.ENGLISH);
        Integer firstParam = params.isEmpty() ? null : toInt(params.get(1));

        if (upper.contains("UPDATE_MESSAGES")) {
            // Stored-procedure-shaped update (testStoredProcedures): mark the given id processed.
            fakeDatabase.markProcessed(firstParam);
            return 1;
        }

        boolean applied = false;

        if (upper.contains("SET LASTNAME")) {
            Matcher matcher = LASTNAME_LITERAL_PATTERN.matcher(sql);
            String literal = matcher.find() ? matcher.group(1) : null;
            fakeDatabase.setLastname(firstParam, literal);
            applied = true;
        }

        if (upper.contains("SET PROCESSED = TRUE")) {
            if (upper.contains("WHERE MYPATIENTID")) {
                fakeDatabase.markProcessed(firstParam);
            } else {
                fakeDatabase.markAllProcessed();
            }
            applied = true;
        }

        return applied ? 1 : 0;
    }

    private static ResultSet buildResultSet(List<Map<String, Object>> sourceRows, List<String> columns) throws SQLException {
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        for (int i = 0; i < columns.size(); i++) {
            int col = i + 1;
            String name = columns.get(i);
            when(metaData.getColumnLabel(col)).thenReturn(name);
            when(metaData.getColumnName(col)).thenReturn(name);
            when(metaData.isAutoIncrement(col)).thenReturn(false);
            when(metaData.isCaseSensitive(col)).thenReturn(true);
            when(metaData.isCurrency(col)).thenReturn(false);
            when(metaData.isNullable(col)).thenReturn(ResultSetMetaData.columnNullable);
            when(metaData.isSigned(col)).thenReturn("mypatientid".equals(name));
            when(metaData.isSearchable(col)).thenReturn(true);
            when(metaData.getColumnDisplaySize(col)).thenReturn(255);
            when(metaData.getSchemaName(col)).thenReturn("");
            when(metaData.getPrecision(col)).thenReturn(0);
            when(metaData.getScale(col)).thenReturn(0);
            when(metaData.getTableName(col)).thenReturn(TABLE);
            when(metaData.getCatalogName(col)).thenReturn("");

            if ("mypatientid".equals(name)) {
                when(metaData.getColumnType(col)).thenReturn(Types.INTEGER);
                when(metaData.getColumnTypeName(col)).thenReturn("int4");
            } else if ("dateofbirth".equals(name)) {
                when(metaData.getColumnType(col)).thenReturn(Types.DATE);
                when(metaData.getColumnTypeName(col)).thenReturn("date");
            } else {
                when(metaData.getColumnType(col)).thenReturn(Types.VARCHAR);
                when(metaData.getColumnTypeName(col)).thenReturn("varchar");
            }
        }
        when(metaData.getColumnCount()).thenReturn(columns.size());

        // Snapshot so a later mutation (e.g. markProcessed while this result set is still open)
        // doesn't change rows out from under an in-flight iteration.
        final List<Map<String, Object>> snapshot = new ArrayList<Map<String, Object>>(sourceRows.size());
        for (Map<String, Object> row : sourceRows) {
            snapshot.add(new LinkedHashMap<String, Object>(row));
        }

        ResultSet resultSet = mock(ResultSet.class);
        final AtomicInteger cursor = new AtomicInteger(-1);
        final List<String> columnsRef = columns;
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(resultSet.next()).thenAnswer(invocation -> cursor.incrementAndGet() < snapshot.size());
        when(resultSet.getObject(anyInt())).thenAnswer(invocation -> {
            int idx = invocation.getArgument(0);
            String columnName = columnsRef.get(idx - 1);
            return snapshot.get(cursor.get()).get(columnName);
        });
        when(resultSet.isClosed()).thenReturn(false);
        doNothing().when(resultSet).close();

        return resultSet;
    }

    private static PreparedStatement newPreparedStatement(String sql) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        final Map<Integer, Object> params = new HashMap<Integer, Object>();

        doAnswer(invocation -> {
            params.put((Integer) invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(statement).setObject(anyInt(), any());
        doAnswer(invocation -> {
            params.put((Integer) invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(statement).setString(anyInt(), anyString());
        doAnswer(invocation -> {
            params.put((Integer) invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(statement).setDate(anyInt(), any());
        doNothing().when(statement).setFetchSize(anyInt());
        doNothing().when(statement).close();

        when(statement.executeQuery()).thenAnswer(invocation -> interpretSelect(sql));
        when(statement.execute()).thenAnswer(invocation -> {
            interpretMutation(sql, params);
            return false;
        });
        when(statement.executeUpdate()).thenAnswer(invocation -> interpretMutation(sql, params));
        when(statement.getUpdateCount()).thenReturn(1);

        return statement;
    }

    private static Statement newStatement() throws SQLException {
        Statement statement = mock(Statement.class);

        when(statement.executeQuery(anyString())).thenAnswer(invocation -> interpretSelect((String) invocation.getArgument(0)));
        when(statement.execute(anyString())).thenAnswer(invocation -> {
            interpretMutation((String) invocation.getArgument(0), Collections.<Integer, Object> emptyMap());
            return false;
        });
        when(statement.executeUpdate(anyString())).thenAnswer(invocation -> interpretMutation((String) invocation.getArgument(0), Collections.<Integer, Object> emptyMap()));
        when(statement.getUpdateCount()).thenReturn(1);
        doNothing().when(statement).close();

        return statement;
    }

    private static Connection newConnection() throws SQLException {
        Connection connection = mock(Connection.class);
        doNothing().when(connection).setAutoCommit(anyBoolean());
        when(connection.isClosed()).thenReturn(false);
        doNothing().when(connection).close();
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> newPreparedStatement((String) invocation.getArgument(0)));
        when(connection.createStatement()).thenAnswer(invocation -> newStatement());
        return connection;
    }

    /**
     * Registered with DriverManager in {@code setUpBeforeClass}. Since the real
     * {@code org.postgresql.Driver} is also on the classpath (and gets registered too, either via
     * JDBC 4 auto-loading or {@code Class.forName}), DriverManager tries it first when asked for a
     * connection, gets a connection-refused SQLException (no live Postgres), catches it
     * internally, and falls through to this driver -- which always succeeds. See the
     * DriverManager connection-lookup javadoc: a per-driver SQLException is swallowed and the
     * next registered driver is tried.
     */
    private static final class FakeDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (url == null || !url.startsWith("jdbc:postgresql:")) {
                return null;
            }
            return newConnection();
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return url != null && url.startsWith("jdbc:postgresql:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }
}
