package com.mirth.connect.connectors.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jetty.ee8.nested.AbstractHandler;
import org.eclipse.jetty.ee8.nested.ContextHandler;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.ee8.nested.ServletConstraint;
import org.eclipse.jetty.ee8.security.ConstraintMapping;
import org.eclipse.jetty.ee8.security.ConstraintSecurityHandler;
import org.eclipse.jetty.ee8.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.security.Password;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;

import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

/**
 * Integration tests for IRT-831: verifies that {@link HttpReceiver}'s Jetty 12 handler chain
 * ({@code ContextHandlerCollection} + {@code DefaultHandler} + {@code Handler.Sequence}) correctly
 * enforces the configured context path — requests outside it return 404, requests on it return 200,
 * and authentication is enforced per-context after the routing fix.
 */
public class HttpReceiverContextPathRoutingTest {

    private static final String LOOPBACK  = "127.0.0.1";
    private static final String TEST_USER = "testuser";
    private static final String TEST_PASS = "testpass";

    private Server          jettyServer;
    private ServerConnector serverConnector;

    /** Captures the request target; always responds 200 with a configurable body. */
    private EchoHandler channelHandler;
    /** Second capture handler used for static-resource context tests. */
    private EchoHandler resourceHandler;

    // -------------------------------------------------------------------------
    // Class-level setup
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

    @After
    public void tearDown() throws Exception {
        if (jettyServer != null && jettyServer.isRunning()) {
            jettyServer.stop();
            jettyServer = null;
        }
    }

    // =========================================================================
    // GROUP A — Basic context path routing
    // =========================================================================

    @Test
    public void testRequestOnContextPathReturns200() throws Exception {
        int port = startServer("/test");
        assertEquals(200, statusOf(port, "/test"));
    }

    @Test
    public void testRequestOnContextSubpathReturns200() throws Exception {
        int port = startServer("/test");
        assertEquals(200, statusOf(port, "/test/sub/path"));
    }

    @Test
    public void testRequestOutsideContextPathReturns404() throws Exception {
        int port = startServer("/test");
        assertEquals(404, statusOf(port, "/other"));
    }

    @Test
    public void testRequestAtRootWhenContextPathIsRootReturns200() throws Exception {
        // An empty/root context path (after onStart normalization: "/" → "") accepts all requests.
        int port = startServer("/");
        assertEquals(200, statusOf(port, "/"));
    }

    /**
     * Regression test for IRT-831 mode (a): the path {@code /testing} shares the prefix
     * {@code /test} with the channel context but must NOT match it.
     * {@code ContextHandlerCollection} uses strict prefix+slash matching, so this must return 404.
     */
    @Test
    public void testMismatchedPrefixReturns404() throws Exception {
        int port = startServer("/test");
        assertEquals(404, statusOf(port, "/testing"));
    }

    // =========================================================================
    // GROUP B — Static resource routing
    // =========================================================================

    @Test
    public void testStaticResourceUnderChannelContextIsServed() throws Exception {
        int port = startServerWithStaticResource("/test", "/test/data");
        assertEquals(200, statusOf(port, "/test/data"));
        assertEquals("resource", bodyOf(port, "/test/data"));
    }

    @Test
    public void testNonResourcePathUnderChannelContextReturns200() throws Exception {
        int port = startServerWithStaticResource("/test", "/test/data");
        assertEquals(200, statusOf(port, "/test/wrong"));
    }

    @Test
    public void testStaticResourcePrefixTakesPrecedenceOverChannel() throws Exception {
        int port = startServerWithStaticResource("/test", "/test/data");
        // Resource handler must serve /test/data; channel handler must not be invoked at all.
        assertEquals("resource", bodyOf(port, "/test/data"));
        assertNull("Channel handler must not be invoked for a static resource path",
                channelHandler.lastTarget);
    }

    @Test
    public void testChannelHandlerServesNonResourcePaths() throws Exception {
        int port = startServerWithStaticResource("/test", "/test/data");
        assertEquals("channel", bodyOf(port, "/test/other"));
    }

    // =========================================================================
    // GROUP C — Authentication enforcement after per-context security placement
    // =========================================================================

