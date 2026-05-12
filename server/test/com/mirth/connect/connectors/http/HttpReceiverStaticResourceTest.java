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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.http.HttpURI;
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

public class HttpReceiverStaticResourceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private HttpReceiver receiver;
    private HttpReceiverProperties props;

    @BeforeClass
    public static void setupBeforeClass() {
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

    @Before
    public void setUp() throws Exception {
        receiver = new HttpReceiver();
        props = new HttpReceiverProperties();
        props.setCharset("UTF-8");

        Channel channel = mock(Channel.class);
        doReturn("test-channel-id").when(channel).getChannelId();
        receiver.setChannel(channel);
        receiver.setConnectorProperties(props);

        // Required to avoid NPE in createRequestMessage's binary content type check
        java.lang.reflect.Field arrayField = HttpReceiver.class.getDeclaredField("binaryMimeTypesArray");
        arrayField.setAccessible(true);
        arrayField.set(receiver, new String[0]);
        java.lang.reflect.Field regexField = HttpReceiver.class.getDeclaredField("binaryMimeTypesRegex");
        regexField.setAccessible(true);
        regexField.set(receiver, java.util.regex.Pattern.compile("$^"));

        // configuration is null until onDeploy(); inject a minimal mock
        HttpConfiguration httpConfig = mock(HttpConfiguration.class);
        when(httpConfig.getRequestInformation(any())).thenReturn(new HashMap<>());
        java.lang.reflect.Field configField = HttpReceiver.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        configField.set(receiver, httpConfig);
    }

    // =========================================================================
    // Infrastructure helpers
    // =========================================================================

    /**
     * Reflectively invokes StaticResourceHandler.handle() without starting a real Jetty server.
     * Unwraps InvocationTargetException so callers see the real exception if one escapes.
     */
    private void invokeHandler(HttpStaticResource resource, Request baseRequest,
            HttpServletResponse response) throws Exception {
        Class<?> cls = null;
        for (Class<?> c : HttpReceiver.class.getDeclaredClasses()) {
            if ("StaticResourceHandler".equals(c.getSimpleName())) {
                cls = c;
                break;
            }
        }
        assertNotNull("StaticResourceHandler inner class not found via reflection", cls);

        Constructor<?> ctor = cls.getDeclaredConstructor(HttpReceiver.class, HttpStaticResource.class);
        ctor.setAccessible(true);
        Object handler = ctor.newInstance(receiver, resource);

        Method handle = cls.getMethod("handle", String.class, Request.class,
                HttpServletRequest.class, HttpServletResponse.class);
        try {
            // target and servletRequest are unused inside the handler
            handle.invoke(handler, null, baseRequest, null, response);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }

    /**
     * Builds a mocked Jetty Request for a GET at the given context path.
     * Pass acceptGzip=true to include an Accept-Encoding: gzip header.
     */
    private Request createGetRequest(String contextPath, boolean acceptGzip) {
        Request req = mock(Request.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        when(req.getRemotePort()).thenReturn(12345);
        when(req.getLocalAddr()).thenReturn("127.0.0.1");
        when(req.getLocalPort()).thenReturn(8080);
        when(req.getQueryString()).thenReturn("");
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost" + contextPath));
        when(req.getContentType()).thenReturn(null);
        when(req.getCharacterEncoding()).thenReturn("UTF-8");
        when(req.getParameterMap()).thenReturn(new HashMap<>());
        when(req.getProtocol()).thenReturn("HTTP/1.1");

        HttpURI httpURI = mock(HttpURI.class);
        when(httpURI.getPathQuery()).thenReturn(contextPath);
        when(req.getHttpURI()).thenReturn(httpURI);

        if (acceptGzip) {
            when(req.getHeaderNames()).thenReturn(
                    Collections.enumeration(Collections.singletonList("Accept-Encoding")));
            when(req.getHeaders("Accept-Encoding")).thenReturn(
                    Collections.enumeration(Collections.singletonList("gzip")));
        } else {
            when(req.getHeaderNames()).thenReturn(
                    Collections.enumeration(Collections.emptyList()));
        }

        return req;
    }

    // =========================================================================
    // GROUP A — ResourceType.CUSTOM (inline string value)
    // =========================================================================

    @Test
    public void testCustomResource_smallString_setsContentLength() throws Exception {
        String value = "Hello, static world!";
        int expectedLen = value.getBytes(StandardCharsets.UTF_8).length;

        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.CUSTOM, value, "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", false), response);

        assertEquals(expectedLen, response.capturedContentLength);
    }

    @Test
    public void testCustomResource_largeString_setsContentLength() throws Exception {
        // 40 KB — the regression size that triggered ERR_HTTP2_PROTOCOL_ERROR
        String value = "A".repeat(40 * 1024);
        int expectedLen = value.getBytes(StandardCharsets.UTF_8).length;

        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.CUSTOM, value, "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", false), response);

        assertEquals(expectedLen, response.capturedContentLength);
    }

    @Test
    public void testCustomResource_bodyBytesMatchContentLength() throws Exception {
        String value = "Body content for length verification";
        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.CUSTOM, value, "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", false), response);

        assertEquals(response.capturedContentLength, response.outputStream.size());
    }

    @Test
    public void testCustomResource_bodyContentIsCorrect() throws Exception {
        String value = "Expected body content";
        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.CUSTOM, value, "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", false), response);

        assertEquals(value, response.outputStream.toString("UTF-8"));
    }

    @Test
    public void testCustomResource_gzip_contentLengthNotSet() throws Exception {
        String value = "Gzipped static content";
        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.CUSTOM, value, "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", true), response);

        assertEquals(-1, response.capturedContentLength);
    }

    @Test
    public void testCustomResource_gzip_bodyIsValidGzip() throws Exception {
        String value = "Gzipped static content";
        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.CUSTOM, value, "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", true), response);

        byte[] compressed = response.outputStream.toByteArray();
        String decompressed;
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            decompressed = new String(gzis.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(value, decompressed);
    }

    @Test
    public void testCustomResource_nonGetMethod_noBodyWritten() throws Exception {
        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.CUSTOM, "value", "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        Request postRequest = mock(Request.class);
        when(postRequest.getMethod()).thenReturn("POST");

        invokeHandler(resource, postRequest, response);

        assertEquals(0, response.outputStream.size());
        assertEquals(-1, response.capturedContentLength);
    }

    @Test
    public void testCustomResource_contentTypeIsSet() throws Exception {
        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.CUSTOM, "js content", "application/javascript", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", false), response);

        assertTrue(response.contentTypeValue.contains("application/javascript"));
    }

    @Test
    public void testCustomResource_invalidCharset_fallsBackToTextPlain() throws Exception {
        // An unrecognized charset causes UnsupportedCharsetException in ContentType.parse(),
        // which the handler catches and falls back to text/plain with the connector's default charset.
        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.CUSTOM, "value",
                "text/html; charset=TOTALLY-INVALID-CHARSET-XYZ", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", false), response);

        assertTrue(response.contentTypeValue.contains("text/plain"));
    }

    // =========================================================================
    // GROUP B — ResourceType.FILE
    // =========================================================================

    @Test
    public void testFileResource_smallFile_setsContentLength() throws Exception {
        File f = tempFolder.newFile("small.txt");
        byte[] content = "small file content".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(content); }

        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.FILE, f.getAbsolutePath(), "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", false), response);

        assertEquals(f.length(), response.capturedContentLength);
    }

    @Test
    public void testFileResource_largeFile_setsContentLength() throws Exception {
        // 40 KB — the regression size
        File f = tempFolder.newFile("large.bin");
        byte[] content = new byte[40 * 1024];
        new Random().nextBytes(content);
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(content); }

        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.FILE, f.getAbsolutePath(), "application/octet-stream", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", false), response);

        assertEquals(f.length(), response.capturedContentLength);
    }

    @Test
    public void testFileResource_bodyMatchesFileContent() throws Exception {
        File f = tempFolder.newFile("content.txt");
        byte[] content = "exact file bytes".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(content); }

        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.FILE, f.getAbsolutePath(), "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", false), response);

        assertArrayEquals(content, response.outputStream.toByteArray());
    }

    @Test
    public void testFileResource_gzip_contentLengthNotSet() throws Exception {
        File f = tempFolder.newFile("gzip.txt");
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write("gzip test".getBytes()); }

        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.FILE, f.getAbsolutePath(), "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", true), response);

        assertEquals(-1, response.capturedContentLength);
    }

    @Test
    public void testFileResource_gzip_bodyDecompressesToFileContent() throws Exception {
        File f = tempFolder.newFile("gzip_content.txt");
        byte[] content = "file content for gzip".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(content); }

        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.FILE, f.getAbsolutePath(), "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", true), response);

        byte[] compressed = response.outputStream.toByteArray();
        byte[] decompressed;
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            decompressed = gzis.readAllBytes();
        }
        assertArrayEquals(content, decompressed);
    }

    // =========================================================================
    // GROUP C — ResourceType.DIRECTORY
    // =========================================================================

    @Test
    public void testDirectoryResource_validFile_setsContentLength() throws Exception {
        File dir = tempFolder.newFolder("static");
        File f = new File(dir, "test.js");
        byte[] content = new byte[1024];
        new Random().nextBytes(content);
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(content); }

        HttpStaticResource resource = new HttpStaticResource(
                "/static", ResourceType.DIRECTORY, dir.getAbsolutePath(), "application/javascript", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/static/test.js", false), response);

        assertEquals(f.length(), response.capturedContentLength);
    }

    @Test
    public void testDirectoryResource_validFile_bodyMatchesContent() throws Exception {
        File dir = tempFolder.newFolder("staticbody");
        File f = new File(dir, "data.txt");
        byte[] content = "directory file content".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(content); }

        HttpStaticResource resource = new HttpStaticResource(
                "/staticbody", ResourceType.DIRECTORY, dir.getAbsolutePath(), "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/staticbody/data.txt", false), response);

        assertArrayEquals(content, response.outputStream.toByteArray());
    }

    @Test
    public void testDirectoryResource_subdirectoryRequest_resetsResponse() throws Exception {
        File dir = tempFolder.newFolder("staticdir");
        new File(dir, "sub").mkdir();

        HttpStaticResource resource = new HttpStaticResource(
                "/staticdir", ResourceType.DIRECTORY, dir.getAbsolutePath(), "text/plain", Collections.emptyMap());
        ResettableResponse response = new ResettableResponse();

        // childPath "sub/file.txt" contains "/" — triggers the subdirectory guard
        invokeHandler(resource, createGetRequest("/staticdir/sub/file.txt", false), response);

        assertTrue(response.resetCalled);
        assertEquals(0, response.outputStream.size());
    }

    @Test
    public void testDirectoryResource_missingFile_resetsResponse() throws Exception {
        File dir = tempFolder.newFolder("staticmissing");

        HttpStaticResource resource = new HttpStaticResource(
                "/staticmissing", ResourceType.DIRECTORY, dir.getAbsolutePath(), "text/plain", Collections.emptyMap());
        ResettableResponse response = new ResettableResponse();

        invokeHandler(resource, createGetRequest("/staticmissing/nonexistent.js", false), response);

        assertTrue(response.resetCalled);
        assertEquals(0, response.outputStream.size());
    }

    @Test
    public void testDirectoryResource_directoryRequestedAsFile_resetsResponse() throws Exception {
        File dir = tempFolder.newFolder("staticdirfile");
        new File(dir, "subdir").mkdir();

        HttpStaticResource resource = new HttpStaticResource(
                "/staticdirfile", ResourceType.DIRECTORY, dir.getAbsolutePath(), "text/plain", Collections.emptyMap());
        ResettableResponse response = new ResettableResponse();

        // "subdir" exists but is itself a directory — should reset
        invokeHandler(resource, createGetRequest("/staticdirfile/subdir", false), response);

        assertTrue(response.resetCalled);
        assertEquals(0, response.outputStream.size());
    }

    // =========================================================================
    // GROUP D — Error path / committed response
    // =========================================================================

    @Test
    public void testStaticResource_committedResponse_swallowsIllegalStateException() throws Exception {
        // Non-existent file triggers FileNotFoundException in the handler catch block.
        // The catch block calls reset() — on a committed response that throws ISE.
        // The fix wraps this in try/catch so the ISE is swallowed rather than propagated.
        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.FILE,
                "/this/path/does/not/exist/anywhere.txt", "text/plain", Collections.emptyMap());
        CommittedResponse response = new CommittedResponse();

        invokeHandler(resource, createGetRequest("/resource", false), response); // must not throw
    }

    @Test
    public void testStaticResource_error_returns500WithStackTrace() throws Exception {
        HttpStaticResource resource = new HttpStaticResource(
                "/resource", ResourceType.FILE,
                "/this/path/does/not/exist/anywhere.txt", "text/plain", Collections.emptyMap());
        ContentLengthCapturingResponse response = new ContentLengthCapturingResponse();

        invokeHandler(resource, createGetRequest("/resource", false), response);

        assertEquals(500, response.statusCode);
        String body = response.outputStream.toString("UTF-8");
        assertTrue("Expected stack trace to mention file not found",
                body.contains("FileNotFoundException") || body.contains("NoSuchFileException"));
    }

    // =========================================================================
    // Helper response implementations
    // =========================================================================

    /** Captures the value passed to setContentLength / setContentLengthLong. */
    static class ContentLengthCapturingResponse extends HttpReceiverTest.TestHttpServletResponse {
        long capturedContentLength = -1;

        @Override public void setContentLength(int len)      { capturedContentLength = len; }
        @Override public void setContentLengthLong(long len) { capturedContentLength = len; }
    }

    /** Tracks whether reset() was called; clears the output stream on reset. */
    static class ResettableResponse extends ContentLengthCapturingResponse {
        boolean resetCalled = false;

        @Override
        public void reset() {
            resetCalled = true;
            outputStream.reset();
        }
    }

    /** Simulates a Jetty-committed response: reset() throws IllegalStateException. */
    static class CommittedResponse extends ContentLengthCapturingResponse {
        @Override public boolean isCommitted() { return true; }
        @Override public void reset() { throw new IllegalStateException("Response already committed"); }
    }
}
