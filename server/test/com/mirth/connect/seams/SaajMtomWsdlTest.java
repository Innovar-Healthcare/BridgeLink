/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.seams;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.wsdl.Definition;
import javax.wsdl.extensions.schema.Schema;
import javax.wsdl.extensions.schema.SchemaImport;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Provider;
import javax.xml.ws.Service;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.WebServiceProvider;

import org.junit.Test;

/**
 * Phase 22 / CVE-08 / D-11 coverage gap closure for the saaj-impl 1.0 -&gt; 1.5.3 and wsdl4j
 * 1.6.2-fixed -&gt; 1.6.3 jar swap (22-03-PLAN.md). {@code JaxWsSaajSeamTest} already proves the
 * base SOAP round-trip (and, by staying green, that saaj-impl 1.5.3 registers its
 * {@code javax.xml.soap} SPI factories against the shipped {@code javax.xml.soap-api-1.4.0}). This
 * suite adds the two coverage gaps a plain jar swap would otherwise leave silent:
 * <ol>
 * <li>{@link #mtomAttachmentSurvivesRoundTrip()} — SAAJ is specifically the <em>attachments</em>
 * API. A real {@link javax.xml.ws.Endpoint#publish} backed by a {@link javax.xml.ws.Provider} in
 * {@link javax.xml.ws.Service.Mode#MESSAGE} mode receives the full envelope (attachments
 * included) over real HTTP via the jaxws-rt/Metro transport (which uses the shipped
 * {@code mimepull-1.9.7.jar} to parse the inbound {@code multipart/related} body) and echoes it
 * back. The client drives the call with a real {@link javax.xml.soap.SOAPConnection} — exercising
 * the {@code SOAPConnectionFactory} SPI entry that {@code JaxWsSaajSeamTest} does not touch. A
 * lost or corrupted attachment is a FINDING, not a pass (22-03-PLAN.md T-22-03-02).</li>
 * <li>{@link #wsdlSchemaImportResolvesUnderWsdl4j163()} — drives a representative WSDL (carrying an
 * embedded {@code xsd:schema} with an {@code xsd:import}) through the exact {@code javax.wsdl}
 * {@code Schema}/{@code SchemaImport} resolution path
 * {@code WebServiceConnectorServlet.java:406-433} uses, proving the {@code com.ibm.wsdl} version
 * bump (1.6.2-fixed -&gt; clean 1.6.3) did not break the servlet's WSDL introspection.</li>
 * </ol>
 */
public class SaajMtomWsdlTest {

    private static final String NAMESPACE = "http://seams.mirth.connect.com/";
    private static final String IMPORTED_NAMESPACE = "urn:seams:mirth:imported";
    private static final String ATTACHMENT_CONTENT_TYPE = "application/octet-stream";

    // A representative WSDL carrying an embedded <xsd:schema> with an <xsd:import>, mirroring the
    // shape WebServiceConnectorServlet.java:404-433 walks (definition.getTypes() ->
    // Schema.getImports() -> SchemaImport.getReferencedSchema()). Written to a temp file pair at
    // test time (documented in 22-03-SUMMARY.md) so wsdl4j's relative schemaLocation resolution
    // (identical to the servlet's real WSDL-import path) has a real base URI to resolve against.
    private static final String MAIN_WSDL_WITH_SCHEMA_IMPORT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<definitions name=\"SaajMtomWsdlSeamService\"\n" +
            "    targetNamespace=\"urn:seams:mirth:spike\"\n" +
            "    xmlns=\"http://schemas.xmlsoap.org/wsdl/\"\n" +
            "    xmlns:tns=\"urn:seams:mirth:spike\"\n" +
            "    xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"\n" +
            "    xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\">\n" +
            "  <types>\n" +
            "    <xsd:schema targetNamespace=\"urn:seams:mirth:spike\" elementFormDefault=\"qualified\">\n" +
            "      <xsd:import namespace=\"" + IMPORTED_NAMESPACE + "\" schemaLocation=\"imported.xsd\"/>\n" +
            "      <xsd:element name=\"Ping\" type=\"xsd:string\"/>\n" +
            "    </xsd:schema>\n" +
            "  </types>\n" +
            "  <message name=\"PingMessage\">\n" +
            "    <part name=\"parameters\" element=\"tns:Ping\"/>\n" +
            "  </message>\n" +
            "  <portType name=\"SeamPortType\">\n" +
            "    <operation name=\"Ping\">\n" +
            "      <input message=\"tns:PingMessage\"/>\n" +
            "    </operation>\n" +
            "  </portType>\n" +
            "  <binding name=\"SeamBinding\" type=\"tns:SeamPortType\">\n" +
            "    <soap:binding style=\"document\" transport=\"http://schemas.xmlsoap.org/soap/http\"/>\n" +
            "    <operation name=\"Ping\">\n" +
            "      <soap:operation soapAction=\"\"/>\n" +
            "      <input><soap:body use=\"literal\"/></input>\n" +
            "    </operation>\n" +
            "  </binding>\n" +
            "  <service name=\"SaajMtomWsdlSeamService\">\n" +
            "    <port name=\"SeamPort\" binding=\"tns:SeamBinding\">\n" +
            "      <soap:address location=\"http://127.0.0.1/seam-wsdl-import-fixture\"/>\n" +
            "    </port>\n" +
            "  </service>\n" +
            "</definitions>\n";

