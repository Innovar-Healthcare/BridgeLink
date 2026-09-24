/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.ws;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.ws.Endpoint;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.donkey.model.channel.ConnectorPluginProperties;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.event.EventDispatcher;
import com.mirth.connect.plugins.httpauth.basic.BasicHttpAuthProperties;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ContextFactoryController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.util.javascript.MirthContextFactory;

/**
 * Regression test for IRT-2428. WebServiceReceiver.onStart() must pin the connector application
 * classloader onto every httpserver worker thread, so that on JDK 19+ (JEP 425, where a
 * non-inheriting httpserver worker defaults to the system classloader) an inbound SOAP request
 * running on a worker still resolves SAAJ from server-lib.
 *
 * The discriminator here is classloader IDENTITY, not a MessageFactory failure: the unit-test JVM
 * has saaj-impl on its application classpath, so a system-loader worker would still resolve SAAJ
 * and could not fail. We inject a distinct child loader as the connector application classloader and
 * assert the worker thread that runs processData carries exactly that loader. Without the
 * ThreadFactory pinning this is red on every JDK (the worker carries the app loader on 17, the
 * system loader on 19+, never the injected child); with it, green.
 */
public class WebServiceReceiverTest {

    private static final String TEST_CHANNEL_ID = "irt2428-ws-listener";
    private static final String TEST_CHANNEL_NAME = "IRT-2428 WS Listener Test Channel";
    private static final String RESPONSE_BODY = "ws-listener-tccl-ok";
    private static final String REQUEST_MARKER = "irt2428-payload";

    private static final AtomicReference<ClassLoader> CAPTURED_WORKER_CCL = new AtomicReference<>();
    private static URLClassLoader childLoader;

    // Promoted from setupBeforeClass locals so each degrade-path test can re-stub them
    // independently (IRT-2428 follow-up, D-01/D-02/D-03).
    private static ContextFactoryController contextFactoryController;
    private static MirthContextFactory contextFactory;

    private WebServiceReceiver receiver;

    @BeforeClass
    public static void setupBeforeClass() throws Exception {
        childLoader = new URLClassLoader(new URL[0], WebServiceReceiverTest.class.getClassLoader());

        ControllerFactory controllerFactory = mock(ControllerFactory.class);
        when(controllerFactory.createEventController()).thenReturn(mock(EventController.class));
        when(controllerFactory.createConfigurationController()).thenReturn(mock(ConfigurationController.class));

        contextFactoryController = mock(ContextFactoryController.class);
        contextFactory = mock(MirthContextFactory.class);
        // getApplicationClassLoader() is final on the Rhino ContextFactory; mockito-inline (present in
        // server/testlib) mocks final methods. Return a distinct child loader so identity is testable.
        when(contextFactory.getApplicationClassLoader()).thenReturn(childLoader);
        // resourceIds is null on a bare receiver, and anySet() would NOT match null; use any().
        when(contextFactoryController.getContextFactory(any())).thenReturn(contextFactory);
        when(controllerFactory.createContextFactoryController()).thenReturn(contextFactoryController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);
    }

    @After
    public void tearDown() throws Exception {
        if (receiver != null) {
            try {
                receiver.stop();
            } finally {
                receiver.onUndeploy();
            }
        }
    }

