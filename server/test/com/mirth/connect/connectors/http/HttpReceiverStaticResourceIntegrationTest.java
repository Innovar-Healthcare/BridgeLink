package com.mirth.connect.connectors.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jetty.ee8.nested.AbstractHandler;
import org.eclipse.jetty.ee8.nested.ContextHandler;
import org.eclipse.jetty.ee8.nested.HandlerCollection;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;

import com.mirth.connect.connectors.http.HttpStaticResource.ResourceType;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

/**
 * Integration tests for IRT-828: verifies that Content-Length is correctly set on the wire for
 * large static resources served by {@link HttpReceiver}, using a real Jetty 12 server on loopback.
 *
 * <p>These tests close the coverage gap left by {@link HttpReceiverStaticResourceTest}: the unit
 * tests verify the fix is <em>present</em> (Content-Length set in the mock response), but cannot
 * prove the fix resolves the runtime behavior — specifically that Jetty 12 does not prematurely
 * commit the response when Content-Length is set.  Running against a real Jetty 12 + HTTP/1.1
 * stack proves the full write path behaves correctly for large (100 KB) resources.
 *
 * <p>HTTP/1.1 is sufficient: the {@code ERR_HTTP2_PROTOCOL_ERROR} the browser reports is a
 * symptom of Jetty sending a malformed HTTP/2 stream when Content-Length is absent.  Once
 * Content-Length is set correctly on HTTP/1.1, the HTTP/2 path is also fixed because Jetty uses
 * Content-Length to correctly signal end-of-stream in both protocols.
 *
 * <p>Verified against <b>Jetty 12.0.x</b>.
 */
public class HttpReceiverStaticResourceIntegrationTest {

    private static final String LOOPBACK   = "127.0.0.1";
    private static final int    LARGE_SIZE = 100 * 1024; // 100 KB — well above the ~30 KB failure threshold

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private HttpReceiver    receiver;
    private Server          jettyServer;
    private ServerConnector serverConnector;

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
    // Per-test setup / teardown
    // -------------------------------------------------------------------------

    @Before
    public void setUp() throws Exception {
        receiver = new HttpReceiver();
        HttpReceiverProperties props = new HttpReceiverProperties();
        props.setCharset("UTF-8");

        Channel channel = mock(Channel.class);
        doReturn("test-channel-id").when(channel).getChannelId();
        doReturn("Test Channel").when(channel).getName();
        receiver.setChannel(channel);
        receiver.setConnectorProperties(props);

        Field arrayField = HttpReceiver.class.getDeclaredField("binaryMimeTypesArray");
        arrayField.setAccessible(true);
        arrayField.set(receiver, new String[0]);

        Field regexField = HttpReceiver.class.getDeclaredField("binaryMimeTypesRegex");
        regexField.setAccessible(true);
        regexField.set(receiver, java.util.regex.Pattern.compile("$^"));

        HttpConfiguration httpConfig = mock(HttpConfiguration.class);
        when(httpConfig.getRequestInformation(any())).thenReturn(new HashMap<>());
        Field configField = HttpReceiver.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        configField.set(receiver, httpConfig);
    }

    @After
    public void tearDown() throws Exception {
        if (jettyServer != null && jettyServer.isRunning()) {
            jettyServer.stop();
            jettyServer = null;
        }
    }

    // =========================================================================
    // GROUP A — ResourceType.CUSTOM (inline string value)
    // =========================================================================