    private static final String IMPORTED_SCHEMA_XSD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"\n" +
            "            targetNamespace=\"" + IMPORTED_NAMESPACE + "\"\n" +
            "            elementFormDefault=\"qualified\">\n" +
            "  <xsd:element name=\"ImportedThing\" type=\"xsd:string\"/>\n" +
            "</xsd:schema>\n";

    // ========== D-11b: NEW MTOM/mimepull attachment round-trip ==========

    @Test
    public void mtomAttachmentSurvivesRoundTrip() throws Exception {
        int port = findFreePort();
        String address = "http://127.0.0.1:" + port + "/mtomSeamStub";

        Endpoint endpoint = Endpoint.publish(address, new MtomEchoProvider());
        try {
            // A few KB of representative binary payload -- large enough that a truncated or
            // partially-corrupted attachment could not pass a byte-for-byte comparison by chance.
            byte[] binaryPayload = new byte[4096];
            new Random(0xC0FFEEL).nextBytes(binaryPayload);

            MessageFactory factory = MessageFactory.newInstance();
            SOAPMessage outgoing = factory.createMessage();
            SOAPBody outgoingBody = outgoing.getSOAPBody();
            SOAPElement payloadElement = outgoingBody.addChildElement(new QName(NAMESPACE, "mtomPing", "seam"));
            payloadElement.addTextNode("mtom-ping");

            AttachmentPart attachment = outgoing.createAttachmentPart(new DataHandler(new ByteArrayDataSource(binaryPayload, ATTACHMENT_CONTENT_TYPE)));
            attachment.setContentId("<binary-payload>");
            outgoing.addAttachmentPart(attachment);
            outgoing.saveChanges();

            // A real javax.xml.soap.SOAPConnection call -- this exercises the SOAPConnectionFactory
            // SPI entry (one of the four saaj-impl-1.5.3 META-INF/services factories) that
            // JaxWsSaajSeamTest's Service.create()/Endpoint.publish() path does not touch.
            SOAPConnection connection = SOAPConnectionFactory.newInstance().createConnection();
            SOAPMessage response;
            try {
                response = connection.call(outgoing, new URL(address));
            } finally {
                connection.close();
            }

            Iterator<AttachmentPart> responseAttachments = response.getAttachments();
            assertTrue("the echoed response must carry the attachment part back", responseAttachments.hasNext());
            AttachmentPart echoedAttachment = responseAttachments.next();

            byte[] echoedBytes;
            try (InputStream echoedStream = echoedAttachment.getDataHandler().getInputStream()) {
                echoedBytes = echoedStream.readAllBytes();
            }

            // Falsifiability: this assertion goes red if the attachment is dropped (echoedBytes
            // empty/absent) or corrupted in transit (any byte differs) by either the client-side
            // saaj-impl 1.5.3 serialization, the jaxws-rt/mimepull-1.9.7 server-side MIME parse, or
            // the return trip -- it cannot pass "by accident" for the wrong reason.
            assertArrayEquals("MTOM attachment must survive the saaj-impl 1.5.3 round-trip byte-for-byte", binaryPayload, echoedBytes);
            assertFalse("exactly one attachment part is expected on the echoed response", responseAttachments.hasNext());
        } finally {
            endpoint.stop();
        }
    }