    @Test
    public void inboundSoapRequestRunsOnWorkerPinnedToConnectorClassLoader() throws Exception {
        // Re-stub to the normal-path baseline; degrade-path tests below override these on the
        // shared static mocks, and tests may run in any order.
        when(contextFactoryController.getContextFactory(any())).thenReturn(contextFactory);
        when(contextFactory.getApplicationClassLoader()).thenReturn(childLoader);

        CAPTURED_WORKER_CCL.set(null);

        int port = findFreePort();
        WebServiceReceiverProperties props = new WebServiceReceiverProperties();
        props.getListenerConnectorProperties().setHost("127.0.0.1");
        props.getListenerConnectorProperties().setPort(String.valueOf(port));
        props.setServiceName("Mirth");
        props.getSourceConnectorProperties().setProcessingThreads(1);

        receiver = new TestWebServiceReceiver(props);
        receiver.setResourceIds(Collections.emptySet());

        Channel channel = new TestChannel();
        channel.setChannelId(TEST_CHANNEL_ID);
        channel.setName(TEST_CHANNEL_NAME);
        receiver.setChannel(channel);

        receiver.onDeploy();
        receiver.start();

        String response = postSoap("http://127.0.0.1:" + port + "/services/Mirth", soapEnvelope(REQUEST_MARKER));

        ClassLoader workerCcl = CAPTURED_WORKER_CCL.get();
        assertNotNull("processData was never invoked on a worker thread (no CCL captured)", workerCcl);
        assertSame("the httpserver worker thread must carry the connector application classloader", childLoader, workerCcl);
        assertTrue("the SOAP response should carry the receiver's response body, was: " + response, response.contains(RESPONSE_BODY));
    }

    @Test
    public void getContextFactoryFailureDegradesToDefaultAcceptMessageOnConnectorLoader() throws Exception {
        // IRT-2428 follow-up (D-01/D-02): a getContextFactory failure must degrade the listener to
        // DefaultAcceptMessage and still start, pinning a server-lib-visible loader on every worker,
        // instead of failing connector start.
        when(contextFactoryController.getContextFactory(any())).thenThrow(new Exception("simulated getContextFactory failure"));

        CAPTURED_WORKER_CCL.set(null);

        int port = findFreePort();
        WebServiceReceiverProperties props = new WebServiceReceiverProperties();
        props.getListenerConnectorProperties().setHost("127.0.0.1");
        props.getListenerConnectorProperties().setPort(String.valueOf(port));
        props.setServiceName("Mirth");
        props.getSourceConnectorProperties().setProcessingThreads(1);

        receiver = new TestWebServiceReceiver(props);
        receiver.setResourceIds(Collections.emptySet());

        Channel channel = new TestChannel();
        channel.setChannelId(TEST_CHANNEL_ID);
        channel.setName(TEST_CHANNEL_NAME);
        receiver.setChannel(channel);

        receiver.onDeploy();
        receiver.start();

        String response = postSoap("http://127.0.0.1:" + port + "/services/Mirth", soapEnvelope(REQUEST_MARKER));

        ClassLoader workerCcl = CAPTURED_WORKER_CCL.get();
        assertNotNull("processData was never invoked on a worker thread (no CCL captured)", workerCcl);
        assertSame("a getContextFactory failure must degrade to the connector classloader, never pin null or the system loader", WebServiceReceiver.class.getClassLoader(), workerCcl);
        assertTrue("the SOAP response should carry the receiver's response body (proves the connector degraded to DefaultAcceptMessage and served the request), was: " + response, response.contains(RESPONSE_BODY));
    }

    @Test
    public void nullApplicationClassLoaderFallsBackToConnectorLoader() throws Exception {
        // IRT-2428 follow-up (D-03): a null application classloader must not be pinned unguarded;
        // fall back to the connector classloader, emit a WARN, and still start.
        when(contextFactoryController.getContextFactory(any())).thenReturn(contextFactory);
        when(contextFactory.getApplicationClassLoader()).thenReturn(null);

        CAPTURED_WORKER_CCL.set(null);

        int port = findFreePort();
        WebServiceReceiverProperties props = new WebServiceReceiverProperties();
        props.getListenerConnectorProperties().setHost("127.0.0.1");
        props.getListenerConnectorProperties().setPort(String.valueOf(port));
        props.setServiceName("Mirth");
        props.getSourceConnectorProperties().setProcessingThreads(1);

        receiver = new TestWebServiceReceiver(props);
        receiver.setResourceIds(Collections.emptySet());

        Channel channel = new TestChannel();
        channel.setChannelId(TEST_CHANNEL_ID);
        channel.setName(TEST_CHANNEL_NAME);
        receiver.setChannel(channel);

        receiver.onDeploy();
        receiver.start();

        String response = postSoap("http://127.0.0.1:" + port + "/services/Mirth", soapEnvelope(REQUEST_MARKER));

        ClassLoader workerCcl = CAPTURED_WORKER_CCL.get();
        assertNotNull("processData was never invoked on a worker thread, or the worker carried a null context classloader", workerCcl);
        assertSame("a null application classloader must fall back to the connector classloader, never pin null", WebServiceReceiver.class.getClassLoader(), workerCcl);
        assertTrue("the SOAP response should carry the receiver's response body, was: " + response, response.contains(RESPONSE_BODY));
    }

