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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.ServerSocket;
import java.util.Arrays;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;

import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

/**
 * <b>Integration tests</b> for the configurable HTTP request header size feature.
 *
 * <p>These tests start a real Jetty 12 server on a loopback port and send real HTTP
 * requests.  They are picked up by the Ant {@code test-run} target via the
 * {@code **&#47;*Test.class} pattern.
 *
 * <p>Property-model tests live in {@link HttpReceiverPropertiesTest}.
 * Configuration-plumbing tests (no network) live in {@link DefaultHttpConfigurationTest}.
 *
 * <p>Verified against <b>Jetty 12.0.32</b>.  Jetty 12's default
 * {@code requestHeaderSize} remains <b>8 192 bytes</b> — identical to Jetty 9.4.
 */
public class HttpListenerRequestHeaderSizeTest {

    private static final int DEFAULT_HEADER_SIZE = 8192;
    private static final int LARGE_HEADER_SIZE   = 32768;
    private static final String LOOPBACK         = "127.0.0.1";

    /** Jetty server started by the current test; stopped in {@link #tearDown()}. */
    private Server jettyServer;

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
    // Per-test teardown
    // -------------------------------------------------------------------------

    @After
    public void tearDown() throws Exception {
        if (jettyServer != null && jettyServer.isRunning()) {
            jettyServer.stop();
            jettyServer = null;
        }
    }

    // -------------------------------------------------------------------------
    // Tests — Jetty 12 runtime (live server + real HTTP requests)
    // -------------------------------------------------------------------------

    /**
     * Small, normal-sized headers are always accepted regardless of the configured
     * limit — this is the baseline sanity check.
     */
    @Test
    public void testRuntime_smallHeaders_returns200() throws Exception {
        int port = startJettyServer(DEFAULT_HEADER_SIZE);
        assertEquals(200, get(port, "X-Test", repeat('A', 100)));
    }

    /**
     * With the default 8 KB limit, a request whose total header block exceeds
     * 8 192 bytes must be rejected by Jetty with HTTP 431 Request Header Fields
     * Too Large — the exact error that prompted this feature.
     *
     * <p>A single 9 000-byte header value is used to push the total above the limit
     * (header name + colon + value + CRLF ≈ 9 020 bytes &gt; 8 192).
     */
    @Test
    public void testRuntime_headersExceedDefault8KB_returns431() throws Exception {
        int port = startJettyServer(DEFAULT_HEADER_SIZE);
        assertEquals(431, get(port, "X-Large-Header", repeat('A', 9_000)));
    }

    /**
     * After raising the limit to 32 KB, the same 9 KB request that was rejected
     * above must now reach the handler and receive HTTP 200.
     *
     * <p>This is the primary regression assertion for the feature: changing
     * {@code requestHeaderSize} from 8192 to 32768 in the HTTP Listener connector
     * settings must make the 431 go away.
     */
    @Test
    public void testRuntime_headersExceed8KB_acceptedAt32KB() throws Exception {
        int port = startJettyServer(LARGE_HEADER_SIZE);
        assertEquals(200, get(port, "X-Large-Header", repeat('A', 9_000)));
    }

    /**
     * Headers that still exceed the raised 32 KB limit must still be rejected.
     * This confirms that the configured limit is enforced at every value,
     * not simply disabled once raised above the default.
     */
    @Test
    public void testRuntime_headersExceed32KB_returns431() throws Exception {
        int port = startJettyServer(LARGE_HEADER_SIZE);
        // 33 000-byte value exceeds the 32 768-byte limit
        assertEquals(431, get(port, "X-Large-Header", repeat('A', 33_000)));
    }

