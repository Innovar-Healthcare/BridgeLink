package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * IRT-2428 Phase 26.15 plan 02: end-to-end proof that a deployed Web Service Listener receives a
 * real SOAP POST and writes the expected artifact, on the JDK 21/25 smoke legs where an unfixed
 * receiver throws the SAAJ meta-factory error (26.15-01's classloader fix). Consumes the
 * committed {@code ws-listener-test.xml} fixture (channel 00000040, WS Listener source + File
 * Writer destination) and 18-07's {@link SmokeTestBase} contracts.
 *
 * <p>CRITICAL: success is never the HTTP response status code. The JAX-WS RI wraps the SAAJ
 * lookup failure in {@code java.lang.Error} ({@code SOAPVersion.getMessageFactory}), which is not
 * an {@code Exception} and so passes straight through {@code LoggingSOAPHandler.handleMessage}'s
 * {@code catch (Exception)}; an unfixed receiver therefore returns an HTTP 500 fault and
 * {@code DefaultAcceptMessage.acceptMessage} never runs. Measured on a stock 26.6.1 container on
 * JDK 21, and consistent with the stack trace in public issue 205. A status assertion still proves
 * nothing in either direction: a 500 can come from any fault, and a 200 does not mean the message
 * was processed. The only honest signal is {@link SmokeTestBase#assertThreeLevels} (a real
 * processed message) plus the written destination artifact actually containing the marker this
 * test sent.
 */
public class WsListenerChannelsTest extends SmokeTestBase {

    private static final String WS_LISTENER_CHANNEL_ID = "00000040-0000-0000-0000-000000000040";
    private static final String WS_ACCEPT_MESSAGE_NAMESPACE = "http://ws.connectors.connect.mirth.com/";
    private static final String SOAP_11_ENVELOPE_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/";

    private static String wsListenerPort;

    @BeforeClass
    public static void setUpWsListenerPort() throws Exception {
        wsListenerPort = requireProperty("WS_LISTENER_PORT");
    }

    @Test
    public void wsListener() throws Exception {
        String marker = "irt2428-ws-listener-" + System.nanoTime();
        String targetUrl = "http://127.0.0.1:" + wsListenerPort + "/services/Mirth";
        String envelope = soapEnvelope(marker);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "text/xml; charset=utf-8")
                // SOAP 1.1 convention for "no action" is the two-character quoted-empty string,
                // not a truly empty header value (HttpClient rejects an empty header value).
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                .build();
        // The response is sent for diagnostics only. Do NOT assert on its status code here - see
        // the class javadoc: the status does not distinguish a processed message from a fault, so
        // only the processed-message and artifact assertions below can catch the defect.
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("WS Listener POST to " + targetUrl + " returned HTTP " + response.statusCode());

        assertThreeLevels(WS_LISTENER_CHANNEL_ID, 1, () -> {
            Path out = pollForFile(Paths.get(outDir, "ws", "output.txt"), 60);
            String content = readFile(out);
            assertTrue("WS Listener destination content should contain the SOAP marker payload",
                    content.contains(marker));
        });
    }

    /**
     * An operation the endpoint does not have must come back as a SOAP fault. JAX-WS marshals
     * faults with JAXB, and JAXB is resolved through the thread context classloader, so this is
     * the check that fails when the receiver's ThreadFactory is missing: on JDK 19+ the worker
     * gets the system classloader, JAXB is not found, and the client receives an empty HTTP 500
     * (IRT-2428 QA). {@link #wsListener} cannot catch that any more, because 26.16 removed the
     * one SAAJ call on the plain-message path from {@code LoggingSOAPHandler}.
     *
     * <p>Here the status code alone is still not the signal (a fault is a 500 either way); the
     * assertion is that the body parses as a SOAP 1.1 envelope carrying a Fault with a non-empty
     * faultstring.
     */
    @Test
    public void wsListenerUnknownOperationReturnsSoapFault() throws Exception {
        String targetUrl = "http://127.0.0.1:" + wsListenerPort + "/services/Mirth";
        String envelope = "<soapenv:Envelope xmlns:soapenv=\"" + SOAP_11_ENVELOPE_NAMESPACE + "\">"
                + "<soapenv:Body>"
                + "<ws:noSuchOperation xmlns:ws=\"" + WS_ACCEPT_MESSAGE_NAMESPACE + "\"/>"
                + "</soapenv:Body>"
                + "</soapenv:Envelope>";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        System.out.println("WS Listener unknown-operation POST returned HTTP " + response.statusCode()
                + " with a " + (body == null ? 0 : body.length()) + "-character body");

        assertEquals("unknown operation should be answered with a SOAP fault status", 500, response.statusCode());
        assertTrue("unknown-operation response body is empty; an empty 500 means the fault could not be "
                + "marshalled on the listener worker (JAXB not visible to its context classloader)",
                body != null && !body.trim().isEmpty());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

        NodeList faults = document.getElementsByTagNameNS(SOAP_11_ENVELOPE_NAMESPACE, "Fault");
        assertEquals("response should carry exactly one SOAP 1.1 Fault, got: " + body, 1, faults.getLength());
        NodeList faultStrings = ((Element) faults.item(0)).getElementsByTagName("faultstring");
        assertEquals("SOAP Fault should carry a faultstring, got: " + body, 1, faultStrings.getLength());
        assertFalse("SOAP Fault faultstring should not be empty, got: " + body,
                faultStrings.item(0).getTextContent().trim().isEmpty());
    }

    private static String soapEnvelope(String payload) {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soapenv:Body>"
                + "<ws:acceptMessage xmlns:ws=\"" + WS_ACCEPT_MESSAGE_NAMESPACE + "\">"
                + "<arg0>" + payload + "</arg0>"
                + "</ws:acceptMessage>"
                + "</soapenv:Body>"
                + "</soapenv:Envelope>";
    }

    /**
     * {@link SmokeTestBase#requireProperty} is private and not inherited across compilation
     * units, so this driver reads the harness-forwarded {@code -DWS_LISTENER_PORT} system
     * property directly with the same fail-fast semantics.
     */
    private static String requireProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required system property '" + name + "' not set. "
                    + "This driver only runs against a live harness - invoke via "
                    + "smoke-tests/run-smoke-test.sh, which passes WS_LISTENER_PORT as a -D property "
                    + "to `ant -f smoke-tests/build.xml test-run`.");
        }
        return value;
    }
}
