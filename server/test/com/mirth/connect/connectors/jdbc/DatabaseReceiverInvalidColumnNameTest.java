/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.jdbc;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.channel.ChannelException;
import com.mirth.connect.donkey.server.channel.DefaultChannelProcessLock;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.FilterTransformerExecutor;
import com.mirth.connect.donkey.server.event.EventDispatcher;
import com.mirth.connect.server.TestUtils.DummyChannel;
import com.mirth.connect.server.channel.MirthMetaDataReplacer;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ContextFactoryController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.controllers.ExtensionController;
import com.mirth.connect.server.util.javascript.MirthContextFactory;

/**
 * Repaired from the live-PostgreSQL test (26.10-03 rename) onto the same mocked-JDBC-seam pattern
 * used by {@link DatabaseReceiverIntegrationTest} (26.10-04, D-06): this class never asked
 * DriverManager for a connection or booted a server itself (it only mocked ControllerFactory and
 * constructed a real MirthContextFactory), but the production DatabaseReceiverQuery code it
 * exercises still asked DriverManager for a connection to DB_URL internally, which failed
 * against a live Postgres that no longer exists in this environment. A test-registered
 * {@link java.sql.Driver} now intercepts that call and vends a Mockito-mocked ResultSet whose
 * column names carry the same invalid-XML-element-name characters the original live
 * "invalidchartest" table exercised (".abc", "ab c", "[def]"), so
 * {@link DatabaseReceiver#fixColumnName} and {@link DatabaseReceiver#resultMapToXml} run against
 * real invalid-name data without a live database.
 */
public class DatabaseReceiverInvalidColumnNameTest {

    private final static String DB_DRIVER = "org.postgresql.Driver";
    private final static String DB_URL = "jdbc:postgresql://localhost:5432/test";
    private final static String DB_USERNAME = "";
    private final static String DB_PASSWORD = "";
    private final static String TEST_CHANNEL_ID = "testchannel";
    private final static String TEST_SERVER_ID = "testserver";

    private static FakeDriver fakeDriver;

    @BeforeClass
    public static void setupControllers() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        EventController eventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(eventController);

        ConfigurationController configurationController = mock(ConfigurationController.class);
        when(configurationController.getRhinoLanguageVersion()).thenReturn(org.mozilla.javascript.Context.VERSION_ES6);
        when(configurationController.getServerVersion()).thenReturn("26.9.0-jdbc-repair-test");
        when(configurationController.getServerId()).thenReturn(TEST_SERVER_ID);
        when(configurationController.getConfigurationMap()).thenReturn(new java.util.HashMap<String, String>());
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        ExtensionController extensionController = mock(ExtensionController.class);
        when(controllerFactory.createExtensionController()).thenReturn(extensionController);

        ContextFactoryController contextFactoryController = mock(ContextFactoryController.class);
        when(contextFactoryController.getContextFactory(any())).thenReturn(createRealContextFactory());
        when(controllerFactory.createContextFactoryController()).thenReturn(contextFactoryController);

        // DatabaseReceiver.poll()'s error-path logging looks up the deployed channel's name via
        // ChannelController.getInstance(); a real server boot wires this, so it needs a mock here.
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

    private static MirthContextFactory contextFactory;