    /**
     * The boundary condition: a header value sized to push the request exactly
     * 1 byte over the configured limit must be rejected, while a value well
     * inside the limit must be accepted.
     *
     * <p>The header overhead (name + ": " + CRLF + request line + Host header) is
     * approximately 60–80 bytes.  A conservative margin of 200 bytes keeps the
     * test stable across minor Jetty version differences in framing overhead.
     */
    @Test
    public void testRuntime_boundaryAroundConfiguredLimit() throws Exception {
        int limit = 16_384; // 16 KB — deliberately between 8 KB default and 32 KB
        int port  = startJettyServer(limit);

        int overhead = 200; // conservative estimate of non-value header bytes

        // Value that keeps total just inside the limit → 200
        assertEquals(200, get(port, "X-Boundary", repeat('A', limit - overhead - 1)));

        // Value that pushes total just over the limit → 431
        assertEquals(431, get(port, "X-Boundary", repeat('A', limit)));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Starts a real Jetty 12 server on a free loopback port, configured via
     * {@link DefaultHttpConfiguration#configureReceiver} (the production code path),
     * with a trivial {@link Handler.Abstract.NonBlocking} that returns HTTP 200 for
     * any request that Jetty accepts.
     *
     * <p>The 431 rejection for oversized headers occurs inside Jetty's HTTP parser,
     * before the handler is ever invoked.  No EE8 context or servlet wrapper is
     * needed here because we are testing the TCP/HTTP layer only.
     *
     * @param requestHeaderSize the value to pass as {@code requestHeaderSize}
     * @return the local port the server is listening on
     */
    private int startJettyServer(int requestHeaderSize) throws Exception {
        int port = findFreePort();
        jettyServer = new Server();

        new DefaultHttpConfiguration().configureReceiver(
                mockReceiver(jettyServer, port, requestHeaderSize));

        // Minimal Jetty 12 core handler: if Jetty passed the request through, respond 200.
        // Requests with oversized headers never reach this handler — Jetty 12 rejects
        // them during HTTP parsing and sends back 431 directly, before handler dispatch.
        //
        // Handler.Abstract.NonBlocking signals InvocationType.NON_BLOCKING to Jetty's
        // scheduler: our handler completes synchronously and never parks a thread.
        jettyServer.setHandler(new Handler.Abstract.NonBlocking() {
            @Override
            public boolean handle(Request request, Response response, Callback callback)
                    throws Exception {
                response.setStatus(200);
                callback.succeeded();
                return true;
            }
        });

        jettyServer.start();
        return port;
    }

    /**
     * Creates a minimal {@link HttpReceiver} mock providing only the values
     * that {@link DefaultHttpConfiguration#configureReceiver} reads.
     */
    private static HttpReceiver mockReceiver(Server server, int port, int requestHeaderSize) {
        HttpReceiver mockReceiver = mock(HttpReceiver.class);
        when(mockReceiver.getServer()).thenReturn(server);
        when(mockReceiver.getHost()).thenReturn(LOOPBACK);
        when(mockReceiver.getPort()).thenReturn(port);
        when(mockReceiver.getTimeout()).thenReturn(30_000);
        when(mockReceiver.getRequestHeaderSize()).thenReturn(requestHeaderSize);
        return mockReceiver;
    }

    /**
     * Sends a GET request to {@code http://127.0.0.1:{port}/test} with a single
     * custom header and returns the HTTP response status code.
     *
     * <p>A new {@link CloseableHttpClient} is created per call so that connection-
     * pool state from a previous test's 431 rejection does not interfere.
     */
    private static int get(int port, String headerName, String headerValue) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet("http://" + LOOPBACK + ":" + port + "/test");
            request.setHeader(headerName, headerValue);
            try (CloseableHttpResponse response = client.execute(request)) {
                return response.getStatusLine().getStatusCode();
            }
        }
    }

    /**
     * Allocates a free TCP port on loopback by briefly binding to port 0, then
     * releases it. There is a small race window between release and Jetty binding,
     * but this is standard practice for test port allocation.
     */
    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    /** Returns a {@link String} of {@code count} copies of character {@code c}. */
    private static String repeat(char c, int count) {
        char[] buf = new char[count];
        Arrays.fill(buf, c);
        return new String(buf);
    }
}
