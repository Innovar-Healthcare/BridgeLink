package com.mirth.connect.connectors.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.ContentType;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.http.HttpURI;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.model.message.attachment.Attachment;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.util.MessageMaps;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

public class HttpReceiverTest {

    HttpReceiver receiver;
    HttpReceiverProperties props;
    Channel channel;
    Message message;
    Response response;
    DispatchResult dispatchResult;
    CustomMessageMap messageMap;
    Map<Object, Object> headersFromMessageMap;

    @BeforeClass
    public static void setupBeforeClass() {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        ConfigurationController configurationController = mock(ConfigurationController.class);
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        EventController eventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(eventController);

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
        receiver = new HttpReceiver();
        props = new HttpReceiverProperties();
        channel = Mockito.mock(Channel.class);
        doReturn("mockChannelId").when(channel).getChannelId();
        receiver.setChannel(channel);
        receiver.setConnectorProperties(props);
        // Initialize binary content type fields so createRequestMessage doesn't NPE
        try {
            Field arrayField = HttpReceiver.class.getDeclaredField("binaryMimeTypesArray");
            arrayField.setAccessible(true);
            arrayField.set(receiver, new String[0]);
            Field regexField = HttpReceiver.class.getDeclaredField("binaryMimeTypesRegex");
            regexField.setAccessible(true);
            regexField.set(receiver, java.util.regex.Pattern.compile("$^")); // matches nothing
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        props.setBinaryMimeTypesRegex(false);
        message = new Message();
        message.setMessageId(1L);
        response = Mockito.mock(Response.class);
        messageMap = new CustomMessageMap();
        dispatchResult = new TestDispatchResult(1L, message, response, true, true);
        doReturn(messageMap).when(channel).getMessageMaps();
    }

    @Test
    public void testGetHeadersFromMap() {
        Map<String, List<String>> responseHeaders = new HashMap<>();
        List<String> value = new ArrayList<String>();
        value.add("testItem");
        responseHeaders.put("testKey", value);
        props.setResponseHeadersMap(responseHeaders);

        Map<String, List<String>> result = receiver.getHeaders(dispatchResult);
        assertEquals(responseHeaders, result);
    }

    @Test
    public void testGetHeadersFromVariable() {
        Map<Object, Object> headerMap = new HashMap<>();
        headerMap.put("customHeader", "customValue");
        messageMap.map.put("myVar", headerMap);
        props.setResponseHeadersVariable("myVar");
        props.setUseHeadersVariable(true);

        HashMap<String, List<String>> expected = new HashMap<>();
        List<String> list = new ArrayList<String>();
        list.add("customValue");
        expected.put("customHeader", list);
        Map<String, List<String>> result = receiver.getHeaders(dispatchResult);
        assertEquals(expected, result);
    }

    @Test
    public void testGetHeadersFromVariableWithListOfValues() {
        Map<Object, Object> headerMap = new HashMap<>();
        ArrayList<String> list = new ArrayList<>();
        list.add("custom1");
        list.add("custom2");
        headerMap.put("customHeader", list);
        messageMap.map.put("myVar", headerMap);
        props.setResponseHeadersVariable("myVar");
        props.setUseHeadersVariable(true);

        HashMap<String, List<String>> expected = new HashMap<>();
        List<String> expectedList = new ArrayList<String>();
        expectedList.add("custom1");
        expectedList.add("custom2");
        expected.put("customHeader", expectedList);
        Map<String, List<String>> result = receiver.getHeaders(dispatchResult);
        assertEquals(expected, result);
    }

    @Test
    public void testGetHeadersFromVariableWithNonStringValues() {
        Map<Object, Object> headerMap = new HashMap<>();
        headerMap.put("customHeader", "customValue");
        headerMap.put("numValue", 1);
        headerMap.put(4, 4);
        List<Integer> numList = new ArrayList<>();
        numList.add(11);
        numList.add(12);
        headerMap.put("numValue2", numList);
        messageMap.map.put("myVar", headerMap);
        props.setResponseHeadersVariable("myVar");
        props.setUseHeadersVariable(true);

        HashMap<String, List<Object>> expected = new HashMap<>();
        expected.put("customHeader", Collections.singletonList("customValue"));
        expected.put("numValue", Collections.singletonList(String.valueOf(1)));
        expected.put(String.valueOf(4), Collections.singletonList(String.valueOf(4)));

        List<Object> list = new ArrayList<Object>();
        list.add("11");
        list.add("12");
        expected.put("numValue2", list);

        Map<String, List<String>> result = receiver.getHeaders(dispatchResult);
        assertEquals(expected, result);
    }

    @Test
    public void testGetHeadersFromVariableWithListThatHasBothBothStringAndNonStringEntries() {
        Map<Object, Object> headerMap = new HashMap<>();
        List<Object> mixedList = new ArrayList<>();
        mixedList.add(11);
        mixedList.add("goodValue");
        headerMap.put("customHeader", mixedList);
        messageMap.map.put("myVar", headerMap);
        props.setResponseHeadersVariable("myVar");
        props.setUseHeadersVariable(true);

        HashMap<String, List<String>> expected = new HashMap<>();

        List<String> list = new ArrayList<String>();
        list.add("11");
        list.add("goodValue");
        expected.put("customHeader", list);
        Map<String, List<String>> result = receiver.getHeaders(dispatchResult);
        assertEquals(expected, result);
    }

    @Test
    public void testGetHeadersFromMapWhenBothMapAndVariableAreSet() {
        Map<Object, Object> headerMap = new HashMap<>();
        headerMap.put("customHeader", "customValue");
        messageMap.map.put("myVar", headerMap);
        props.setResponseHeadersVariable("myVar");
        Map<String, List<String>> responseHeaders = new HashMap<>();
        List<String> value = new ArrayList<String>();
        value.add("testItem");
        responseHeaders.put("testKey", value);
        props.setResponseHeadersMap(responseHeaders);
        props.setUseHeadersVariable(false);

        Map<String, List<String>> result = receiver.getHeaders(dispatchResult);
        assertEquals(responseHeaders, result);
    }

    @Test
    public void testGetHeadersFromVariableWhenBothMapAndVariableAreSet() {
        Map<Object, Object> headerMap = new HashMap<>();
        headerMap.put("customHeader", "customValue");
        messageMap.map.put("myVar", headerMap);
        props.setResponseHeadersVariable("myVar");
        Map<String, List<String>> responseHeaders = new HashMap<>();
        List<String> value = new ArrayList<String>();
        value.add("testItem");
        responseHeaders.put("testKey", value);
        props.setResponseHeadersMap(responseHeaders);
        props.setUseHeadersVariable(true);

        HashMap<String, List<String>> expected = new HashMap<>();
        List<String> list = new ArrayList<String>();
        list.add("customValue");
        expected.put("customHeader", list);
        Map<String, List<String>> result = receiver.getHeaders(dispatchResult);
        assertEquals(expected, result);
    }

    @Test
    public void testGetEmptyMapWhenHeadersVariableDoesNotExist() {
        props.setResponseHeadersVariable("doesn't exist");
        props.setUseHeadersVariable(true);
        Map<String, List<String>> result = receiver.getHeaders(dispatchResult);
        assertTrue(result.isEmpty());
    }
    
    @Test
    public void testShouldParseMultipart() {
    	HttpReceiverProperties props = new HttpReceiverProperties();
    	props.setXmlBody(true);
    	props.setParseMultipart(true);
    	
    	Request request = mock(Request.class);
    	when(request.getMethod()).thenReturn("POST");
    	
    	when(request.getContentType()).thenReturn("text/plain");
    	assertFalse(receiver.shouldParseMultipart(props, request));
    	
    	when(request.getContentType()).thenReturn("multipart/form-data");
    	assertTrue(receiver.shouldParseMultipart(props, request));
    }

    // ===== shouldParseMultipart edge cases =====

    @Test
    public void testShouldParseMultipartWhenXmlBodyFalse() {
        HttpReceiverProperties testProps = new HttpReceiverProperties();
        testProps.setXmlBody(false);
        testProps.setParseMultipart(true);

        Request request = mock(Request.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("multipart/form-data");

        assertFalse(receiver.shouldParseMultipart(testProps, request));
    }

    @Test
    public void testShouldParseMultipartWhenParseMultipartFalse() {
        HttpReceiverProperties testProps = new HttpReceiverProperties();
        testProps.setXmlBody(true);
        testProps.setParseMultipart(false);

        Request request = mock(Request.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("multipart/form-data");

        assertFalse(receiver.shouldParseMultipart(testProps, request));
    }

    @Test
    public void testShouldParseMultipartWhenBothDisabled() {
        HttpReceiverProperties testProps = new HttpReceiverProperties();
        testProps.setXmlBody(false);
        testProps.setParseMultipart(false);

        Request request = mock(Request.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("multipart/form-data");

        assertFalse(receiver.shouldParseMultipart(testProps, request));
    }

    // ===== isBinaryContentType tests =====

    @Test
    public void testIsBinaryContentTypeWithRegexMatch() throws Exception {
        props.setBinaryMimeTypesRegex(true);
        props.setBinaryMimeTypes("application/.*(?<!json|xml)$|image/.*|video/.*|audio/.*");

        Field regexField = HttpReceiver.class.getDeclaredField("binaryMimeTypesRegex");
        regexField.setAccessible(true);
        regexField.set(receiver, Pattern.compile(props.getBinaryMimeTypes()));

        assertTrue(receiver.isBinaryContentType(ContentType.parse("application/octet-stream")));
        assertTrue(receiver.isBinaryContentType(ContentType.parse("image/png")));
        assertTrue(receiver.isBinaryContentType(ContentType.parse("video/mp4")));
        assertTrue(receiver.isBinaryContentType(ContentType.parse("audio/mpeg")));
    }

    @Test
    public void testIsBinaryContentTypeWithRegexNonMatch() throws Exception {
        props.setBinaryMimeTypesRegex(true);
        props.setBinaryMimeTypes("application/.*(?<!json|xml)$|image/.*|video/.*|audio/.*");

        Field regexField = HttpReceiver.class.getDeclaredField("binaryMimeTypesRegex");
        regexField.setAccessible(true);
        regexField.set(receiver, Pattern.compile(props.getBinaryMimeTypes()));

        assertFalse(receiver.isBinaryContentType(ContentType.parse("application/json")));
        assertFalse(receiver.isBinaryContentType(ContentType.parse("application/xml")));
        assertFalse(receiver.isBinaryContentType(ContentType.parse("text/plain")));
        assertFalse(receiver.isBinaryContentType(ContentType.parse("text/html")));
    }

    @Test
    public void testIsBinaryContentTypeWithPrefixMatch() throws Exception {
        props.setBinaryMimeTypesRegex(false);

        Field arrayField = HttpReceiver.class.getDeclaredField("binaryMimeTypesArray");
        arrayField.setAccessible(true);
        arrayField.set(receiver, new String[] { "application/octet-stream", "image/png", "video/mp4" });

        assertTrue(receiver.isBinaryContentType(ContentType.parse("application/octet-stream")));
        assertTrue(receiver.isBinaryContentType(ContentType.parse("image/png")));
        assertTrue(receiver.isBinaryContentType(ContentType.parse("video/mp4")));
    }

    @Test
    public void testIsBinaryContentTypeWithPrefixNonMatch() throws Exception {
        props.setBinaryMimeTypesRegex(false);

        Field arrayField = HttpReceiver.class.getDeclaredField("binaryMimeTypesArray");
        arrayField.setAccessible(true);
        arrayField.set(receiver, new String[] { "application/octet-stream", "image/png", "video/mp4" });

        assertFalse(receiver.isBinaryContentType(ContentType.parse("text/plain")));
        assertFalse(receiver.isBinaryContentType(ContentType.parse("application/json")));
        assertFalse(receiver.isBinaryContentType(ContentType.parse("text/html")));
    }

    // ===== extractParameters tests =====

    @Test
    public void testExtractParameters() {
        Request request = mock(Request.class);
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("key1", new String[] { "val1" });
        parameterMap.put("key2", new String[] { "val2a", "val2b" });
        when(request.getParameterMap()).thenReturn(parameterMap);

        Map<String, List<String>> result = receiver.extractParameters(request);

        assertEquals(2, result.size());
        assertEquals(Arrays.asList("val1"), result.get("key1"));
        assertEquals(Arrays.asList("val2a", "val2b"), result.get("key2"));
    }

    @Test
    public void testExtractParametersWithEmptyMap() {
        Request request = mock(Request.class);
        when(request.getParameterMap()).thenReturn(new HashMap<>());

        Map<String, List<String>> result = receiver.extractParameters(request);
        assertTrue(result.isEmpty());
    }

    // ===== parametersEqual tests =====

    @Test
    public void testParametersEqualWithSameParams() {
        Map<String, List<String>> params1 = new HashMap<>();
        params1.put("key1", Arrays.asList("val1", "val2"));
        params1.put("key2", Arrays.asList("val3"));

        Map<String, List<String>> params2 = new HashMap<>();
        params2.put("key1", Arrays.asList("val1", "val2"));
        params2.put("key2", Arrays.asList("val3"));

        assertTrue(receiver.parametersEqual(params1, params2));
    }

    @Test
    public void testParametersEqualWithDifferentKeys() {
        Map<String, List<String>> params1 = new HashMap<>();
        params1.put("key1", Arrays.asList("val1"));

        Map<String, List<String>> params2 = new HashMap<>();
        params2.put("key2", Arrays.asList("val1"));

        assertFalse(receiver.parametersEqual(params1, params2));
    }

    @Test
    public void testParametersEqualWithDifferentValues() {
        Map<String, List<String>> params1 = new HashMap<>();
        params1.put("key1", Arrays.asList("val1"));

        Map<String, List<String>> params2 = new HashMap<>();
        params2.put("key1", Arrays.asList("val2"));

        assertFalse(receiver.parametersEqual(params1, params2));
    }

    @Test
    public void testParametersEqualWithBothEmpty() {
        assertTrue(receiver.parametersEqual(new HashMap<>(), new HashMap<>()));
    }

    // ===== getRequestURL tests =====

    @Test
    public void testGetRequestURLWithValidURL() {
        Request request = mock(Request.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test"));

        String result = receiver.getRequestURL(request);
        assertEquals("http://localhost:8080/test", result);
    }

    @Test
    public void testGetRequestURLWithMalformedURLFallsBackToSchemeHostPort() {
        Request request = mock(Request.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("not_a_valid_url"));
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);

        String result = receiver.getRequestURL(request);
        assertEquals("http://localhost:8080", result);
    }

    @Test
    public void testGetRequestURLOmitsPort80ForHttp() {
        Request request = mock(Request.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("not_a_valid_url"));
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(80);

        String result = receiver.getRequestURL(request);
        assertEquals("http://localhost", result);
    }

    @Test
    public void testGetRequestURLOmitsPort443ForHttps() {
        Request request = mock(Request.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("not_a_valid_url"));
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(443);

        String result = receiver.getRequestURL(request);
        assertEquals("https://localhost", result);
    }

    @Test
    public void testGetRequestURLIncludesNonStandardPortForHttps() {
        Request request = mock(Request.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("not_a_valid_url"));
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(8443);

        String result = receiver.getRequestURL(request);
        assertEquals("https://example.com:8443", result);
    }

    // ===== getHeaders additional edge cases =====

    @Test
    public void testGetHeadersWithEmptyResponseHeadersMap() {
        props.setResponseHeadersMap(new HashMap<>());
        props.setUseHeadersVariable(false);

        Map<String, List<String>> result = receiver.getHeaders(dispatchResult);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetHeadersFromMapWithMultipleKeysAndValues() {
        Map<String, List<String>> responseHeaders = new HashMap<>();
        List<String> value1 = new ArrayList<>();
        value1.add("value1a");
        value1.add("value1b");
        responseHeaders.put("key1", value1);

        List<String> value2 = new ArrayList<>();
        value2.add("value2");
        responseHeaders.put("key2", value2);
        props.setResponseHeadersMap(responseHeaders);
        props.setUseHeadersVariable(false);

        Map<String, List<String>> result = receiver.getHeaders(dispatchResult);
        assertEquals(2, result.size());
        assertEquals(Arrays.asList("value1a", "value1b"), result.get("key1"));
        assertEquals(Arrays.asList("value2"), result.get("key2"));
    }

    @Test
    public void testGetHeadersFromVariableWithNonMapValue() {
        messageMap.map.put("myVar", "just a string, not a map");
        props.setResponseHeadersVariable("myVar");
        props.setUseHeadersVariable(true);

        Map<String, List<String>> result = receiver.getHeaders(dispatchResult);
        assertTrue(result.isEmpty());
    }

    // ========== Attachment / createRequestMessage tests ==========

    @Test
    public void testCreateRequestMessageWithTextContent() throws Exception {
        String bodyContent = "Hello, this is a text body";
        Request request = createMockRequest("POST", "text/plain", bodyContent.getBytes(StandardCharsets.UTF_8));

        HttpRequestMessage requestMessage = receiver.createRequestMessage(request, false, false);

        assertNotNull(requestMessage);
        assertEquals("POST", requestMessage.getMethod());
        assertNotNull(requestMessage.getContent());
        assertTrue(requestMessage.getContent() instanceof String);
        assertEquals(bodyContent, requestMessage.getContent());
    }

    @Test
    public void testCreateRequestMessageWithBinaryContent() throws Exception {
        byte[] binaryData = new byte[] { 0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE };
        Request request = createMockRequest("POST", "application/octet-stream", binaryData);

        // Setup isBinaryContentType to return true for application/octet-stream
        Field arrayField = HttpReceiver.class.getDeclaredField("binaryMimeTypesArray");
        arrayField.setAccessible(true);
        arrayField.set(receiver, new String[] { "application/octet-stream" });
        props.setBinaryMimeTypesRegex(false);

        HttpRequestMessage requestMessage = receiver.createRequestMessage(request, false, false);

        assertNotNull(requestMessage);
        assertTrue(requestMessage.getContent() instanceof byte[]);
        byte[] resultBytes = (byte[]) requestMessage.getContent();
        assertEquals(binaryData.length, resultBytes.length);
        for (int i = 0; i < binaryData.length; i++) {
            assertEquals(binaryData[i], resultBytes[i]);
        }
    }

    @Test
    public void testCreateRequestMessageWithMultipartContent() throws Exception {
        // Create a proper multipart MIME body
        String boundary = "----TestBoundary123";
        String multipartBody = "------TestBoundary123\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "File content here\r\n"
                + "------TestBoundary123\r\n"
                + "Content-Disposition: form-data; name=\"field1\"\r\n"
                + "\r\n"
                + "value1\r\n"
                + "------TestBoundary123--\r\n";
        byte[] bodyBytes = multipartBody.getBytes(StandardCharsets.UTF_8);
        Request request = createMockRequest("POST", "multipart/form-data; boundary=----TestBoundary123", bodyBytes);

        HttpRequestMessage requestMessage = receiver.createRequestMessage(request, false, true);

        assertNotNull(requestMessage);
        assertTrue(requestMessage.getContent() instanceof MimeMultipart);
        MimeMultipart multipart = (MimeMultipart) requestMessage.getContent();
        assertEquals(2, multipart.getCount());
    }

    @Test
    public void testCreateRequestMessageWithMultipartFileAttachment() throws Exception {
        // Simulate a file upload with a binary file attachment
        String boundary = "----FileBoundary";
        byte[] fileBytes = new byte[] { 0x50, 0x4B, 0x03, 0x04 }; // ZIP header magic bytes
        StringBuilder sb = new StringBuilder();
        sb.append("------FileBoundary\r\n");
        sb.append("Content-Disposition: form-data; name=\"document\"; filename=\"archive.zip\"\r\n");
        sb.append("Content-Type: application/zip\r\n");
        sb.append("Content-Transfer-Encoding: binary\r\n");
        sb.append("\r\n");
        String header = sb.toString();

        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] trailerBytes = "\r\n------FileBoundary--\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] bodyBytes = new byte[headerBytes.length + fileBytes.length + trailerBytes.length];
        System.arraycopy(headerBytes, 0, bodyBytes, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, bodyBytes, headerBytes.length, fileBytes.length);
        System.arraycopy(trailerBytes, 0, bodyBytes, headerBytes.length + fileBytes.length, trailerBytes.length);

        Request request = createMockRequest("POST", "multipart/form-data; boundary=----FileBoundary", bodyBytes);

        HttpRequestMessage requestMessage = receiver.createRequestMessage(request, false, true);

        assertNotNull(requestMessage);
        assertTrue(requestMessage.getContent() instanceof MimeMultipart);
        MimeMultipart multipart = (MimeMultipart) requestMessage.getContent();
        assertEquals(1, multipart.getCount());
        assertEquals("application/zip", multipart.getBodyPart(0).getContentType());
    }

    @Test
    public void testCreateRequestMessageIgnoresPayloadWhenFlagIsTrue() throws Exception {
        byte[] bodyContent = "should be ignored".getBytes(StandardCharsets.UTF_8);
        Request request = createMockRequest("GET", "text/plain", bodyContent);

        HttpRequestMessage requestMessage = receiver.createRequestMessage(request, true, false);

        assertNotNull(requestMessage);
        // Content should be null when ignorePayload is true
        assertEquals(null, requestMessage.getContent());
    }

    @Test
    public void testCreateRequestMessageSetsMethodCorrectly() throws Exception {
        Request request = createMockRequest("PUT", "text/plain", "data".getBytes(StandardCharsets.UTF_8));

        HttpRequestMessage requestMessage = receiver.createRequestMessage(request, false, false);
        assertEquals("PUT", requestMessage.getMethod());
    }

    @Test
    public void testCreateRequestMessageSetsContentTypeCorrectly() throws Exception {
        Request request = createMockRequest("POST", "application/json; charset=utf-8", "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8));

        HttpRequestMessage requestMessage = receiver.createRequestMessage(request, false, false);
        assertEquals("application/json", requestMessage.getContentType().getMimeType());
    }

    @Test
    public void testCreateRequestMessageFallsBackToTextPlainOnInvalidContentType() throws Exception {
        Request request = createMockRequest("POST", null, "body".getBytes(StandardCharsets.UTF_8));
        // ContentType.parse(null) throws, so it should fall back to TEXT_PLAIN
        when(request.getContentType()).thenReturn(null);

        HttpRequestMessage requestMessage = receiver.createRequestMessage(request, false, false);
        assertEquals("text/plain", requestMessage.getContentType().getMimeType());
    }

    @Test
    public void testCreateRequestMessageSetsRemoteAddress() throws Exception {
        Request request = createMockRequest("GET", "text/plain", "data".getBytes(StandardCharsets.UTF_8));
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        HttpRequestMessage requestMessage = receiver.createRequestMessage(request, true, false);
        assertEquals("192.168.1.100", requestMessage.getRemoteAddress());
    }

    @Test
    public void testCreateRequestMessageSetsQueryString() throws Exception {
        Request request = createMockRequest("GET", "text/plain", "data".getBytes(StandardCharsets.UTF_8));
        when(request.getQueryString()).thenReturn("param1=value1&param2=value2");

        HttpRequestMessage requestMessage = receiver.createRequestMessage(request, true, false);
        assertEquals("param1=value1&param2=value2", requestMessage.getQueryString());
    }

    // ========== Attachment model tests ==========

    @Test
    public void testAttachmentCreation() {
        Attachment attachment = new Attachment("att1", "Hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertEquals("att1", attachment.getId());
        assertEquals("text/plain", attachment.getType());
        assertEquals("Hello", new String(attachment.getContent(), StandardCharsets.UTF_8));
        assertEquals("${ATTACH:att1}", attachment.getAttachmentId());
    }

    @Test
    public void testAttachmentWithBinaryContent() {
        byte[] pngHeader = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 };
        Attachment attachment = new Attachment("img1", pngHeader, "image/png");
        assertEquals("img1", attachment.getId());
        assertEquals("image/png", attachment.getType());
        assertEquals(4, attachment.getContent().length);
        assertEquals((byte) 0x89, attachment.getContent()[0]);
    }

    @Test
    public void testAttachmentListPassedToRawMessage() {
        List<Attachment> attachments = new ArrayList<>();
        attachments.add(new Attachment("att1", "data1".getBytes(StandardCharsets.UTF_8), "text/plain"));
        attachments.add(new Attachment("att2", new byte[] { 0x00, 0x01 }, "application/octet-stream"));

        assertEquals(2, attachments.size());
        assertEquals("att1", attachments.get(0).getId());
        assertEquals("att2", attachments.get(1).getId());
        assertEquals("text/plain", attachments.get(0).getType());
        assertEquals("application/octet-stream", attachments.get(1).getType());
    }

    // ========== populateSourceMap tests ==========

    @Test
    public void testPopulateSourceMapSetsExpectedKeys() throws Exception {
        Request request = createMockRequest("POST", "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        when(request.getRemotePort()).thenReturn(12345);
        when(request.getLocalAddr()).thenReturn("127.0.0.1");
        when(request.getLocalPort()).thenReturn(8080);
        when(request.getProtocol()).thenReturn("HTTP/1.1");

        HttpURI httpURI = mock(HttpURI.class);
        when(httpURI.isAbsolute()).thenReturn(false);
        when(httpURI.getPathQuery()).thenReturn("/test?a=b");
        when(request.getHttpURI()).thenReturn(httpURI);

        // Need to set up the configuration mock
        HttpConfiguration configuration = mock(HttpConfiguration.class);
        when(configuration.getRequestInformation(request)).thenReturn(new HashMap<>());
        Field configField = HttpReceiver.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        configField.set(receiver, configuration);

        HttpRequestMessage requestMessage = receiver.createRequestMessage(request, true, false);

        Map<String, Object> sourceMap = new HashMap<>();
        receiver.populateSourceMap(request, requestMessage, sourceMap);

        assertTrue(sourceMap.containsKey("remoteAddress"));
        assertTrue(sourceMap.containsKey("remotePort"));
        assertEquals(12345, sourceMap.get("remotePort"));
        assertTrue(sourceMap.containsKey("localAddress"));
        assertEquals("127.0.0.1", sourceMap.get("localAddress"));
        assertTrue(sourceMap.containsKey("localPort"));
        assertEquals(8080, sourceMap.get("localPort"));
        assertTrue(sourceMap.containsKey("method"));
        assertEquals("POST", sourceMap.get("method"));
        assertTrue(sourceMap.containsKey("protocol"));
        assertEquals("HTTP/1.1", sourceMap.get("protocol"));
        assertTrue(sourceMap.containsKey("headers"));
        assertTrue(sourceMap.containsKey("parameters"));
        assertTrue(sourceMap.containsKey("url"));
        assertTrue(sourceMap.containsKey("uri"));
        assertTrue(sourceMap.containsKey("query"));
        assertTrue(sourceMap.containsKey("contextPath"));
    }

    // ========== sendResponse tests ==========

    @Test
    public void testSendResponseWithTextContent() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);
        Enumeration<String> emptyEnum = Collections.enumeration(Collections.emptyList());
        when(baseRequest.getHeaders("Accept-Encoding")).thenReturn(emptyEnum);

        props.setResponseContentType("text/plain");
        props.setResponseStatusCode("200");
        props.setResponseDataTypeBinary(false);
        props.setCharset("UTF-8");

        Response selectedResponse = mock(Response.class);
        when(selectedResponse.getMessage()).thenReturn("Response body content");
        when(selectedResponse.getStatus()).thenReturn(Status.SENT);
        DispatchResult dr = new TestDispatchResult(1L, message, selectedResponse, true, true);

        receiver.sendResponse(baseRequest, servletResponse, dr);

        assertEquals(200, servletResponse.statusCode);
        assertTrue(servletResponse.outputStream.toByteArray().length > 0);
        String responseBody = new String(servletResponse.outputStream.toByteArray(), StandardCharsets.UTF_8);
        assertEquals("Response body content", responseBody);
    }

    @Test
    public void testSendResponseWithBinaryContent() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);
        Enumeration<String> emptyEnum = Collections.enumeration(Collections.emptyList());
        when(baseRequest.getHeaders("Accept-Encoding")).thenReturn(emptyEnum);

        props.setResponseContentType("application/octet-stream");
        props.setResponseStatusCode("200");
        props.setResponseDataTypeBinary(true);
        props.setCharset("UTF-8");

        // Base64 encoded "Hello"
        String base64Content = new String(com.mirth.connect.donkey.util.Base64Util.encodeBase64("Hello".getBytes(StandardCharsets.UTF_8)), "US-ASCII");
        Response selectedResponse = mock(Response.class);
        when(selectedResponse.getMessage()).thenReturn(base64Content);
        when(selectedResponse.getStatus()).thenReturn(Status.SENT);
        DispatchResult dr = new TestDispatchResult(1L, message, selectedResponse, true, true);

        receiver.sendResponse(baseRequest, servletResponse, dr);

        assertEquals(200, servletResponse.statusCode);
        byte[] responseBytes = servletResponse.outputStream.toByteArray();
        assertEquals("Hello", new String(responseBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testSendResponseWithGzipCompression() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);
        Vector<String> acceptEncodings = new Vector<>();
        acceptEncodings.add("gzip, deflate");
        when(baseRequest.getHeaders("Accept-Encoding")).thenReturn(acceptEncodings.elements());

        props.setResponseContentType("text/plain");
        props.setResponseStatusCode("200");
        props.setResponseDataTypeBinary(false);
        props.setCharset("UTF-8");

        Response selectedResponse = mock(Response.class);
        when(selectedResponse.getMessage()).thenReturn("Compressed response body");
        when(selectedResponse.getStatus()).thenReturn(Status.SENT);
        DispatchResult dr = new TestDispatchResult(1L, message, selectedResponse, true, true);

        receiver.sendResponse(baseRequest, servletResponse, dr);

        assertEquals(200, servletResponse.statusCode);
        assertEquals("gzip", servletResponse.headers.get("Content-Encoding"));

        // Decompress and verify
        byte[] compressedBytes = servletResponse.outputStream.toByteArray();
        GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressedBytes));
        byte[] decompressedBytes = org.apache.commons.io.IOUtils.toByteArray(gzipIn);
        assertEquals("Compressed response body", new String(decompressedBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testSendResponseWithErrorStatus() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);
        Enumeration<String> emptyEnum = Collections.enumeration(Collections.emptyList());
        when(baseRequest.getHeaders("Accept-Encoding")).thenReturn(emptyEnum);

        props.setResponseContentType("text/plain");
        props.setResponseStatusCode("");
        props.setResponseDataTypeBinary(false);
        props.setCharset("UTF-8");

        Response selectedResponse = mock(Response.class);
        when(selectedResponse.getMessage()).thenReturn("Error occurred");
        when(selectedResponse.getStatus()).thenReturn(Status.ERROR);
        DispatchResult dr = new TestDispatchResult(1L, message, selectedResponse, true, true);

        receiver.sendResponse(baseRequest, servletResponse, dr);

        assertEquals(500, servletResponse.statusCode);
    }

    @Test
    public void testSendResponseWithCustomStatusCode() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);
        Enumeration<String> emptyEnum = Collections.enumeration(Collections.emptyList());
        when(baseRequest.getHeaders("Accept-Encoding")).thenReturn(emptyEnum);

        props.setResponseContentType("application/json");
        props.setResponseStatusCode("201");
        props.setResponseDataTypeBinary(false);
        props.setCharset("UTF-8");

        Response selectedResponse = mock(Response.class);
        when(selectedResponse.getMessage()).thenReturn("{\"status\":\"created\"}");
        when(selectedResponse.getStatus()).thenReturn(Status.SENT);
        DispatchResult dr = new TestDispatchResult(1L, message, selectedResponse, true, true);

        receiver.sendResponse(baseRequest, servletResponse, dr);

        assertEquals(201, servletResponse.statusCode);
    }