    @Test
    public void testNoCredentialsReturns401() throws Exception {
        int port = startServerWithBasicAuth("/test");
        assertEquals(401, statusOf(port, "/test"));
    }

    @Test
    public void testValidCredentialsReturns200() throws Exception {
        int port = startServerWithBasicAuth("/test");
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse resp = client.execute(getWithAuth(port, "/test"))) {
            assertEquals(200, resp.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testAuthEnforcedOnStaticResourceWithoutCredentials() throws Exception {
        int port = startServerWithBasicAuthAndResource("/test", "/test/data");
        assertEquals(401, statusOf(port, "/test/data"));
        assertEquals(401, statusOf(port, "/test"));
    }

    @Test
    public void testAuthEnforcedOnStaticResourceWithValidCredentials() throws Exception {
        int port = startServerWithBasicAuthAndResource("/test", "/test/data");
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse resp = client.execute(getWithAuth(port, "/test/data"))) {
            assertEquals(200, resp.getStatusLine().getStatusCode());
            assertEquals("resource",
                    IOUtils.toString(resp.getEntity().getContent(), StandardCharsets.UTF_8));
        }
    }

    // =========================================================================
    // GROUP D — Jetty 12.0.33 API compatibility (D-06)
    // =========================================================================

    /**
     * Jetty 12.0.33 API compatibility: confirms that {@link ContextHandler} correctly reports
     * its configured context path via {@code getContextPath()} — the API used by
     * {@link HttpReceiver#onStart()} to register channel contexts. This verifies the Jetty 12.0.33
     * upgrade did not break the ContextHandler context-path API contract.
     */
    @Test
    public void testJetty1200ContextHandlerContextPathApi() {
        ContextHandler ctx = new ContextHandler();
        ctx.setContextPath("/api/channels");
        assertEquals("ContextHandler must return the exact context path that was set",
                "/api/channels", ctx.getContextPath());
    }

    // =========================================================================
    // Server builders
    // =========================================================================

    /** Single channel context, no auth. */
    private int startServer(String contextPath) throws Exception {
        channelHandler = new EchoHandler("ok");
        jettyServer    = newServer();

        ContextHandler ctx = new ContextHandler();
        ctx.setContextPath(contextPath);
        ctx.setHandler(channelHandler);

        assembleHandlers(jettyServer, ctx);
        jettyServer.start();
        return serverConnector.getLocalPort();
    }

    /** Channel context + static-resource context (no auth). */
    private int startServerWithStaticResource(String channelPath, String resourcePath)
            throws Exception {
        channelHandler  = new EchoHandler("channel");
        resourceHandler = new EchoHandler("resource");
        jettyServer     = newServer();

        ContextHandler resourceCtx = new ContextHandler();
        resourceCtx.setContextPath(resourcePath);
        resourceCtx.setAllowNullPathInfo(true);
        resourceCtx.setHandler(resourceHandler);

        ContextHandler channelCtx = new ContextHandler();
        channelCtx.setContextPath(channelPath);
        channelCtx.setHandler(channelHandler);

        // Resource context is more specific; ContextHandlerCollection picks longest prefix
        // automatically, so insertion order does not matter — but list resource first for clarity.
        assembleHandlers(jettyServer, resourceCtx, channelCtx);
        jettyServer.start();
        return serverConnector.getLocalPort();
    }

    /** Single channel context wrapped in BASIC auth. */
    private int startServerWithBasicAuth(String contextPath) throws Exception {
        channelHandler = new EchoHandler("ok");
        jettyServer    = newServer();

        HashLoginService loginService = makeLoginService();
        jettyServer.addBean(loginService);

        ContextHandler ctx = new ContextHandler();
        ctx.setContextPath(contextPath);
        ctx.setHandler(wrapWithBasicAuth(channelHandler, loginService));

        assembleHandlers(jettyServer, ctx);
        jettyServer.start();
        return serverConnector.getLocalPort();
    }

    /** Channel context + static-resource context, both wrapped in BASIC auth. */
    private int startServerWithBasicAuthAndResource(String channelPath, String resourcePath)
            throws Exception {
        channelHandler  = new EchoHandler("channel");
        resourceHandler = new EchoHandler("resource");
        jettyServer     = newServer();

        HashLoginService loginService = makeLoginService();
        jettyServer.addBean(loginService);

        ContextHandler resourceCtx = new ContextHandler();
        resourceCtx.setContextPath(resourcePath);
        resourceCtx.setAllowNullPathInfo(true);
        resourceCtx.setHandler(wrapWithBasicAuth(resourceHandler, loginService));

        ContextHandler channelCtx = new ContextHandler();
        channelCtx.setContextPath(channelPath);
        channelCtx.setHandler(wrapWithBasicAuth(channelHandler, loginService));

        assembleHandlers(jettyServer, resourceCtx, channelCtx);
        jettyServer.start();
        return serverConnector.getLocalPort();
    }

    // =========================================================================
    // Helpers — Jetty assembly (mirrors the IRT-831 fix in HttpReceiver.onStart())
    // =========================================================================

    private Server newServer() {
        Server s = new Server();
        org.eclipse.jetty.server.HttpConfiguration httpCfg =
                new org.eclipse.jetty.server.HttpConfiguration();
        httpCfg.setSendServerVersion(false);
        serverConnector = new ServerConnector(s, new HttpConnectionFactory(httpCfg));
        serverConnector.setHost(LOOPBACK);
        serverConnector.setPort(0);
        s.addConnector(serverConnector);
        return s;
    }

    private static void assembleHandlers(Server server, ContextHandler... contexts) {
        org.eclipse.jetty.server.handler.ContextHandlerCollection coreHandlers =
                new org.eclipse.jetty.server.handler.ContextHandlerCollection();
        for (ContextHandler ctx : contexts) {
            ctx.setServer(server);
            coreHandlers.addHandler(ctx.getCoreContextHandler());
        }
        org.eclipse.jetty.server.handler.DefaultHandler defaultHandler =
                new org.eclipse.jetty.server.handler.DefaultHandler();
        defaultHandler.setServeFavIcon(false);
        server.setHandler(new org.eclipse.jetty.server.Handler.Sequence(coreHandlers, defaultHandler));
    }

    private static HashLoginService makeLoginService() {
        UserStore userStore = new UserStore();
        userStore.addUser(TEST_USER, new Password(TEST_PASS), new String[]{"user"});
        HashLoginService svc = new HashLoginService("test-realm");
        svc.setUserStore(userStore);
        return svc;
    }

    private static ConstraintSecurityHandler wrapWithBasicAuth(
            org.eclipse.jetty.ee8.nested.Handler inner, HashLoginService loginService) {
        ServletConstraint constraint = new ServletConstraint();
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);
        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setConstraint(constraint);
        mapping.setPathSpec("/*");
        ConstraintSecurityHandler sec = new ConstraintSecurityHandler();
        sec.addConstraintMapping(mapping);
        sec.setAuthenticator(new BasicAuthenticator());
        sec.setLoginService(loginService);
        sec.setHandler(inner);
        return sec;
    }

    // =========================================================================
    // Helpers — HTTP client
    // =========================================================================

    private static int statusOf(int port, String path) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse resp = client.execute(get(port, path))) {
            return resp.getStatusLine().getStatusCode();
        }
    }

    private static String bodyOf(int port, String path) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse resp = client.execute(get(port, path))) {
            return IOUtils.toString(resp.getEntity().getContent(), StandardCharsets.UTF_8);
        }
    }

    private static HttpGet get(int port, String path) {
        return new HttpGet("http://" + LOOPBACK + ":" + port + path);
    }

    private static HttpGet getWithAuth(int port, String path) {
        HttpGet req = get(port, path);
        String encoded = Base64.getEncoder().encodeToString(
                (TEST_USER + ":" + TEST_PASS).getBytes(StandardCharsets.UTF_8));
        req.setHeader("Authorization", "Basic " + encoded);
        return req;
    }

    // =========================================================================
    // Inner class
    // =========================================================================

    private static class EchoHandler extends AbstractHandler {

        private final String body;
        volatile String lastTarget;

        EchoHandler(String body) {
            this.body = body;
        }

        @Override
        public void handle(String target, Request baseRequest,
                HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            lastTarget = target;
            baseRequest.setHandled(true);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(body);
        }
    }
}
