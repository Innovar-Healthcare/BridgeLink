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
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.ContentType;
import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.ResponseTransformerExecutor;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.data.DonkeyDao;
import com.mirth.connect.donkey.server.message.DataType;
import com.mirth.connect.donkey.server.data.passthru.PassthruDaoFactory;
import com.mirth.connect.donkey.server.event.EventDispatcher;
import com.mirth.connect.donkey.server.queue.ConnectorMessageQueueDataSource;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.server.channel.MirthMetaDataReplacer;
import com.mirth.connect.server.controllers.ChannelController;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ContextFactoryController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.controllers.ExtensionController;
import com.mirth.connect.server.util.javascript.MirthContextFactory;

/**
 * Repaired from the live-PostgreSQL/full-server-boot test (26.10-03 rename) onto the mocked JDBC
 * seam proven by {@link DatabaseReceiverIntegrationTest} (26.10-04, D-06): a test-registered
 * {@link java.sql.Driver} intercepts DriverManager's connection lookup for {@link #DB_URL} and
 * vends Mockito-mocked Connection/PreparedStatement objects backed by an in-memory
 * {@link FakeDatabase} (two tables, mirroring TABLE1/TABLE2), so the real
 * DatabaseDispatcher/DatabaseDispatcherQuery/DatabaseDispatcherScript production code runs
 * unmodified end to end (INSERT dispatch, parameter substitution) without a live database or a
 * full server boot. testScript drives a
 * real Rhino {@link MirthContextFactory} (the same seam RhinoSeamTest proves) so the
 * DatabaseConnectionFactory call the script text makes also routes through the fake driver.
 * <p>
 * testStoredProcedure is re-expressed (D-06 escape valve): there is no PL/pgSQL engine available
 * without a live Postgres, so the same {@code SELECT store_message(...)} SQL text is routed
 * through the fake seam's pattern matching, which performs the equivalent single-row insert the
 * function body would have -- preserving the "stored-procedure-shaped SELECT still dispatches a
 * write" behavior under test. See 26.10-04-SUMMARY.md for the full deviation record.
 */
public class DatabaseDispatcherTest {
    final public static String TEST_HL7_MESSAGE = "MSH|^~\\&|LABNET|Acme Labs|||20090601105700||ORU^R01|HMCDOOGAL-0088|D|2.2\rPID|1|8890088|8890088^^^72777||McDoogal^Hattie^||19350118|F||2106-3|100 Beach Drive^Apt. 5^Mission Viejo^CA^92691^US^H||(949) 555-0025|||||8890088^^^72|604422825\rPV1|1|R|C3E^C315^B||||2^HIBBARD^JULIUS^|5^ZIMMERMAN^JOE^|9^ZOIDBERG^JOHN^|CAR||||4|||2301^OBRIEN, KEVIN C|I|1783332658^1^1||||||||||||||||||||DISNEY CLINIC||N|||20090514205600\rORC|RE|928272608|056696716^LA||CM||||20090601105600||||  C3E|||^RESULT PERFORMED\rOBR|1|928272608|056696716^LA|1001520^K|||20090601101300|||MLH25|||HEMOLYZED/VP REDRAW|20090601102400||2301^OBRIEN, KEVIN C||||01123085310001100100152023509915823509915800000000101|0000915200932|20090601105600||LAB|F||^^^20090601084100^^ST~^^^^^ST\rOBX|1|NM|1001520^K||5.3|MMOL/L|3.5-5.5||||F|||20090601105600|IIM|IIM\r";

    private final static String DB_DRIVER = "org.postgresql.Driver";
    private final static String DB_URL = "jdbc:postgresql://localhost:5432/mirthdb";
    private final static String DB_USERNAME = "";
    private final static String DB_PASSWORD = "";
    private final static String TABLE1 = "mypatients_destination1";
    private final static String TABLE2 = "mypatients_destination2";
    private final static String TEST_CHANNEL_ID = "testchannel";
    private final static String TEST_SERVER_ID = "testserver";

    private static MirthContextFactory contextFactory;
    private static FakeDriver fakeDriver;
    private static final FakeDatabase fakeDatabase = new FakeDatabase();

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

        // DatabaseDispatcher.replaceConnectorProperties/send's error path look up the deployed
        // channel's name via ChannelController.getInstance(); a real server boot wires this.
        Channel deployedChannel = new Channel();
        deployedChannel.setId(TEST_CHANNEL_ID);
        deployedChannel.setName("test channel");
        ChannelController channelController = mock(ChannelController.class);
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

    @Test
    public final void testSql() throws Exception {
        runTest(getDefaultProperties(false));
    }

    @Test
    public final void testScript() throws Exception {
        runTest(getDefaultProperties(true));
    }

