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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jetty.ee8.nested.AbstractHandler;
import org.eclipse.jetty.ee8.nested.ContextHandler;
import org.eclipse.jetty.ee8.nested.HandlerCollection;
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
    // Helpers — Jetty server setup
    // =========================================================================

    /**
     * Starts a real Jetty 12 server on a random loopback port, replicating the handler chain
     * from {@link HttpReceiver#onStart()} but with {@code StaticResourceHandler} for a single
     * static resource.  Uses reflection to instantiate the private inner class.
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

        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(resourceContext);

        ContextHandler rootContext = new ContextHandler();
        rootContext.setContextPath("/");
        rootContext.setHandler(handlers);
        jettyServer.setHandler(rootContext.getCoreContextHandler());

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
}
