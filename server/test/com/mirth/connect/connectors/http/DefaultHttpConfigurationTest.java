/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.ServerSocket;

import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;

import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

/**
 * Unit/component tests for {@link DefaultHttpConfiguration#configureReceiver}.
 *
 * <p>Verifies that the {@code requestHeaderSize} value from {@link HttpReceiver} is
 * correctly wired into Jetty's {@link org.eclipse.jetty.server.HttpConfiguration}.
 * No server is started and no network traffic is generated.
 *
 * <p>Uses the same Guice/Mockito {@code @BeforeClass} pattern as {@link HttpReceiverTest}
 * because {@link DefaultHttpConfiguration} calls
 * {@code ControllerFactory.getFactory().createConfigurationController()} in its
 * field initializer, which requires the static injection to be in place first.
 */
public class DefaultHttpConfigurationTest {

    private static final int DEFAULT_HEADER_SIZE = 8192;
    private static final int LARGE_HEADER_SIZE   = 32768;

    // -------------------------------------------------------------------------
    // Class-level setup — Guice / Mockito
    // -------------------------------------------------------------------------

    @BeforeClass
    public static void setUpClass() {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        ConfigurationController configurationController = mock(ConfigurationController.class);
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        EventController eventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(eventController);

        Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        }).getInstance(ControllerFactory.class);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Calls {@link DefaultHttpConfiguration#configureReceiver} with a mock
     * {@link HttpReceiver} that reports {@code requestHeaderSize = 32768}, then
     * introspects the resulting Jetty {@link ServerConnector} to confirm the
     * value was applied to {@link org.eclipse.jetty.server.HttpConfiguration}.
     *
     * <p>The server is <em>never started</em>; this test only verifies the wiring.
     */
    @Test
    public void testConfigureReceiver_appliesRequestHeaderSizeToJetty() throws Exception {
        Server server = new Server();
        new DefaultHttpConfiguration().configureReceiver(
                mockReceiver(server, findFreePort(), LARGE_HEADER_SIZE));

        ServerConnector connector = (ServerConnector) server.getConnectors()[0];
        HttpConnectionFactory factory = connector.getConnectionFactory(HttpConnectionFactory.class);

        assertEquals(
            "Jetty HttpConfiguration must reflect the value from HttpReceiver.getRequestHeaderSize()",
            LARGE_HEADER_SIZE,
            factory.getHttpConfiguration().getRequestHeaderSize());
    }

    /**
     * Default value (8 192) is also reflected in the Jetty connector so that
     * the default codepath is exercised, not only the non-default case.
     *
     * <p>The server is <em>never started</em>; this test only verifies the wiring.
     */
    @Test
    public void testConfigureReceiver_defaultSizeApplied() throws Exception {
        Server server = new Server();
        new DefaultHttpConfiguration().configureReceiver(
                mockReceiver(server, findFreePort(), DEFAULT_HEADER_SIZE));

        ServerConnector connector = (ServerConnector) server.getConnectors()[0];
        HttpConnectionFactory factory = connector.getConnectionFactory(HttpConnectionFactory.class);

        assertEquals(DEFAULT_HEADER_SIZE, factory.getHttpConfiguration().getRequestHeaderSize());
    }

    /**
     * Jetty 12.0.33 API compatibility: confirms that {@link HttpConnectionFactory} can be retrieved
     * from a {@link ServerConnector} via {@code getConnectionFactory(Class)} and that the
     * {@link org.eclipse.jetty.server.HttpConfiguration} it wraps is non-null — the basic wiring
     * contract used by {@link DefaultHttpConfiguration#configureReceiver} and all BridgeLink HTTP
     * connector setup code. This is a standing compatibility guard for the Jetty 12.0.33 upgrade.
     */
    @Test
    public void testJetty1200HttpConnectionFactoryWiring() throws Exception {
        Server server = new Server();
        new DefaultHttpConfiguration().configureReceiver(
                mockReceiver(server, findFreePort(), DEFAULT_HEADER_SIZE));

        ServerConnector connector = (ServerConnector) server.getConnectors()[0];
        HttpConnectionFactory factory = connector.getConnectionFactory(HttpConnectionFactory.class);

        assertNotNull("HttpConnectionFactory must be retrievable from ServerConnector in Jetty 12.0.33",
                factory);
        assertNotNull("HttpConfiguration must be non-null in Jetty 12.0.33",
                factory.getHttpConfiguration());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static HttpReceiver mockReceiver(Server server, int port, int requestHeaderSize) {
        HttpReceiver mockReceiver = mock(HttpReceiver.class);
        when(mockReceiver.getServer()).thenReturn(server);
        when(mockReceiver.getHost()).thenReturn("127.0.0.1");
        when(mockReceiver.getPort()).thenReturn(port);
        when(mockReceiver.getTimeout()).thenReturn(30_000);
        when(mockReceiver.getRequestHeaderSize()).thenReturn(requestHeaderSize);
        return mockReceiver;
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