    /*
     * The multi-statement half is preserved unmodified in intent: the same two semicolon-joined
     * INSERTs (still produced by the same ${...} extraction the production connector performs)
     * are routed through the fake seam's interpretMutation, which applies both inserts to their
     * respective in-memory tables from the single execute() call, exercising the "one execute()
     * touches two tables" behavior the original live-Postgres "simple query protocol" call
     * exercised.
     */
    @Test
    public final void testMultipleStatements() throws Exception {
        // @formatter:off
        String query = "INSERT INTO " + TABLE1 + " (mypatientid, lastname, firstname, gender, dateofbirth) VALUES (${mypatientid}::integer, ${lastname}, ${firstname}, ${gender}, ${dateofbirth}::date);"
                     + "INSERT INTO " + TABLE2 + " (mypatientid, lastname, firstname, gender, dateofbirth) VALUES (${mypatientid}::integer, ${lastname}, ${firstname}, ${gender}, ${dateofbirth}::date);";
        // @formatter:on

        DatabaseDispatcherProperties properties = getDefaultProperties(false);
        properties.setQuery(query);

        List<String> tables = new ArrayList<String>();
        tables.add(TABLE1);
        tables.add(TABLE2);

        runTest(properties, tables);
    }

    /*
     * Re-expressed (D-06 escape valve, see class javadoc): no PL/pgSQL engine is available
     * without a live Postgres. The same select/parameter-substitution SQL text is preserved
     * unmodified and routed through the fake seam, which recognizes the store_message(...) call
     * shape and performs the same single-row insert into TABLE1 the function body would have.
     */
    @Test
    public final void testStoredProcedure() throws Exception {
        DatabaseDispatcherProperties properties = getDefaultProperties(false);
        properties.setQuery("SELECT store_message(${mypatientid}::integer, ${lastname}, ${firstname}, ${gender}, ${dateofbirth}::date)");
        runTest(properties);
    }

    private DatabaseDispatcherProperties getDefaultProperties(boolean useScript) {
        DatabaseDispatcherProperties properties = new DatabaseDispatcherProperties();
        properties.setDriver(DB_DRIVER);
        properties.setUrl(DB_URL);
        properties.setUsername(DB_USERNAME);
        properties.setPassword(DB_PASSWORD);
        properties.setUseScript(useScript);

        if (!useScript) {
            properties.setQuery("INSERT INTO " + TABLE1 + " (mypatientid, lastname, firstname, gender, dateofbirth) VALUES (${mypatientid}::integer, ${lastname}, ${firstname}, ${gender}, ${dateofbirth}::date)");
        } else {
            StringBuilder script = new StringBuilder();
            script.append("var dbConn = DatabaseConnectionFactory.createDatabaseConnection('" + DB_DRIVER + "','" + DB_URL + "','" + DB_USERNAME + "','" + DB_PASSWORD + "');\n");

            script.append("var params = new java.util.ArrayList();\n");
            script.append("params.add($('mypatientid'));\n");
            script.append("params.add($('lastname'));\n");
            script.append("params.add($('firstname'));\n");
            script.append("params.add($('gender'));\n");
            script.append("params.add($('dateofbirth'));\n");

            script.append("var result = dbConn.executeUpdate(\"INSERT INTO " + TABLE1 + " (mypatientid, lastname, firstname, gender, dateofbirth) VALUES (?::integer, ?, ?, ?, ?::date)\", params);\n");
            script.append("dbConn.close();\n");

            properties.setQuery(script.toString());
        }

        return properties;
    }

    private void initTables() {
        fakeDatabase.reset();
    }

    private void runTest(DatabaseDispatcherProperties properties) throws Exception {
        List<String> tables = new ArrayList<String>();
        tables.add(TABLE1);
        runTest(properties, tables);
    }