    // ========== D-11c: NEW WSDL schema-import resolution ==========

    @Test
    public void wsdlSchemaImportResolvesUnderWsdl4j163() throws Exception {
        File tempDir = Files.createTempDirectory("saaj-mtom-wsdl-schema-import").toFile();
        try {
            File importedSchemaFile = new File(tempDir, "imported.xsd");
            File mainWsdlFile = new File(tempDir, "main.wsdl");
            Files.write(importedSchemaFile.toPath(), IMPORTED_SCHEMA_XSD.getBytes(StandardCharsets.UTF_8));
            Files.write(mainWsdlFile.toPath(), MAIN_WSDL_WITH_SCHEMA_IMPORT.getBytes(StandardCharsets.UTF_8));

            // Same factory/reader shape as WebServiceConnectorServlet.java:256-266
            // (importWsdlInterfaces): WSDLFactory.newInstance().newWSDLReader().readWSDL(null, url).
            WSDLFactory wsdlFactory = WSDLFactory.newInstance();
            WSDLReader wsdlReader = wsdlFactory.newWSDLReader();
            Definition definition = wsdlReader.readWSDL(null, mainWsdlFile.toURI().toString());

            List<?> extensibilityElements = definition.getTypes().getExtensibilityElements();
            assertFalse("the representative WSDL must declare at least one <xsd:schema>", extensibilityElements.isEmpty());

            Object firstExtensibilityElement = extensibilityElements.get(0);
            assertTrue("the WSDL <types> extensibility element must be a javax.wsdl.extensions.schema.Schema " +
                    "(the same instanceof check WebServiceConnectorServlet.java:406 makes)", firstExtensibilityElement instanceof Schema);
            Schema schema = (Schema) firstExtensibilityElement;

            @SuppressWarnings("unchecked")
            Map<String, Vector<SchemaImport>> imports = schema.getImports();
            assertNotNull("the top-level schema must declare an xsd:import", imports);
            assertFalse("the top-level schema's import map must be non-empty", imports.isEmpty());

            Vector<SchemaImport> importsForNamespace = imports.values().iterator().next();
            assertFalse(importsForNamespace.isEmpty());
            SchemaImport schemaImport = importsForNamespace.get(0);

            Schema referencedSchema = schemaImport.getReferencedSchema();

            // Falsifiability: this assertion goes red if wsdl4j 1.6.3's com.ibm.wsdl SchemaImportImpl
            // fails to resolve the relative schemaLocation against the WSDL's base URI --
            // getReferencedSchema() returning null is exactly the regression this test guards
            // against (the servlet's usage at WebServiceConnectorServlet.java:413 dereferences the
            // result immediately without a null check).
            assertNotNull("wsdl4j 1.6.3 must resolve the xsd:import to a non-null referenced schema -- " +
                    "the exact javax.wsdl path WebServiceConnectorServlet.java:406-433 relies on", referencedSchema);
            assertEquals(IMPORTED_NAMESPACE, referencedSchema.getElement().getAttribute("targetNamespace"));
        } finally {
            deleteRecursively(tempDir);
        }
    }

    // ========== Helpers ==========

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void deleteRecursively(File root) throws IOException {
        if (!root.exists()) {
            return;
        }
        Files.walk(root.toPath()).sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }

    private static final class ByteArrayDataSource implements DataSource {
        private final byte[] data;
        private final String contentType;

        private ByteArrayDataSource(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(data);
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            throw new IOException("ByteArrayDataSource is read-only");
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getName() {
            return "binary-payload";
        }
    }

    // ========== MTOM echo endpoint under test ==========

    // MESSAGE mode gives the provider access to the full inbound SOAPMessage -- attachments
    // included -- which PAYLOAD-mode JAX-WS dispatch (as used by JaxWsSaajSeamTest's EchoPort)
    // would hide from application code.
    @WebServiceProvider(serviceName = "MtomEchoService", portName = "MtomEchoServicePort", targetNamespace = NAMESPACE)
    @ServiceMode(value = Service.Mode.MESSAGE)
    public static class MtomEchoProvider implements Provider<SOAPMessage> {
        @Override
        public SOAPMessage invoke(SOAPMessage request) {
            return request;
        }
    }
}