    @Test
    public void testCustom_large_contentLengthPresentOnWire() throws Exception {
        String content = repeat('A', LARGE_SIZE);
        long expectedLen = content.getBytes(StandardCharsets.UTF_8).length;
        int port = startServer(new HttpStaticResource("/resource", ResourceType.CUSTOM, content, "text/plain", Collections.emptyMap()));

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(get(port, "/resource"))) {
            assertEquals(200, resp.getStatusLine().getStatusCode());
            Header h = resp.getFirstHeader("Content-Length");
            assertNotNull("Content-Length must be present for large CUSTOM resource", h);
            assertEquals(expectedLen, Long.parseLong(h.getValue()));
        }
    }

    @Test
    public void testCustom_large_bodyIntegrity() throws Exception {
        String content = repeat('A', LARGE_SIZE);
        int port = startServer(new HttpStaticResource("/resource", ResourceType.CUSTOM, content, "text/plain", Collections.emptyMap()));

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(get(port, "/resource"))) {
            byte[] body = IOUtils.toByteArray(resp.getEntity().getContent());
            assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), body);
        }
    }

    @Test
    public void testCustom_gzip_contentEncodingHeaderPresent() throws Exception {
        String content = repeat('A', LARGE_SIZE);
        int port = startServer(new HttpStaticResource("/resource", ResourceType.CUSTOM, content, "text/plain", Collections.emptyMap()));

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(getGzip(port, "/resource"))) {
            assertEquals(200, resp.getStatusLine().getStatusCode());
            Header h = resp.getFirstHeader("Content-Encoding");
            assertNotNull("Content-Encoding header must be present for gzip CUSTOM response", h);
            assertTrue("Content-Encoding must be gzip", h.getValue().contains("gzip"));
        }
    }

    @Test
    public void testCustom_gzip_bodyDecompressesToOriginalContent() throws Exception {
        String content = repeat('A', LARGE_SIZE);
        int port = startServer(new HttpStaticResource("/resource", ResourceType.CUSTOM, content, "text/plain", Collections.emptyMap()));

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(getGzip(port, "/resource"))) {
            byte[] compressed = IOUtils.toByteArray(resp.getEntity().getContent());
            String decompressed;
            try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                decompressed = new String(gzis.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertEquals(content, decompressed);
        }
    }

    // =========================================================================
    // GROUP B — ResourceType.FILE
    // =========================================================================

    @Test
    public void testFile_large_contentLengthPresentOnWire() throws Exception {
        byte[] content = randomBytes(LARGE_SIZE);
        File f = writeTempFile("large.bin", content);
        int port = startServer(new HttpStaticResource("/resource", ResourceType.FILE, f.getAbsolutePath(), "application/octet-stream", Collections.emptyMap()));

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(get(port, "/resource"))) {
            assertEquals(200, resp.getStatusLine().getStatusCode());
            Header h = resp.getFirstHeader("Content-Length");
            assertNotNull("Content-Length must be present for large FILE resource", h);
            assertEquals(content.length, Long.parseLong(h.getValue()));
        }
    }

    @Test
    public void testFile_large_bodyIntegrity() throws Exception {
        byte[] content = randomBytes(LARGE_SIZE);
        File f = writeTempFile("large-body.bin", content);
        int port = startServer(new HttpStaticResource("/resource", ResourceType.FILE, f.getAbsolutePath(), "application/octet-stream", Collections.emptyMap()));

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(get(port, "/resource"))) {
            byte[] body = IOUtils.toByteArray(resp.getEntity().getContent());
            assertArrayEquals(content, body);
        }
    }

    @Test
    public void testFile_gzip_contentEncodingHeaderPresent() throws Exception {
        byte[] content = new byte[LARGE_SIZE];
        Arrays.fill(content, (byte) 'Z');
        File f = writeTempFile("gzip-check.bin", content);
        int port = startServer(new HttpStaticResource("/resource", ResourceType.FILE, f.getAbsolutePath(), "application/octet-stream", Collections.emptyMap()));

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(getGzip(port, "/resource"))) {
            assertEquals(200, resp.getStatusLine().getStatusCode());
            Header h = resp.getFirstHeader("Content-Encoding");
            assertNotNull("Content-Encoding header must be present for gzip FILE response", h);
            assertTrue("Content-Encoding must be gzip", h.getValue().contains("gzip"));
        }
    }

    @Test
    public void testFile_gzip_bodyDecompressesToOriginalContent() throws Exception {
        byte[] content = new byte[LARGE_SIZE];
        Arrays.fill(content, (byte) 'Z');
        File f = writeTempFile("gzip-body.bin", content);
        int port = startServer(new HttpStaticResource("/resource", ResourceType.FILE, f.getAbsolutePath(), "application/octet-stream", Collections.emptyMap()));

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(getGzip(port, "/resource"))) {
            byte[] compressed = IOUtils.toByteArray(resp.getEntity().getContent());
            byte[] decompressed;
            try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                decompressed = gzis.readAllBytes();
            }
            assertArrayEquals(content, decompressed);
        }
    }

    // =========================================================================
    // GROUP C — Routing: requests outside context path must return 404 (IRT-831)
    // =========================================================================

    @Test
    public void testRequestOutsideResourceContextPathReturns404() throws Exception {
        String content = "hello";
        int port = startServer(new HttpStaticResource("/resource", ResourceType.CUSTOM, content,
                "text/plain", Collections.emptyMap()));

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(get(port, "/other"))) {
            assertEquals(404, resp.getStatusLine().getStatusCode());
        }
    }

    // =========================================================================
    // GROUP D — ResourceType.DIRECTORY fallthrough to channel handler (IRT-831 fix)
    // Verifies that StaticResourceHandler does NOT mark the request as handled when
    // a requested file is absent, allowing the channel context to process it.
    // =========================================================================

    @Test
    public void testDirectory_missingFile_fallsThroughToChannelHandler() throws Exception {
        File dir = tempFolder.newFolder("dir-missing");
        int port = startServerWithDirectoryAndChannel("/test/data", dir, "/test");

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(get(port, "/test/data/nonexistent.txt"))) {
            assertEquals("Missing file under DIRECTORY resource must fall through to channel handler",
                    200, resp.getStatusLine().getStatusCode());
            assertEquals("channel",
                    IOUtils.toString(resp.getEntity().getContent(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testDirectory_existingFile_isServedByStaticHandler() throws Exception {
        File dir = tempFolder.newFolder("dir-serve");
        File file = new File(dir, "hello.txt");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("static-content".getBytes(StandardCharsets.UTF_8));
        }
        int port = startServerWithDirectoryAndChannel("/test/data", dir, "/test");

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(get(port, "/test/data/hello.txt"))) {
            assertEquals(200, resp.getStatusLine().getStatusCode());
            assertEquals("static-content",
                    IOUtils.toString(resp.getEntity().getContent(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testDirectory_subdirectoryInPath_fallsThroughToChannelHandler() throws Exception {
        File dir = tempFolder.newFolder("dir-subpath");
        int port = startServerWithDirectoryAndChannel("/test/data", dir, "/test");

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(get(port, "/test/data/sub/file.txt"))) {
            assertEquals("Subdirectory path must fall through to channel handler",
                    200, resp.getStatusLine().getStatusCode());
            assertEquals("channel",
                    IOUtils.toString(resp.getEntity().getContent(), StandardCharsets.UTF_8));
        }
    }

    // =========================================================================
    // GROUP E — CUSTOM and FILE fallthrough to channel handler (IRT-831, commit a200466b0)
    // Before this fix, only DIRECTORY resources were embedded inside the channel
    // ContextHandler. CUSTOM and FILE still had their own ContextHandler, so
    // CoreContextHandler returned true on any prefix match — blocking fallthrough
    // for sub-paths the StaticResourceHandler could not serve.
    // =========================================================================

    @Test
    public void testCustom_subPath_fallsThroughToChannelHandler() throws Exception {
        String content = "custom-content";
        int port = startServerWithResourceAndChannel(
                "/test/data", ResourceType.CUSTOM, content, "/test");

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(get(port, "/test/data/wrong"))) {
            assertEquals(
                    "Sub-path miss on CUSTOM resource must fall through to channel handler",
                    200, resp.getStatusLine().getStatusCode());
            assertEquals("channel",
                    IOUtils.toString(resp.getEntity().getContent(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testFile_subPath_fallsThroughToChannelHandler() throws Exception {
        byte[] content = "file-content".getBytes(StandardCharsets.UTF_8);
        File f = writeTempFile("serve.bin", content);
        int port = startServerWithResourceAndChannel(
                "/test/data", ResourceType.FILE, f.getAbsolutePath(), "/test");

        try (CloseableHttpClient client = noCompressionClient();
             CloseableHttpResponse resp = client.execute(get(port, "/test/data/wrong"))) {
            assertEquals(
                    "Sub-path miss on FILE resource must fall through to channel handler",
                    200, resp.getStatusLine().getStatusCode());
            assertEquals("channel",
                    IOUtils.toString(resp.getEntity().getContent(), StandardCharsets.UTF_8));
        }
    }

    // =========================================================================
    // Helpers — Jetty server setup
    // =========================================================================

    /**
     * Starts a real Jetty 12 server on a random loopback port using the IRT-831 handler chain
     * ({@code ContextHandlerCollection} + {@code DefaultHandler} + {@code Handler.Sequence})
     * with {@code StaticResourceHandler} for a single static resource.
     * Uses reflection to instantiate the private inner class.
     *
     * @return the local port the server is listening on
     */
    private int startServer(HttpStaticResource resource) throws Exception {
        jettyServer = new Server();

        org.eclipse.jetty.server.HttpConfiguration httpCfg = new org.eclipse.jetty.server.HttpConfiguration();
        httpCfg.setSendServerVersion(false);
        serverConnector = new ServerConnector(jettyServer, new HttpConnectionFactory(httpCfg));
        serverConnector.setHost(LOOPBACK);
        serverConnector.setPort(0); // random free port
        serverConnector.setIdleTimeout(30_000);
        jettyServer.addConnector(serverConnector);

        // Reflectively instantiate the private StaticResourceHandler inner class
        Class<?> handlerClass = null;
        for (Class<?> c : HttpReceiver.class.getDeclaredClasses()) {
            if ("StaticResourceHandler".equals(c.getSimpleName())) {
                handlerClass = c;
                break;
            }
        }
        assertNotNull("StaticResourceHandler inner class not found in HttpReceiver", handlerClass);
        Constructor<?> ctor = handlerClass.getDeclaredConstructor(HttpReceiver.class, HttpStaticResource.class);
        ctor.setAccessible(true);
        AbstractHandler staticHandler = (AbstractHandler) ctor.newInstance(receiver, resource);

        // Replicate the EE8 handler chain from HttpReceiver.onStart()
        ContextHandler resourceContext = new ContextHandler();
        resourceContext.setContextPath(resource.getContextPath());
        resourceContext.setAllowNullPathInfo(true);
        resourceContext.setHandler(staticHandler);

        org.eclipse.jetty.server.handler.ContextHandlerCollection coreHandlers =
                new org.eclipse.jetty.server.handler.ContextHandlerCollection();
        resourceContext.setServer(jettyServer);
        coreHandlers.addHandler(resourceContext.getCoreContextHandler());

        org.eclipse.jetty.server.handler.DefaultHandler defaultHandler =
                new org.eclipse.jetty.server.handler.DefaultHandler();
        defaultHandler.setServeFavIcon(false);

        jettyServer.setHandler(new org.eclipse.jetty.server.Handler.Sequence(coreHandlers, defaultHandler));

        jettyServer.start();
        return serverConnector.getLocalPort();
    }

    /**
     * Starts a server with a single static resource handler (any {@link ResourceType}) and a
     * {@link ChannelEchoHandler} embedded inside one channel {@link ContextHandler}, replicating
     * the architecture from commit a200466b0. Used by GROUP E tests.
     */
    private int startServerWithResourceAndChannel(
            String resourceContextPath, ResourceType resourceType,
            String resourceValue, String channelContextPath) throws Exception {
        jettyServer = new Server();
        org.eclipse.jetty.server.HttpConfiguration httpCfg =
                new org.eclipse.jetty.server.HttpConfiguration();
        httpCfg.setSendServerVersion(false);
        serverConnector = new ServerConnector(jettyServer, new HttpConnectionFactory(httpCfg));
        serverConnector.setHost(LOOPBACK);
        serverConnector.setPort(0);
        serverConnector.setIdleTimeout(30_000);
        jettyServer.addConnector(serverConnector);

        Class<?> handlerClass = null;
        for (Class<?> c : HttpReceiver.class.getDeclaredClasses()) {
            if ("StaticResourceHandler".equals(c.getSimpleName())) {
                handlerClass = c;
                break;
            }
        }
        assertNotNull("StaticResourceHandler inner class not found in HttpReceiver", handlerClass);
        Constructor<?> ctor = handlerClass.getDeclaredConstructor(
                HttpReceiver.class, HttpStaticResource.class);
        ctor.setAccessible(true);
        HttpStaticResource resource = new HttpStaticResource(
                resourceContextPath, resourceType,
                resourceValue, "text/plain", Collections.emptyMap());
        AbstractHandler staticHandler = (AbstractHandler) ctor.newInstance(receiver, resource);

        HandlerCollection innerChain = new HandlerCollection() {
            @Override
            public void handle(String target, Request baseRequest,
                    HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                for (org.eclipse.jetty.ee8.nested.Handler h : getHandlers()) {
                    h.handle(target, baseRequest, request, response);
                    if (baseRequest.isHandled()) {
                        return;
                    }
                }
            }
        };
        innerChain.addHandler(staticHandler);
        innerChain.addHandler(new ChannelEchoHandler());

        ContextHandler channelCtx = new ContextHandler();
        channelCtx.setContextPath(channelContextPath);
        channelCtx.setAllowNullPathInfo(true);
        channelCtx.setHandler(innerChain);
        channelCtx.setServer(jettyServer);

        org.eclipse.jetty.server.handler.DefaultHandler defaultHandler =
                new org.eclipse.jetty.server.handler.DefaultHandler();
        defaultHandler.setServeFavIcon(false);

        jettyServer.setHandler(new org.eclipse.jetty.server.Handler.Sequence(
                channelCtx.getCoreContextHandler(), defaultHandler));

        jettyServer.start();
        return serverConnector.getLocalPort();
    }

    // =========================================================================
    // Helpers — HTTP client
    // =========================================================================

    /**
     * Returns a client with content compression disabled — no automatic {@code Accept-Encoding}
     * header is added and gzip responses are NOT auto-decompressed.  Required so that tests can
     * inspect the raw {@code Content-Length} and {@code Content-Encoding} response headers.
     */
    private static CloseableHttpClient noCompressionClient() {
        return HttpClients.custom().disableContentCompression().build();
    }

    private static HttpGet get(int port, String path) {
        return new HttpGet("http://" + LOOPBACK + ":" + port + path);
    }

    private static HttpGet getGzip(int port, String path) {
        HttpGet request = new HttpGet("http://" + LOOPBACK + ":" + port + path);
        request.setHeader("Accept-Encoding", "gzip");
        return request;
    }

    // =========================================================================
    // Helpers — data generation and file I/O
    // =========================================================================

    private static byte[] randomBytes(int size) {
        byte[] buf = new byte[size];
        new Random().nextBytes(buf);
        return buf;
    }

    private File writeTempFile(String name, byte[] content) throws Exception {
        File f = tempFolder.newFile(name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content);
        }
        return f;
    }

    private static String repeat(char c, int count) {
        char[] buf = new char[count];
        Arrays.fill(buf, c);
        return new String(buf);
    }

    /**
     * Starts a server with a DIRECTORY {@link StaticResourceHandler} at {@code resourceContextPath}
     * and a simple channel echo handler at {@code channelContextPath}. Used by GROUP D tests to
     * verify that unanswered requests fall through from the resource context to the channel.
     */
    private int startServerWithDirectoryAndChannel(
            String resourceContextPath, File dir, String channelContextPath) throws Exception {
        jettyServer = new Server();
        org.eclipse.jetty.server.HttpConfiguration httpCfg =
                new org.eclipse.jetty.server.HttpConfiguration();
        httpCfg.setSendServerVersion(false);
        serverConnector = new ServerConnector(jettyServer, new HttpConnectionFactory(httpCfg));
        serverConnector.setHost(LOOPBACK);
        serverConnector.setPort(0);
        serverConnector.setIdleTimeout(30_000);
        jettyServer.addConnector(serverConnector);

        Class<?> handlerClass = null;
        for (Class<?> c : HttpReceiver.class.getDeclaredClasses()) {
            if ("StaticResourceHandler".equals(c.getSimpleName())) {
                handlerClass = c;
                break;
            }
        }
        assertNotNull("StaticResourceHandler inner class not found in HttpReceiver", handlerClass);
        Constructor<?> ctor = handlerClass.getDeclaredConstructor(
                HttpReceiver.class, HttpStaticResource.class);
        ctor.setAccessible(true);
        HttpStaticResource resource = new HttpStaticResource(
                resourceContextPath, ResourceType.DIRECTORY,
                dir.getAbsolutePath(), "application/octet-stream", Collections.emptyMap());
        AbstractHandler staticHandler = (AbstractHandler) ctor.newInstance(receiver, resource);

        // Both handlers live inside ONE channel ContextHandler with a stop-on-first-handled
        // HandlerCollection. Separate contexts don't work: the EE8-to-core bridge
        // (CoreContextHandler) returns true once the context path matches, even when the
        // inner EE8 handler never sets isHandled(). Subclassing HandlerCollection (rather
        // than using an anonymous AbstractHandler) ensures Jetty's lifecycle calls
        // (setServer, start) propagate through addHandler() to both inner handlers.
        HandlerCollection innerChain = new HandlerCollection() {
            @Override
            public void handle(String target, Request baseRequest,
                    HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                for (org.eclipse.jetty.ee8.nested.Handler h : getHandlers()) {
                    h.handle(target, baseRequest, request, response);
                    if (baseRequest.isHandled()) {
                        return;
                    }
                }
            }
        };
        innerChain.addHandler(staticHandler);
        innerChain.addHandler(new ChannelEchoHandler());

        ContextHandler channelCtx = new ContextHandler();
        channelCtx.setContextPath(channelContextPath);
        channelCtx.setAllowNullPathInfo(true);
        channelCtx.setHandler(innerChain);
        channelCtx.setServer(jettyServer);

        org.eclipse.jetty.server.handler.DefaultHandler defaultHandler =
                new org.eclipse.jetty.server.handler.DefaultHandler();
        defaultHandler.setServeFavIcon(false);

        jettyServer.setHandler(new org.eclipse.jetty.server.Handler.Sequence(
                channelCtx.getCoreContextHandler(),
                defaultHandler));

        jettyServer.start();
        return serverConnector.getLocalPort();
    }

    private static class ChannelEchoHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest,
                HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            baseRequest.setHandled(true);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("channel");
        }
    }
}