    /**
     * Constructs a real MirthContextFactory (BridgeLink's own Rhino seam), the same technique
     * RhinoSeamTest proves: server/build.xml's testclasspath does not include server/conf, so the
     * committed server/conf/mirth.properties is made available on the thread context classloader
     * only for the duration of the one-time JavaScriptScopeUtil/MirthContextFactory/
     * DefaultConfigurationController static init this constructor triggers. Without this, the
     * constructor throws ExceptionInInitializerError ("could not load [mirth.properties] as a
     * classloader resource") under the real ant test-run (server/conf is not on that classpath),
     * even though it can appear to work under an ad hoc classpath that happens to include conf/.
     */
    private static MirthContextFactory createRealContextFactory() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        java.io.File confDir = new java.io.File("conf");
        java.net.URLClassLoader confLoader = null;
        try {
            if (confDir.isDirectory()) {
                java.net.URL confUrl = confDir.toURI().toURL();
                confLoader = new java.net.URLClassLoader(new java.net.URL[] { confUrl }, originalClassLoader);
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

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        if (fakeDriver != null) {
            DriverManager.deregisterDriver(fakeDriver);
        }
    }

    @Test
    public void test() throws Exception {
        /**
         * Create a table invalidchartest with column names that don't have valid XML characters.
         *
         * Examples: .abc [def]
         */

        DatabaseReceiverProperties properties = new DatabaseReceiverProperties();
        properties.setDriver(DB_DRIVER);
        properties.setUrl(DB_URL);
        properties.setUsername(DB_USERNAME);
        properties.setPassword(DB_PASSWORD);
        properties.setSelect("SELECT * FROM invalidchartest");
        properties.getPollConnectorProperties().setPollOnStart(true);

        TestChannel testChannel = new TestChannel();
        // A real deploy (DonkeyEngineController.deployChannel) wires this; DummyChannel never
        // goes through that path, so finishDispatch()'s releaseProcessLock() would NPE on
        // respondAfterProcessing=true (lockAcquired=true) DispatchResults without it.
        testChannel.setProcessLock(new DefaultChannelProcessLock(1));

        DatabaseReceiver connector = new DatabaseReceiver();
        connector.setConnectorProperties(properties);
        connector.setChannelId(testChannel.getChannelId());
        connector.setChannel(testChannel);
        connector.setMetaDataId(0);
        connector.setMetaDataReplacer(new MirthMetaDataReplacer());
        connector.setRespondAfterProcessing(true);
        connector.setFilterTransformerExecutor(new FilterTransformerExecutor(connector.getInboundDataType(), connector.getOutboundDataType()));
        testChannel.setSourceConnector(connector);

        connector.onDeploy();
        connector.start();

        // The poll connector dispatches asynchronously, so wait for the first message rather than
        // sleeping a fixed interval and hoping the poll completed: under parallel forked-JVM CI load
        // a fixed 1s window races the poll thread and fails intermittently (observed on the JDK 17
        // shard). Poll the condition up to a generous timeout instead.
        long deadline = System.currentTimeMillis() + 15000;
        while (testChannel.messages.size() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        connector.stop();
        connector.onUndeploy();

        assertTrue(testChannel.messages.size() > 0);
    }

    private class TestChannel extends DummyChannel {

        private List<RawMessage> messages = new ArrayList<RawMessage>();

        public TestChannel() {
            super(TEST_CHANNEL_ID, TEST_SERVER_ID);
        }

        @Override
        protected EventDispatcher getEventDispatcher() {
            return e -> {
            };
        }

        @Override
        protected DispatchResult dispatchRawMessage(RawMessage rawMessage, boolean batch) throws ChannelException {
            messages.add(rawMessage);
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Fake JDBC seam: a single-row "invalidchartest" result set with column names carrying
    // invalid XML element-name characters, plus a java.sql.Driver registered with DriverManager
    // so DatabaseReceiverQuery's internal connection request to DB_URL succeeds
    // without a live PostgreSQL. See class javadoc.
    // ------------------------------------------------------------------------------------------

    private static ResultSet buildInvalidColumnResultSet() throws SQLException {
        List<String> columns = new ArrayList<String>();
        columns.add(".abc");
        columns.add("ab c");
        columns.add("[def]");
        columns.add("validcolumn");

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
            when(metaData.isSigned(col)).thenReturn(false);
            when(metaData.isSearchable(col)).thenReturn(true);
            when(metaData.getColumnDisplaySize(col)).thenReturn(255);
            when(metaData.getSchemaName(col)).thenReturn("");
            when(metaData.getPrecision(col)).thenReturn(0);
            when(metaData.getScale(col)).thenReturn(0);
            when(metaData.getTableName(col)).thenReturn("invalidchartest");
            when(metaData.getCatalogName(col)).thenReturn("");
            when(metaData.getColumnType(col)).thenReturn(Types.VARCHAR);
            when(metaData.getColumnTypeName(col)).thenReturn("varchar");
        }
        when(metaData.getColumnCount()).thenReturn(columns.size());

        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put(".abc", "value1");
        row.put("ab c", "value2");
        row.put("[def]", "value3");
        row.put("validcolumn", "value4");

        ResultSet resultSet = mock(ResultSet.class);
        final AtomicInteger cursor = new AtomicInteger(-1);
        final List<String> columnsRef = columns;
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(resultSet.next()).thenAnswer(invocation -> cursor.incrementAndGet() < 1);
        when(resultSet.getObject(anyInt())).thenAnswer(invocation -> {
            int idx = invocation.getArgument(0);
            return row.get(columnsRef.get(idx - 1));
        });
        when(resultSet.isClosed()).thenReturn(false);
        doNothing().when(resultSet).close();

        return resultSet;
    }

    private static PreparedStatement newPreparedStatement() throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        doNothing().when(statement).setFetchSize(anyInt());
        doNothing().when(statement).close();
        when(statement.executeQuery()).thenAnswer(invocation -> buildInvalidColumnResultSet());
        return statement;
    }

    private static Connection newConnection() throws SQLException {
        Connection connection = mock(Connection.class);
        doNothing().when(connection).setAutoCommit(anyBoolean());
        when(connection.isClosed()).thenReturn(false);
        doNothing().when(connection).close();
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> newPreparedStatement());
        return connection;
    }

    /**
     * Registered with DriverManager in setupControllers(). The real org.postgresql.Driver is also
     * on the classpath and gets tried first when a connection is requested, fails with a
     * connection-refused SQLException (no live Postgres), and DriverManager falls through to this
     * driver, which always succeeds.
     */
    private static final class FakeDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (url == null || !url.toUpperCase(Locale.ENGLISH).startsWith("JDBC:POSTGRESQL:")) {
                return null;
            }
            return newConnection();
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return url != null && url.toUpperCase(Locale.ENGLISH).startsWith("JDBC:POSTGRESQL:");
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