    @Test
    public void handlerErrorReturnsTrueAndDoesNotMaterializeMessage() throws Exception {
        // IRT-2428 follow-up (D-04): a pure-logging handler must never reverse jaxws-rt message
        // direction on error, and must never materialize the SAAJ message. A false return makes
        // jaxws-rt reverse direction and echo the client's own request back as a 200 with the
        // channel never seeing the message.
        WebServiceReceiverProperties props = new WebServiceReceiverProperties();
        WebServiceReceiver minimalReceiver = new TestWebServiceReceiver(props);

        SOAPMessageContext smc = mock(SOAPMessageContext.class);
        when(smc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY)).thenThrow(new RuntimeException("simulated SOAPMessageContext failure"));

        LoggingSOAPHandler handler = new LoggingSOAPHandler(minimalReceiver);

        boolean result = handler.handleMessage(smc);

        assertTrue("a pure-logging handler must never reverse jaxws-rt direction on error", result);
        verify(smc, never()).getMessage();
    }

    @Test
    public void onStopShutsDownExecutorWhenEndpointStopThrows() throws Exception {
        // IRT-2428 follow-up (D-05/WR-01): executor.shutdown() must always run even when an
        // earlier stop() throws, and the field must be nulled afterward, so the long-lived
        // non-daemon pinned pool is not leaked across every redeploy.
        WebServiceReceiverProperties props = new WebServiceReceiverProperties();
        WebServiceReceiver testReceiver = new TestWebServiceReceiver(props);

        ExecutorService realExecutor = Executors.newSingleThreadExecutor();
        Endpoint mockEndpoint = mock(Endpoint.class);
        doThrow(new RuntimeException("simulated endpoint stop failure")).when(mockEndpoint).stop();

        setPrivateField(testReceiver, "executor", realExecutor);
        setPrivateField(testReceiver, "webServiceEndpoint", mockEndpoint);
        setPrivateField(testReceiver, "server", null);
        setPrivateField(testReceiver, "authenticatorProvider", null);

        try {
            testReceiver.onStop();
            fail("onStop() should rethrow the first cause when webServiceEndpoint.stop() throws");
        } catch (ConnectorTaskException e) {
            // expected: the single first-cause throw contract is preserved
        }

        assertTrue("executor.shutdown() must run even though webServiceEndpoint.stop() threw", realExecutor.isShutdown());
        assertNull("the executor field must be nulled after shutdown", getPrivateField(testReceiver, "executor"));
    }

    @Test
    public void stopStartWithoutRedeployStillEnforcesAuthentication() throws Exception {
        // IRT-2428 review follow-up: authenticatorProvider is deploy-scoped (created in onDeploy)
        // and Donkey stop/start (and pause/resume) never re-run onDeploy(). Before this fix,
        // onStop() shut down and nulled authenticatorProvider, so a bare stop/start (no redeploy)
        // republished the endpoint with NO authenticator and accepted every request
        // unauthenticated -- a fail-open regression on a PHI endpoint. RED on revert: against the
        // pre-fix onStop(), the second POST below returns 200 instead of 401.
        when(contextFactoryController.getContextFactory(any())).thenReturn(contextFactory);
        when(contextFactory.getApplicationClassLoader()).thenReturn(childLoader);

        CAPTURED_WORKER_CCL.set(null);

        int port = findFreePort();
        WebServiceReceiverProperties props = new WebServiceReceiverProperties();
        props.getListenerConnectorProperties().setHost("127.0.0.1");
        props.getListenerConnectorProperties().setPort(String.valueOf(port));
        props.setServiceName("Mirth");
        props.getSourceConnectorProperties().setProcessingThreads(1);

        BasicHttpAuthProperties authProps = new BasicHttpAuthProperties();
        authProps.setRealm("IRT-2428 Test Realm");
        authProps.getCredentialsMap().put("testuser", "testpass");
        Set<ConnectorPluginProperties> pluginProperties = new HashSet<ConnectorPluginProperties>();
        pluginProperties.add(authProps);
        props.setPluginProperties(pluginProperties);

        receiver = new TestWebServiceReceiver(props);
        receiver.setResourceIds(Collections.emptySet());

        Channel channel = new TestChannel();
        channel.setChannelId(TEST_CHANNEL_ID);
        channel.setName(TEST_CHANNEL_NAME);
        receiver.setChannel(channel);

        receiver.onDeploy();
        receiver.start();

        int firstStatus = postSoapStatus("http://127.0.0.1:" + port + "/services/Mirth", soapEnvelope(REQUEST_MARKER));
        assertTrue("baseline: an unauthenticated request must be challenged (401) when Basic auth is configured, was: " + firstStatus, firstStatus == 401);

        // Simulate a dashboard Stop -> Start WITHOUT a redeploy (onDeploy is intentionally not
        // re-invoked, matching how Donkey actually drives stop/start).
        receiver.onStop();
        receiver.onStart();

        int secondStatus = postSoapStatus("http://127.0.0.1:" + port + "/services/Mirth", soapEnvelope(REQUEST_MARKER));
        assertTrue("after a stop/start without redeploy, an unauthenticated request must STILL be challenged (401) -- authenticatorProvider must survive onStop(), was: " + secondStatus, secondStatus == 401);
    }

    private static void setPrivateField(WebServiceReceiver instance, String fieldName, Object value) throws Exception {
        Field field = WebServiceReceiver.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Object getPrivateField(WebServiceReceiver instance, String fieldName) throws Exception {
        Field field = WebServiceReceiver.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static String soapEnvelope(String payload) {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soapenv:Body>"
                + "<ws:acceptMessage xmlns:ws=\"http://ws.connectors.connect.mirth.com/\">"
                + "<arg0>" + payload + "</arg0>"
                + "</ws:acceptMessage>"
                + "</soapenv:Body>"
                + "</soapenv:Envelope>";
    }

    // onStart publishes the endpoint synchronously, but retry briefly to absorb any bind/accept race.
    private static String postSoap(String url, String envelope) throws Exception {
        IOException last = null;
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
                conn.setRequestProperty("SOAPAction", "\"\"");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(envelope.getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                byte[] body = is == null ? new byte[0] : is.readAllBytes();
                return new String(body, StandardCharsets.UTF_8);
            } catch (IOException e) {
                last = e;
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Could not reach the WS listener at " + url, last);
    }

    // Same bind/accept-race retry as postSoap, but returns the HTTP status code instead of the
    // body -- used to assert auth-challenge (401) behavior without needing the response content.
    private static int postSoapStatus(String url, String envelope) throws Exception {
        IOException last = null;
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
                conn.setRequestProperty("SOAPAction", "\"\"");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(envelope.getBytes(StandardCharsets.UTF_8));
                }
                return conn.getResponseCode();
            } catch (IOException e) {
                last = e;
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Could not reach the WS listener at " + url, last);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static class TestWebServiceReceiver extends WebServiceReceiver {
        public TestWebServiceReceiver(WebServiceReceiverProperties properties) {
            super();
            setChannelId(TEST_CHANNEL_ID);
            setMetaDataId(0);
            setConnectorProperties(properties);
        }

        @Override
        protected String getConfigurationClass() {
            return "com.mirth.connect.connectors.ws.DefaultWebServiceConfiguration";
        }

        @Override
        public String processData(String message) {
            // Runs on the httpserver worker thread that handled the inbound SOAP request.
            CAPTURED_WORKER_CCL.set(Thread.currentThread().getContextClassLoader());
            return RESPONSE_BODY;
        }
    }

    private static class TestChannel extends Channel {
        @Override
        protected EventDispatcher getEventDispatcher() {
            return mock(EventDispatcher.class);
        }
    }
}
