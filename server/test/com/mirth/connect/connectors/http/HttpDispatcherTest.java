package com.mirth.connect.connectors.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.http.entity.ContentType;
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
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.util.MessageMaps;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

public class HttpDispatcherTest {

    HttpDispatcher dispatcher;
    HttpDispatcherProperties props;
    Channel channel;
    Message message;
    Response response;
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
        dispatcher = new HttpDispatcher();
        props = new HttpDispatcherProperties();
        channel = Mockito.mock(Channel.class);
        doReturn("mockChannelId").when(channel).getChannelId();
        dispatcher.setChannel(channel);
        dispatcher.setConnectorProperties(props);
        message = new Message();
        message.setMessageId(1L);
        response = Mockito.mock(Response.class);
        messageMap = new CustomMessageMap(new HashMap<>());
        doReturn(messageMap).when(channel).getMessageMaps();
    }

    @Test
    public void testGetHeadersFromMap() {
        Map<String, List<String>> responseHeaders = new HashMap<>();
        List<String> value = new ArrayList<String>();
        value.add("testItem");
        responseHeaders.put("testKey", value);
        props.setHeadersMap(responseHeaders);

        Map<String, List<String>> result = dispatcher.getHeaders(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(responseHeaders, result);
    }

    @Test
    public void testGetHeadersFromVariable() {
        Map<Object, Object> headerMap = new HashMap<>();
        headerMap.put("customHeader", "customValue");
        messageMap.map.put("myVar", headerMap);
        props.setHeadersVariable("myVar");
        props.setUseHeadersVariable(true);

        HashMap<String, List<String>> expected = new HashMap<>();
        List<String> list = new ArrayList<String>();
        list.add("customValue");
        expected.put("customHeader", list);
        Map<String, List<String>> result = dispatcher.getHeaders(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(expected, result);
    }

    @Test
    public void testGetHeadersFromVariableHandlingInvalidValues() {
        Map<Object, Object> headerMap = new HashMap<>();
        headerMap.put("customHeader", "customValue");
        headerMap.put("numericValue", 1);
        headerMap.put(4, 4);
        messageMap.map.put("myVar", headerMap);
        props.setHeadersVariable("myVar");
        props.setUseHeadersVariable(true);

        HashMap<String, List<String>> expected = new HashMap<>();
        expected.put("customHeader", Collections.singletonList("customValue"));
        expected.put("numericValue", Collections.singletonList(String.valueOf(1)));
        expected.put(String.valueOf(4), Collections.singletonList(String.valueOf(4)));
        Map<String, List<String>> result = dispatcher.getHeaders(props, Mockito.mock(ConnectorMessage.class));
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
        props.setHeadersVariable("myVar");
        props.setUseHeadersVariable(true);

        HashMap<String, List<String>> expected = new HashMap<>();
        List<String> list = new ArrayList<String>();
        list.add("11");
        list.add("goodValue");
        expected.put("customHeader", list);
        Map<String, List<String>> result = dispatcher.getHeaders(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(expected, result);
    }

    @Test
    public void testGetHeadersFromMapWhenBothMapAndVariableAreSet() {
        Map<Object, Object> headerMap = new HashMap<>();
        headerMap.put("customHeader", "customValue");
        messageMap.map.put("myVar", headerMap);
        props.setHeadersVariable("myVar");
        Map<String, List<String>> responseHeaders = new HashMap<>();
        List<String> value = new ArrayList<String>();
        value.add("testItem");
        responseHeaders.put("testKey", value);
        props.setHeadersMap(responseHeaders);
        props.setUseHeadersVariable(false);

        Map<String, List<String>> result = dispatcher.getHeaders(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(responseHeaders, result);
    }

    @Test
    public void testGetHeadersFromVariableWhenBothMapAndVariableAreSet() {
        Map<Object, Object> headerMap = new HashMap<>();
        headerMap.put("customHeader", "customValue");
        messageMap.map.put("myVar", headerMap);
        props.setHeadersVariable("myVar");
        Map<String, List<String>> responseHeaders = new HashMap<>();
        List<String> value = new ArrayList<String>();
        value.add("testItem");
        responseHeaders.put("testKey", value);
        props.setHeadersMap(responseHeaders);
        props.setUseHeadersVariable(true);

        HashMap<String, List<String>> expected = new HashMap<>();
        List<String> list = new ArrayList<String>();
        list.add("customValue");
        expected.put("customHeader", list);
        Map<String, List<String>> result = dispatcher.getHeaders(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(expected, result);
    }

    @Test
    public void testGetParametersFromMap() {
        Map<String, List<String>> parameters = new HashMap<>();
        List<String> value = new ArrayList<String>();
        value.add("testItem");
        parameters.put("testKey", value);
        props.setParametersMap(parameters);

        Map<String, List<String>> result = dispatcher.getParameters(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(parameters, result);
    }

    @Test
    public void testGetParametersFromVariable() {
        Map<Object, Object> parameters = new HashMap<>();
        parameters.put("customParam", "customValue");
        messageMap.map.put("myVar", parameters);
        props.setParametersVariable("myVar");
        props.setUseParametersVariable(true);

        HashMap<String, List<String>> expected = new HashMap<>();
        List<String> list = new ArrayList<String>();
        list.add("customValue");
        expected.put("customParam", list);
        Map<String, List<String>> result = dispatcher.getParameters(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(expected, result);
    }

    @Test
    public void testGetParametersWithNonStringValues() {
        Map<Object, Object> parameters = new HashMap<>();
        parameters.put("customParam", "customValue");
        parameters.put("numValue", 1);
        parameters.put(4, 4);
        List<Integer> numList = new ArrayList<>();
        numList.add(11);
        numList.add(12);
        parameters.put("numValue2", numList);
        messageMap.map.put("myVar", parameters);
        props.setParametersVariable("myVar");
        props.setUseParametersVariable(true);

        HashMap<String, List<Object>> expected = new HashMap<>();
        expected.put("customParam", Collections.singletonList("customValue"));
        expected.put("numValue", Collections.singletonList(String.valueOf(1)));
        expected.put(String.valueOf(4), Collections.singletonList(String.valueOf(4)));

        List<Object> list = new ArrayList<Object>();
        list.add("11");
        list.add("12");
        expected.put("numValue2", list);

        Map<String, List<String>> result = dispatcher.getParameters(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(expected, result);
    }

    @Test
    public void testGetParametersFromVariableWithListThatHasBothBothStringAndNonStringEntries() {
        Map<Object, Object> parameters = new HashMap<>();
        List<Object> mixedList = new ArrayList<>();
        mixedList.add(11);
        mixedList.add("goodValue");
        parameters.put("customParam", mixedList);
        messageMap.map.put("myVar", parameters);
        props.setParametersVariable("myVar");
        props.setUseParametersVariable(true);

        HashMap<String, List<String>> expected = new HashMap<>();
        List<String> list = new ArrayList<String>();
        list.add("11");
        list.add("goodValue");
        expected.put("customParam", list);
        Map<String, List<String>> result = dispatcher.getParameters(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(expected, result);
    }

    @Test
    public void testGetParametersFromMapWhenBothMapAndVariableAreSet() {
        Map<Object, Object> varMap = new HashMap<>();
        varMap.put("customParam", "customValue");
        messageMap.map.put("myVar", varMap);
        props.setParametersVariable("myVar");
        Map<String, List<String>> parameters = new HashMap<>();
        List<String> value = new ArrayList<String>();
        value.add("testItem");
        parameters.put("testKey", value);
        props.setParametersMap(parameters);
        props.setUseParametersVariable(false);

        Map<String, List<String>> result = dispatcher.getParameters(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(parameters, result);
    }

    @Test
    public void testGetParametersFromVariableWhenBothMapAndVariableAreSet() {
        Map<Object, Object> varMap = new HashMap<>();
        varMap.put("customParam", "customValue");
        messageMap.map.put("myVar", varMap);
        props.setParametersVariable("myVar");
        Map<String, List<String>> parameters = new HashMap<>();
        List<String> value = new ArrayList<String>();
        value.add("testItem");
        parameters.put("testKey", value);
        props.setParametersMap(parameters);
        props.setUseParametersVariable(true);

        HashMap<String, List<String>> expected = new HashMap<>();
        List<String> list = new ArrayList<String>();
        list.add("customValue");
        expected.put("customParam", list);
        Map<String, List<String>> result = dispatcher.getParameters(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(expected, result);
    }

    @Test
    public void testGetEmptyMapWhenHeadersVariableDoesNotExist() {
        props.setHeadersVariable("doesn't exist");
        props.setUseHeadersVariable(true);
        Map<String, List<String>> result = dispatcher.getHeaders(props, Mockito.mock(ConnectorMessage.class));
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetEmptyMapWhenParameterVariableDoesNotExist() {
        props.setParametersVariable("doesn't exist");
        props.setUseParametersVariable(true);
        Map<String, List<String>> result = dispatcher.getParameters(props, Mockito.mock(ConnectorMessage.class));
        assertTrue(result.isEmpty());
    }
    
    @Test
    public void testShouldParseMultipart() {
    	HttpDispatcherProperties props = new HttpDispatcherProperties();
    	props.setResponseXmlBody(true);
    	props.setResponseParseMultipart(true);
    	
    	assertFalse(dispatcher.shouldParseMultipart(props, "text/plain"));
    	assertTrue(dispatcher.shouldParseMultipart(props, "multipart/form-data"));
    }

    // ===== shouldParseMultipart edge cases =====

    @Test
    public void testShouldParseMultipartWithMixedMultipart() {
        HttpDispatcherProperties testProps = new HttpDispatcherProperties();
        testProps.setResponseXmlBody(true);
        testProps.setResponseParseMultipart(true);

        assertTrue(dispatcher.shouldParseMultipart(testProps, "multipart/mixed"));
    }

    @Test
    public void testShouldNotParseMultipartWhenXmlBodyFalse() {
        HttpDispatcherProperties testProps = new HttpDispatcherProperties();
        testProps.setResponseXmlBody(false);
        testProps.setResponseParseMultipart(true);

        assertFalse(dispatcher.shouldParseMultipart(testProps, "multipart/form-data"));
    }

    @Test
    public void testShouldNotParseMultipartWhenParseMultipartFalse() {
        HttpDispatcherProperties testProps = new HttpDispatcherProperties();
        testProps.setResponseXmlBody(true);
        testProps.setResponseParseMultipart(false);

        assertFalse(dispatcher.shouldParseMultipart(testProps, "multipart/form-data"));
    }

    @Test
    public void testShouldNotParseMultipartWhenBothDisabled() {
        HttpDispatcherProperties testProps = new HttpDispatcherProperties();
        testProps.setResponseXmlBody(false);
        testProps.setResponseParseMultipart(false);

        assertFalse(dispatcher.shouldParseMultipart(testProps, "multipart/form-data"));
    }

    @Test
    public void testShouldNotParseMultipartForNonMultipartMimeTypes() {
        HttpDispatcherProperties testProps = new HttpDispatcherProperties();
        testProps.setResponseXmlBody(true);
        testProps.setResponseParseMultipart(true);

        assertFalse(dispatcher.shouldParseMultipart(testProps, "text/plain"));
        assertFalse(dispatcher.shouldParseMultipart(testProps, "application/json"));
        assertFalse(dispatcher.shouldParseMultipart(testProps, "application/xml"));
        assertFalse(dispatcher.shouldParseMultipart(testProps, "text/html"));
    }

    // ===== isBinaryContentType tests (private, tested via reflection) =====

    @Test
    public void testIsBinaryContentTypeWithRegexMatch() throws Exception {
        props.setResponseBinaryMimeTypesRegex(true);

        Field regexMapField = HttpDispatcher.class.getDeclaredField("binaryMimeTypesRegexMap");
        regexMapField.setAccessible(true);
        regexMapField.set(dispatcher, new ConcurrentHashMap<String, Pattern>());

        Method isBinaryMethod = HttpDispatcher.class.getDeclaredMethod("isBinaryContentType", String.class, ContentType.class);
        isBinaryMethod.setAccessible(true);

        String binaryMimeTypes = "application/.*(?<!json|xml)$|image/.*|video/.*|audio/.*";

        assertTrue((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("application/octet-stream")));
        assertTrue((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("image/png")));
        assertTrue((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("video/mp4")));
        assertTrue((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("audio/mpeg")));
    }

    @Test
    public void testIsBinaryContentTypeWithRegexNonMatch() throws Exception {
        props.setResponseBinaryMimeTypesRegex(true);

        Field regexMapField = HttpDispatcher.class.getDeclaredField("binaryMimeTypesRegexMap");
        regexMapField.setAccessible(true);
        regexMapField.set(dispatcher, new ConcurrentHashMap<String, Pattern>());

        Method isBinaryMethod = HttpDispatcher.class.getDeclaredMethod("isBinaryContentType", String.class, ContentType.class);
        isBinaryMethod.setAccessible(true);

        String binaryMimeTypes = "application/.*(?<!json|xml)$|image/.*|video/.*|audio/.*";

        assertFalse((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("application/json")));
        assertFalse((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("application/xml")));
        assertFalse((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("text/plain")));
        assertFalse((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("text/html")));
    }

    @Test
    public void testIsBinaryContentTypeWithPrefixMatch() throws Exception {
        props.setResponseBinaryMimeTypesRegex(false);

        Field arrayMapField = HttpDispatcher.class.getDeclaredField("binaryMimeTypesArrayMap");
        arrayMapField.setAccessible(true);
        arrayMapField.set(dispatcher, new ConcurrentHashMap<String, String[]>());

        Method isBinaryMethod = HttpDispatcher.class.getDeclaredMethod("isBinaryContentType", String.class, ContentType.class);
        isBinaryMethod.setAccessible(true);

        String binaryMimeTypes = "application/octet-stream, image/png, video/mp4";

        assertTrue((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("application/octet-stream")));
        assertTrue((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("image/png")));
        assertTrue((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("video/mp4")));
    }

    @Test
    public void testIsBinaryContentTypeWithPrefixNonMatch() throws Exception {
        props.setResponseBinaryMimeTypesRegex(false);

        Field arrayMapField = HttpDispatcher.class.getDeclaredField("binaryMimeTypesArrayMap");
        arrayMapField.setAccessible(true);
        arrayMapField.set(dispatcher, new ConcurrentHashMap<String, String[]>());

        Method isBinaryMethod = HttpDispatcher.class.getDeclaredMethod("isBinaryContentType", String.class, ContentType.class);
        isBinaryMethod.setAccessible(true);

        String binaryMimeTypes = "application/octet-stream, image/png, video/mp4";

        assertFalse((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("text/plain")));
        assertFalse((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("application/json")));
        assertFalse((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("text/html")));
    }

    @Test
    public void testIsBinaryContentTypeRegexCachesPattern() throws Exception {
        props.setResponseBinaryMimeTypesRegex(true);

        ConcurrentHashMap<String, Pattern> regexMap = new ConcurrentHashMap<>();
        Field regexMapField = HttpDispatcher.class.getDeclaredField("binaryMimeTypesRegexMap");
        regexMapField.setAccessible(true);
        regexMapField.set(dispatcher, regexMap);

        Method isBinaryMethod = HttpDispatcher.class.getDeclaredMethod("isBinaryContentType", String.class, ContentType.class);
        isBinaryMethod.setAccessible(true);

        String binaryMimeTypes = "image/.*";

        // First call should populate the cache
        assertTrue((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("image/png")));
        assertEquals(1, regexMap.size());

        // Second call should use the cached pattern (map size unchanged)
        assertTrue((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("image/jpeg")));
        assertEquals(1, regexMap.size());
    }

    @Test
    public void testIsBinaryContentTypePrefixCachesArray() throws Exception {
        props.setResponseBinaryMimeTypesRegex(false);

        ConcurrentHashMap<String, String[]> arrayMap = new ConcurrentHashMap<>();
        Field arrayMapField = HttpDispatcher.class.getDeclaredField("binaryMimeTypesArrayMap");
        arrayMapField.setAccessible(true);
        arrayMapField.set(dispatcher, arrayMap);

        Method isBinaryMethod = HttpDispatcher.class.getDeclaredMethod("isBinaryContentType", String.class, ContentType.class);
        isBinaryMethod.setAccessible(true);

        String binaryMimeTypes = "image/png, video/mp4";

        // First call should populate the cache
        assertTrue((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("image/png")));
        assertEquals(1, arrayMap.size());

        // Second call should use the cached array (map size unchanged)
        assertTrue((Boolean) isBinaryMethod.invoke(dispatcher, binaryMimeTypes, ContentType.parse("video/mp4")));
        assertEquals(1, arrayMap.size());
    }

    @Test
    public void testIsBinaryContentTypeWithInvalidRegexReturnsFalse() throws Exception {
        props.setResponseBinaryMimeTypesRegex(true);

        Field regexMapField = HttpDispatcher.class.getDeclaredField("binaryMimeTypesRegexMap");
        regexMapField.setAccessible(true);
        regexMapField.set(dispatcher, new ConcurrentHashMap<String, Pattern>());

        Method isBinaryMethod = HttpDispatcher.class.getDeclaredMethod("isBinaryContentType", String.class, ContentType.class);
        isBinaryMethod.setAccessible(true);

        // Invalid regex pattern
        assertFalse((Boolean) isBinaryMethod.invoke(dispatcher, "[invalid(regex", ContentType.parse("image/png")));
    }

    // ===== getHeaders / getParameters additional edge cases =====

    @Test
    public void testGetHeadersFromVariableWhenValueIsNotAMap() {
        messageMap.map.put("myVar", "just a string, not a map");
        props.setHeadersVariable("myVar");
        props.setUseHeadersVariable(true);

        Map<String, List<String>> result = dispatcher.getHeaders(props, Mockito.mock(ConnectorMessage.class));
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetParametersFromVariableWhenValueIsNotAMap() {
        messageMap.map.put("myVar", 12345);
        props.setParametersVariable("myVar");
        props.setUseParametersVariable(true);

        Map<String, List<String>> result = dispatcher.getParameters(props, Mockito.mock(ConnectorMessage.class));
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetHeadersFromMapWithMultipleValues() {
        Map<String, List<String>> headersMap = new LinkedHashMap<>();
        headersMap.put("Accept", Arrays.asList("text/html", "application/json"));
        headersMap.put("X-Custom", Arrays.asList("val1"));
        props.setHeadersMap(headersMap);
        props.setUseHeadersVariable(false);

        Map<String, List<String>> result = dispatcher.getHeaders(props, Mockito.mock(ConnectorMessage.class));
        assertEquals(2, result.size());
        assertEquals(Arrays.asList("text/html", "application/json"), result.get("Accept"));
        assertEquals(Arrays.asList("val1"), result.get("X-Custom"));
    }

    // ===== getConnectorProperties test =====

    @Test
    public void testGetConnectorProperties() {
        assertEquals(props, dispatcher.getConnectorProperties());
        assertTrue(dispatcher.getConnectorProperties() instanceof HttpDispatcherProperties);
    }

    // ===== HttpDispatcherProperties defaults test =====

    @Test
    public void testDefaultProperties() {
        HttpDispatcherProperties defaultProps = new HttpDispatcherProperties();

        assertEquals("", defaultProps.getHost());
        assertFalse(defaultProps.isUseProxyServer());
        assertEquals("", defaultProps.getProxyAddress());
        assertEquals("", defaultProps.getProxyPort());
        assertEquals("post", defaultProps.getMethod());
        assertTrue(defaultProps.getHeadersMap().isEmpty());
        assertFalse(defaultProps.isUseHeadersVariable());
        assertEquals("", defaultProps.getHeadersVariable());
        assertTrue(defaultProps.getParametersMap().isEmpty());
        assertFalse(defaultProps.isUseParametersVariable());
        assertEquals("", defaultProps.getParametersVariable());
        assertFalse(defaultProps.isResponseXmlBody());
        assertTrue(defaultProps.isResponseParseMultipart());
        assertFalse(defaultProps.isResponseIncludeMetadata());
        assertEquals("application/.*(?<!json|xml)$|image/.*|video/.*|audio/.*", defaultProps.getResponseBinaryMimeTypes());
        assertTrue(defaultProps.isResponseBinaryMimeTypesRegex());
        assertFalse(defaultProps.isMultipart());
        assertFalse(defaultProps.isUseAuthentication());
        assertEquals("Basic", defaultProps.getAuthenticationType());
        assertFalse(defaultProps.isUsePreemptiveAuthentication());
        assertEquals("", defaultProps.getUsername());
        assertEquals("", defaultProps.getPassword());
        assertEquals("", defaultProps.getContent());
        assertEquals("text/plain", defaultProps.getContentType());
        assertFalse(defaultProps.isDataTypeBinary());
        assertEquals("UTF-8", defaultProps.getCharset());
        assertEquals("30000", defaultProps.getSocketTimeout());
    }

    // ===== Copy constructor test =====

    @Test
    public void testCopyConstructorPreservesValues() {
        HttpDispatcherProperties original = new HttpDispatcherProperties();
        original.setHost("http://example.com");
        original.setMethod("GET");
        original.setUseProxyServer(true);
        original.setProxyAddress("proxy.example.com");
        original.setProxyPort("8080");
        original.setUseAuthentication(true);
        original.setAuthenticationType("Digest");
        original.setUsername("user");
        original.setPassword("pass");
        original.setContent("body content");
        original.setContentType("application/json");
        original.setCharset("ISO-8859-1");
        original.setSocketTimeout("60000");
        original.setResponseXmlBody(true);
        original.setResponseParseMultipart(false);
        original.setResponseBinaryMimeTypes("image/.*");
        original.setResponseBinaryMimeTypesRegex(false);
        original.setMultipart(true);
        original.setDataTypeBinary(true);
        original.setUsePreemptiveAuthentication(true);
        original.setResponseIncludeMetadata(true);

        Map<String, List<String>> headersMap = new LinkedHashMap<>();
        headersMap.put("X-Test", Arrays.asList("val"));
        original.setHeadersMap(headersMap);
        original.setUseHeadersVariable(true);
        original.setHeadersVariable("hdrVar");

        Map<String, List<String>> paramsMap = new LinkedHashMap<>();
        paramsMap.put("key", Arrays.asList("val"));
        original.setParametersMap(paramsMap);
        original.setUseParametersVariable(true);
        original.setParametersVariable("paramVar");

        HttpDispatcherProperties copy = new HttpDispatcherProperties(original);

        assertEquals(original.getHost(), copy.getHost());
        assertEquals(original.getMethod(), copy.getMethod());
        assertEquals(original.isUseProxyServer(), copy.isUseProxyServer());
        assertEquals(original.getProxyAddress(), copy.getProxyAddress());
        assertEquals(original.getProxyPort(), copy.getProxyPort());
        assertEquals(original.isUseAuthentication(), copy.isUseAuthentication());
        assertEquals(original.getAuthenticationType(), copy.getAuthenticationType());
        assertEquals(original.getUsername(), copy.getUsername());
        assertEquals(original.getPassword(), copy.getPassword());
        assertEquals(original.getContent(), copy.getContent());
        assertEquals(original.getContentType(), copy.getContentType());
        assertEquals(original.getCharset(), copy.getCharset());
        assertEquals(original.getSocketTimeout(), copy.getSocketTimeout());
        assertEquals(original.isResponseXmlBody(), copy.isResponseXmlBody());
        assertEquals(original.isResponseParseMultipart(), copy.isResponseParseMultipart());
        assertEquals(original.getResponseBinaryMimeTypes(), copy.getResponseBinaryMimeTypes());
        assertEquals(original.isResponseBinaryMimeTypesRegex(), copy.isResponseBinaryMimeTypesRegex());
        assertEquals(original.isMultipart(), copy.isMultipart());
        assertEquals(original.isDataTypeBinary(), copy.isDataTypeBinary());
        assertEquals(original.isUsePreemptiveAuthentication(), copy.isUsePreemptiveAuthentication());
        assertEquals(original.isResponseIncludeMetadata(), copy.isResponseIncludeMetadata());
        assertEquals(original.isUseHeadersVariable(), copy.isUseHeadersVariable());
        assertEquals(original.getHeadersVariable(), copy.getHeadersVariable());
        assertEquals(original.getHeadersMap(), copy.getHeadersMap());
        assertEquals(original.isUseParametersVariable(), copy.isUseParametersVariable());
        assertEquals(original.getParametersVariable(), copy.getParametersVariable());
        assertEquals(original.getParametersMap(), copy.getParametersMap());
    }

    @Test
    public void testCopyConstructorCreatesIndependentHeadersMap() {
        HttpDispatcherProperties original = new HttpDispatcherProperties();
        Map<String, List<String>> headersMap = new LinkedHashMap<>();
        headersMap.put("X-Test", new ArrayList<>(Arrays.asList("val")));
        original.setHeadersMap(headersMap);

        HttpDispatcherProperties copy = new HttpDispatcherProperties(original);

        // Mutate the original
        original.getHeadersMap().put("New-Header", Arrays.asList("new"));

        // Copy should be unaffected
        assertFalse(copy.getHeadersMap().containsKey("New-Header"));
    }

    @Test
    public void testCopyConstructorCreatesIndependentParametersMap() {
        HttpDispatcherProperties original = new HttpDispatcherProperties();
        Map<String, List<String>> paramsMap = new LinkedHashMap<>();
        paramsMap.put("key", new ArrayList<>(Arrays.asList("val")));
        original.setParametersMap(paramsMap);

        HttpDispatcherProperties copy = new HttpDispatcherProperties(original);

        // Mutate the original
        original.getParametersMap().put("newKey", Arrays.asList("newVal"));

        // Copy should be unaffected
        assertFalse(copy.getParametersMap().containsKey("newKey"));
    }

    // ===== Protocol and name tests =====

    @Test
    public void testProtocol() {
        assertEquals("HTTP", props.getProtocol());
    }

    @Test
    public void testName() {
        assertEquals("HTTP Sender", props.getName());
    }

    class CustomMessageMap extends MessageMaps {
        protected Map<Object, Object> map;

        public CustomMessageMap(Map<Object, Object> map) {
            this.map = map;
        }

        public CustomMessageMap() {}

        @Override
        public Object get(String key, ConnectorMessage connectorMessage, boolean includeResponseMap) {
            return map.get(key);
        }
    }
}