    private void runTest(DatabaseDispatcherProperties properties, List<String> tables) throws Exception {
        final int numMessages = 3;

        initTables();

        Channel channel = new Channel();
        channel.setId(TEST_CHANNEL_ID);
        channel.setName("test channel");
        ChannelController.getInstance().putDeployedChannelInCache(channel);

        DonkeyDao dao = new PassthruDaoFactory().getDao();

        DestinationConnector databaseDispatcher = new TestDatabaseDispatcher(TEST_CHANNEL_ID, TEST_SERVER_ID, 1, properties);
        databaseDispatcher.onDeploy();
        databaseDispatcher.start();

        long messageIdSequence = 1;

        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();

        Map<String, String> map = new HashMap<String, String>();
        map.put("mypatientid", "1");
        map.put("firstname", "Joe");
        map.put("lastname", "Rodriguez");
        map.put("gender", "M");
        map.put("dateofbirth", "1935-01-18");
        messages.add(map);

        map = new HashMap<String, String>();
        map.put("mypatientid", "2");
        map.put("firstname", "Hubert");
        map.put("lastname", "Farnsworth");
        map.put("gender", "M");
        map.put("dateofbirth", "1935-01-18");
        messages.add(map);

        map = new HashMap<String, String>();
        map.put("mypatientid", "3");
        map.put("firstname", "Amy");
        map.put("lastname", "Wong");
        map.put("gender", "F");
        map.put("dateofbirth", "1935-01-18");
        messages.add(map);

        for (int i = 0; i < numMessages; i++) {
            ConnectorMessage message = new ConnectorMessage();
            message.setMessageId(messageIdSequence++);
            message.setChannelId(TEST_CHANNEL_ID);
            message.setChainId(1);
            message.setServerId(TEST_SERVER_ID);

            MessageContent rawContent = new MessageContent(message.getChannelId(), message.getMessageId(), message.getMetaDataId(), ContentType.RAW, TEST_HL7_MESSAGE, "HL7", false);
            MessageContent encodedContent = SerializationUtils.clone(rawContent);
            encodedContent.setContentType(ContentType.ENCODED);

            message.setRaw(rawContent);
            message.setEncoded(encodedContent);
            message.getChannelMap().putAll(messages.get(i));
            message.setStatus(Status.TRANSFORMED);

            databaseDispatcher.process(dao, message, Status.RECEIVED);
        }

        databaseDispatcher.stop();
        databaseDispatcher.onUndeploy();
        dao.close();

        for (String table : tables) {
            List<Map<String, Object>> rows = fakeDatabase.rows(table);
            int i = 0;

            for (Map<String, Object> row : rows) {
                map = messages.get(i++);
                assertEquals(map.get("mypatientid"), String.valueOf(row.get("mypatientid")));
                assertEquals(map.get("firstname"), row.get("firstname"));
                assertEquals(map.get("lastname"), row.get("lastname"));
                assertEquals(map.get("gender"), row.get("gender"));
                assertEquals(map.get("dateofbirth"), row.get("dateofbirth"));
            }

            assertEquals(messages.size(), i);
        }
    }