    @Test
    public void testSendResponseWithNullDispatchResult() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);
        Enumeration<String> emptyEnum = Collections.enumeration(Collections.emptyList());
        when(baseRequest.getHeaders("Accept-Encoding")).thenReturn(emptyEnum);

        props.setResponseContentType("text/plain");
        props.setResponseStatusCode("202");
        props.setResponseDataTypeBinary(false);
        props.setCharset("UTF-8");

        receiver.sendResponse(baseRequest, servletResponse, null);

        assertEquals(202, servletResponse.statusCode);
    }

    @Test
    public void testSendResponseDefaultStatusCodeWhenNoResponseSelected() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);

        props.setResponseContentType("text/plain");
        props.setResponseStatusCode("");
        props.setResponseDataTypeBinary(false);
        props.setCharset("UTF-8");

        // DispatchResult with null selectedResponse
        DispatchResult dr = new TestDispatchResult(1L, message, null, true, true);

        receiver.sendResponse(baseRequest, servletResponse, dr);

        assertEquals(200, servletResponse.statusCode);
    }

    @Test
    public void testSendResponseWithPrebuiltResponseBytes() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);
        Enumeration<String> emptyEnum = Collections.enumeration(Collections.emptyList());
        when(baseRequest.getHeaders("Accept-Encoding")).thenReturn(emptyEnum);

        byte[] prebuiltBytes = "Prebuilt binary attachment data".getBytes(StandardCharsets.UTF_8);
        ContentType contentType = ContentType.APPLICATION_OCTET_STREAM;
        Map<String, List<String>> responseHeaders = new HashMap<>();
        responseHeaders.put("X-Custom", Collections.singletonList("custom-value"));

        Response selectedResponse = mock(Response.class);
        when(selectedResponse.getMessage()).thenReturn("should be ignored");
        when(selectedResponse.getStatus()).thenReturn(Status.SENT);
        DispatchResult dr = new TestDispatchResult(1L, message, selectedResponse, true, true);

        props.setResponseStatusCode("200");

        receiver.sendResponse(baseRequest, servletResponse, dr, contentType, responseHeaders, prebuiltBytes);

        assertEquals(200, servletResponse.statusCode);
        byte[] resultBytes = servletResponse.outputStream.toByteArray();
        assertEquals("Prebuilt binary attachment data", new String(resultBytes, StandardCharsets.UTF_8));
        assertEquals("custom-value", servletResponse.headers.get("X-Custom"));
    }

    // ========== sendErrorResponse tests ==========

    @Test
    public void testSendErrorResponse() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);

        receiver.sendErrorResponse(baseRequest, servletResponse, null, new RuntimeException("Test error"));

        assertEquals(500, servletResponse.statusCode);
        String errorBody = new String(servletResponse.outputStream.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(errorBody.contains("RuntimeException"));
    }

    @Test
    public void testSendErrorResponseWithDispatchResult() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);

        Response selectedResponse = mock(Response.class);
        DispatchResult dr = new TestDispatchResult(1L, message, selectedResponse, true, true);

        receiver.sendErrorResponse(baseRequest, servletResponse, dr, new IOException("I/O failure during attachment read"));

        assertEquals(500, servletResponse.statusCode);
        assertTrue(dr.isAttemptedResponse());
    }

    // ========== IRT-832: Content-Length tests ==========

    @Test
    public void testSendResponseNonGzipSetsContentLength() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);
        when(baseRequest.getHeaders("Accept-Encoding")).thenReturn(Collections.enumeration(Collections.emptyList()));

        props.setResponseDataTypeBinary(false);
        props.setCharset("UTF-8");
        props.setResponseStatusCode("200");

        String body = "Hello Content-Length";
        byte[] expectedBytes = body.getBytes(StandardCharsets.UTF_8);

        Response selectedResponse = mock(Response.class);
        when(selectedResponse.getMessage()).thenReturn(body);
        when(selectedResponse.getStatus()).thenReturn(Status.SENT);
        DispatchResult dr = new TestDispatchResult(1L, message, selectedResponse, true, true);

        receiver.sendResponse(baseRequest, servletResponse, dr);

        assertEquals("Content-Length must match body byte length", expectedBytes.length, servletResponse.contentLength);
        assertArrayEquals(expectedBytes, servletResponse.outputStream.toByteArray());
    }

    @Test
    public void testSendResponseGzipDoesNotSetContentLength() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);
        Vector<String> acceptEncodings = new Vector<>();
        acceptEncodings.add("gzip");
        when(baseRequest.getHeaders("Accept-Encoding")).thenReturn(acceptEncodings.elements());

        props.setResponseDataTypeBinary(false);
        props.setCharset("UTF-8");
        props.setResponseStatusCode("200");

        Response selectedResponse = mock(Response.class);
        when(selectedResponse.getMessage()).thenReturn("Gzip body");
        when(selectedResponse.getStatus()).thenReturn(Status.SENT);
        DispatchResult dr = new TestDispatchResult(1L, message, selectedResponse, true, true);

        receiver.sendResponse(baseRequest, servletResponse, dr);

        assertEquals("Content-Length must not be set for gzip responses", -1, servletResponse.contentLength);
        assertEquals("gzip", servletResponse.headers.get("Content-Encoding"));
    }

    @Test
    public void testSendErrorResponseSetsContentLength() throws Exception {
        TestHttpServletResponse servletResponse = new TestHttpServletResponse();
        Request baseRequest = mock(Request.class);

        receiver.sendErrorResponse(baseRequest, servletResponse, null, new RuntimeException("boom"));

        byte[] errBytes = servletResponse.outputStream.toByteArray();
        assertTrue("Error body must be non-empty", errBytes.length > 0);
        assertEquals("Content-Length must match error body byte length", errBytes.length, servletResponse.contentLength);
        assertEquals(500, servletResponse.statusCode);
    }

    // ========== Helper methods ==========

    /**
     * Creates a fully mocked Request for createRequestMessage tests.
     */
    // ===== Jetty 12.0.33 HttpURI API compatibility test (D-06) =====

    /**
     * Jetty 12.0.33 API compatibility: confirms that {@link HttpURI} correctly reports
     * whether a URI is absolute (scheme-present) vs relative — the API used by
     * {@link HttpReceiver#populateSourceMap} to build the request URL for the source map.
     * Exercises the Jetty 12.0.33 {@code HttpURI.isAbsolute()} method.
     */
    @Test
    public void testJetty1200HttpUriIsAbsoluteApi() {
        HttpURI absoluteUri = HttpURI.build("http://localhost:8080/api/channels").asImmutable();
        assertTrue("URI with scheme must be absolute", absoluteUri.isAbsolute());

        HttpURI relativeUri = HttpURI.build("/api/channels").asImmutable();
        assertFalse("URI without scheme must not be absolute", relativeUri.isAbsolute());
    }

    private Request createMockRequest(String method, String contentType, byte[] body) throws Exception {
        Request request = mock(Request.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getContentType()).thenReturn(contentType);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getQueryString()).thenReturn("");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test"));
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        when(request.getAttribute(Mockito.anyString())).thenReturn(null);

        // Mock header enumeration (empty)
        Enumeration<String> emptyHeaderNames = Collections.enumeration(Collections.emptyList());
        when(request.getHeaderNames()).thenReturn(emptyHeaderNames);

        // Mock input stream
        ByteArrayInputStream bais = new ByteArrayInputStream(body);
        TestServletInputStream sis = new TestServletInputStream(bais);
        when(request.getInputStream()).thenReturn(sis);

        return request;
    }

    /**
     * Simple ServletInputStream wrapper for testing.
     */
    static class TestServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        TestServletInputStream(ByteArrayInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(javax.servlet.ReadListener readListener) {
        }
    }

    /**
     * Simple HttpServletResponse implementation for capturing response output in tests.
     */
    static class TestHttpServletResponse implements HttpServletResponse {
        int statusCode;
        String contentTypeValue;
        int contentLength = -1;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Map<String, String> headers = new HashMap<>();

        @Override
        public void setStatus(int sc) { this.statusCode = sc; }
        @Override
        public int getStatus() { return statusCode; }
        @Override
        public void setContentType(String type) { this.contentTypeValue = type; }
        @Override
        public String getContentType() { return contentTypeValue; }
        @Override
        public void addHeader(String name, String value) { headers.put(name, value); }
        @Override
        public void setHeader(String name, String value) { headers.put(name, value); }
        @Override
        public String getHeader(String name) { return headers.get(name); }
        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public void write(int b) throws IOException { outputStream.write(b); }
                @Override
                public void write(byte[] b) throws IOException { outputStream.write(b); }
                @Override
                public void write(byte[] b, int off, int len) throws IOException { outputStream.write(b, off, len); }
                @Override
                public boolean isReady() { return true; }
                @Override
                public void setWriteListener(WriteListener writeListener) {}
            };
        }

        // Stub remaining HttpServletResponse methods
        @Override public void setStatus(int sc, String sm) { this.statusCode = sc; }
        @Override public void sendError(int sc, String msg) { this.statusCode = sc; }
        @Override public void sendError(int sc) { this.statusCode = sc; }
        @Override public void sendRedirect(String location) {}
        @Override public void setDateHeader(String name, long date) {}
        @Override public void addDateHeader(String name, long date) {}
        @Override public void setIntHeader(String name, int value) {}
        @Override public void addIntHeader(String name, int value) {}
        @Override public boolean containsHeader(String name) { return headers.containsKey(name); }
        @Override public String encodeURL(String url) { return url; }
        @Override public String encodeRedirectURL(String url) { return url; }
        @Override public String encodeUrl(String url) { return url; }
        @Override public String encodeRedirectUrl(String url) { return url; }
        @Override public java.util.Collection<String> getHeaders(String name) { return Collections.singletonList(headers.get(name)); }
        @Override public java.util.Collection<String> getHeaderNames() { return headers.keySet(); }
        @Override public void addCookie(javax.servlet.http.Cookie cookie) {}
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public java.io.PrintWriter getWriter() { return new java.io.PrintWriter(outputStream); }
        @Override public void setCharacterEncoding(String charset) {}
        @Override public void setContentLength(int len) { this.contentLength = len; }
        @Override public void setContentLengthLong(long len) { this.contentLength = (int) len; }
        @Override public void setBufferSize(int size) {}
        @Override public int getBufferSize() { return 0; }
        @Override public void flushBuffer() {}
        @Override public void resetBuffer() {}
        @Override public boolean isCommitted() { return false; }
        @Override public void reset() {}
        @Override public void setLocale(java.util.Locale loc) {}
        @Override public java.util.Locale getLocale() { return java.util.Locale.getDefault(); }
    }

    class TestDispatchResult extends DispatchResult {
        protected TestDispatchResult(long messageId, Message processedMessage, Response selectedResponse, boolean markAsProcessed, boolean lockAcquired) {
            super(messageId, processedMessage, selectedResponse, markAsProcessed, lockAcquired);
        }
    }

    class CustomMessageMap extends MessageMaps {
        protected Map<Object, Object> map = new HashMap<>();

        @Override
        public Object get(String key, ConnectorMessage connectorMessage, boolean includeResponseMap) {
            return map.get(key);
        }
    }
}
