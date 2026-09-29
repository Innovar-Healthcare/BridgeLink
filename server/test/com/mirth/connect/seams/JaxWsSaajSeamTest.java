/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.seams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;

import org.junit.Test;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Dependency-seam characterization suite (NET-03, D-09/D-10) for the JAX-WS / SAAJ SOAP
 * round-trip AS SHIPPED: jaxws-rt 2.3.0.2 / jaxws-api 2.3.0 (server/lib/javax/jaxws) plus the
 * shipped SAAJ implementation (server/lib/javax/jaxws/ext/saaj-impl-1.0.jar). No Mockito is used
 * here &mdash; the endpoint and messages are real, which keeps this seam meaningful on JDK 25
 * (18-RESEARCH.md Assumption A2 flags JAX-WS-on-25 as the actual risk under test; jaxws-rt's own
 * bundled provider is what publishes the endpoint below, not the JDK's removed java.xml.ws
 * module).
 * <p>
 * Purpose: Phase 22's saaj-impl/wsdl4j bump must keep this suite green unchanged. A JDK-25-only
 * failure here is expected, valuable signal for Phase 28 certification &mdash; it should be
 * recorded, not masked.
 */
public class JaxWsSaajSeamTest {

    private static final String NAMESPACE = "http://seams.mirth.connect.com/";
    private static final String KNOWN_PAYLOAD = "seam-known-payload-McDoogal";

    // ========== Test 1: Endpoint.publish + WSDL retrieval ==========

    @Test
    public void endpointPublishesAndServesWsdl() throws Exception {
        int port = findFreePort();
        String address = "http://127.0.0.1:" + port + "/seamStub";

        Endpoint endpoint = Endpoint.publish(address, new EchoServiceImpl());
        try {
            String wsdl = readUrl(address + "?wsdl");
            assertNotNull(wsdl);
            assertTrue("published WSDL must contain a <definitions> element", wsdl.contains("definitions"));
        } finally {
            endpoint.stop();
        }
    }

    // ========== Test 2: SAAJ SOAPMessage round-trip (no HTTP, no Mockito) ==========

    @Test
    public void saajEnvelopeRoundTrips() throws Exception {
        MessageFactory factory = MessageFactory.newInstance();

        SOAPMessage outgoing = factory.createMessage();
        SOAPBody outgoingBody = outgoing.getSOAPBody();
        SOAPElement payloadElement = outgoingBody.addChildElement(new QName(NAMESPACE, "payload", "seam"));
        payloadElement.addTextNode(KNOWN_PAYLOAD);
        outgoing.saveChanges();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        outgoing.writeTo(bytes);

        SOAPMessage reparsed = factory.createMessage(new MimeHeaders(), new ByteArrayInputStream(bytes.toByteArray()));
        SOAPBody reparsedBody = reparsed.getSOAPBody();
        NodeList payloadNodes = reparsedBody.getElementsByTagNameNS(NAMESPACE, "payload");

        assertEquals(1, payloadNodes.getLength());
        Node payloadNode = payloadNodes.item(0);
        assertEquals(KNOWN_PAYLOAD, payloadNode.getTextContent());
    }

    // ========== Test 3: full JAX-WS + SAAJ envelope round-trip through the published endpoint ==========

    @Test
    public void soapCallRoundTrips() throws Exception {
        int port = findFreePort();
        String address = "http://127.0.0.1:" + port + "/seamStub";

        Endpoint endpoint = Endpoint.publish(address, new EchoServiceImpl());
        try {
            URL wsdlUrl = new URL(address + "?wsdl");
            QName serviceName = new QName(NAMESPACE, "EchoService");
            Service service = Service.create(wsdlUrl, serviceName);
            EchoPort port2 = service.getPort(EchoPort.class);

            String result = port2.echo("seam-round-trip-payload");

            assertEquals("seam-round-trip-payload", result);
        } finally {
            endpoint.stop();
        }
    }

    // ========== Helpers ==========

    private int findFreePort() throws IOException {
        // Ephemeral allocation: bind then immediately release. Single-JVM test, so the brief
        // TOCTOU window between close() and Endpoint.publish() re-binding it is acceptable here
        // (18-RESEARCH.md Pattern 4 / T-18-06: never 0.0.0.0, always stopped in @After/finally).
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String readUrl(String urlStr) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlStr).openConnection();
        connection.setRequestMethod("GET");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } finally {
            connection.disconnect();
        }
    }

    // ========== SOAP endpoint under test ==========

    @WebService(targetNamespace = NAMESPACE, name = "Echo")
    public interface EchoPort {
        @WebMethod
        String echo(@WebParam(name = "input") String input);
    }

    @WebService(serviceName = "EchoService", portName = "EchoServicePort", endpointInterface = "com.mirth.connect.seams.JaxWsSaajSeamTest$EchoPort", targetNamespace = NAMESPACE)
    public static class EchoServiceImpl implements EchoPort {
        @Override
        public String echo(String input) {
            return input;
        }
    }
}