    private class TestDatabaseDispatcher extends DatabaseDispatcher {
        public TestDatabaseDispatcher(String channelId, String serverId, Integer metaDataId, DatabaseDispatcherProperties properties) {
            super();
            setChannelId(channelId);
            setMetaDataId(metaDataId);
            setConnectorProperties(properties);

            // A real deploy resolves this via the Channel-driven DestinationChain; here we set it
            // directly since DatabaseDispatcher.replaceConnectorProperties() calls
            // getChannel().getName() to resolve ${...} placeholders. getEventDispatcher() is
            // overridden because DummyChannel does not implement it (normally wired by a real
            // server boot) and DestinationConnector.start() dispatches a status event through it.
            com.mirth.connect.donkey.server.channel.Channel donkeyChannel = new com.mirth.connect.server.TestUtils.DummyChannel(channelId, serverId) {
                @Override
                protected EventDispatcher getEventDispatcher() {
                    return event -> {
                    };
                }
            };
            donkeyChannel.setName("test channel");
            // afterSend() also reaches into channel.getSourceConnector().getMetaDataReplacer();
            // a real deploy wires the source connector, DummyChannel does not.
            SourceConnector sourceConnector = new SourceConnector() {
                @Override
                public void handleRecoveredResponse(DispatchResult dispatchResult) {
                }

                @Override
                public void onDeploy() throws ConnectorTaskException {
                }

                @Override
                public void onUndeploy() throws ConnectorTaskException {
                }

                @Override
                public void onStart() throws ConnectorTaskException {
                }

                @Override
                public void onStop() throws ConnectorTaskException {
                }

                @Override
                public void onHalt() throws ConnectorTaskException {
                }
            };
            sourceConnector.setMetaDataReplacer(new MirthMetaDataReplacer());
            donkeyChannel.setSourceConnector(sourceConnector);
            setChannel(donkeyChannel);

            // A real deploy (DestinationChain construction) wires both of these; afterSend()
            // dereferences responseTransformerExecutor.getInbound().getType() unconditionally
            // when storageSettings.isStoreResponse() is true (the StorageSettings default).
            setInboundDataType(new DataType("XML", null, null));
            setOutboundDataType(new DataType("XML", null, null));
            setResponseTransformerExecutor(new ResponseTransformerExecutor(getInboundDataType(), getOutboundDataType()));

            if (properties.getDestinationConnectorProperties().isQueueEnabled()) {
                getQueue().setDataSource(new ConnectorMessageQueueDataSource(channelId, serverId, metaDataId, Status.QUEUED, isQueueRotate(), new PassthruDaoFactory()));
                getQueue().updateSize();
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Fake JDBC seam: two in-memory "mypatients_destinationN" tables plus a java.sql.Driver
    // registered with DriverManager so the real DatabaseDispatcherQuery/DatabaseDispatcherScript/
    // DatabaseConnection production code (which all ask DriverManager for a connection to DB_URL
    // internally) gets a working connection without a live PostgreSQL. See class javadoc.
    // ------------------------------------------------------------------------------------------

    private static final class FakeDatabase {
        private final Map<String, List<Map<String, Object>>> tables = new HashMap<String, List<Map<String, Object>>>();

        synchronized void reset() {
            tables.clear();
            tables.put(TABLE1, new CopyOnWriteArrayList<Map<String, Object>>());
            tables.put(TABLE2, new CopyOnWriteArrayList<Map<String, Object>>());
        }

        synchronized void insert(String table, Object mypatientid, Object lastname, Object firstname, Object gender, Object dateofbirth) {
            List<Map<String, Object>> rows = tables.get(table);
            if (rows == null) {
                rows = new CopyOnWriteArrayList<Map<String, Object>>();
                tables.put(table, rows);
            }
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("mypatientid", mypatientid);
            row.put("lastname", lastname);
            row.put("firstname", firstname);
            row.put("gender", gender);
            row.put("dateofbirth", dateofbirth);
            rows.add(row);
        }

        synchronized List<Map<String, Object>> rows(String table) {
            List<Map<String, Object>> rows = tables.get(table);
            return rows == null ? new ArrayList<Map<String, Object>>() : new ArrayList<Map<String, Object>>(rows);
        }
    }

    /**
     * Interprets an INSERT (optionally multi-statement, semicolon-joined) or stored-procedure-
     * shaped SELECT against {@link #fakeDatabase}, using the bound parameter array (5 values per
     * statement, in mypatientid/lastname/firstname/gender/dateofbirth order -- the same order
     * every query in this file lists its columns/call args in).
     */
    private static void interpretMutation(String sql, Object[] params) {
        String upper = sql.toUpperCase(Locale.ENGLISH);

        if (upper.contains("STORE_MESSAGE")) {
            insertFromParams(TABLE1, params, 0);
            return;
        }

        int paramIndex = 0;
        if (upper.contains(TABLE1.toUpperCase(Locale.ENGLISH))) {
            paramIndex = insertFromParams(TABLE1, params, paramIndex);
        }
        if (upper.contains(TABLE2.toUpperCase(Locale.ENGLISH))) {
            insertFromParams(TABLE2, params, paramIndex);
        }
    }

    private static int insertFromParams(String table, Object[] params, int startIndex) {
        if (params.length < startIndex + 5) {
            return startIndex;
        }
        fakeDatabase.insert(table, params[startIndex], params[startIndex + 1], params[startIndex + 2], params[startIndex + 3], params[startIndex + 4]);
        return startIndex + 5;
    }

    private static PreparedStatement newPreparedStatement(String sql) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        final Map<Integer, Object> params = new HashMap<Integer, Object>();

        doAnswer(invocation -> {
            params.put((Integer) invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(statement).setObject(anyInt(), any());
        doNothing().when(statement).close();

        when(statement.execute()).thenAnswer(invocation -> {
            interpretMutation(sql, toOrderedArray(params));
            return false;
        });
        when(statement.executeUpdate()).thenAnswer(invocation -> {
            interpretMutation(sql, toOrderedArray(params));
            return 1;
        });
        when(statement.getUpdateCount()).thenReturn(1);

        return statement;
    }

    private static Object[] toOrderedArray(Map<Integer, Object> params) {
        int max = 0;
        for (Integer key : params.keySet()) {
            max = Math.max(max, key);
        }
        Object[] result = new Object[max];
        for (Map.Entry<Integer, Object> entry : params.entrySet()) {
            result[entry.getKey() - 1] = entry.getValue();
        }
        return result;
    }

    private static Connection newConnection() throws SQLException {
        Connection connection = mock(Connection.class);
        doNothing().when(connection).setAutoCommit(anyBoolean());
        when(connection.isClosed()).thenReturn(false);
        doNothing().when(connection).close();
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> newPreparedStatement((String) invocation.getArgument(0)));
        return connection;
    }

    /**
     * Registered with DriverManager in setUpBeforeClass. The real org.postgresql.Driver is also
     * on the classpath and gets tried first when a connection is requested, fails with a
     * connection-refused SQLException (no live Postgres), and DriverManager falls through to this
     * driver, which always succeeds.
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
