package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.stream.StreamSource;

import org.junit.Test;

import com.mirth.connect.smoketest.stubs.DicomScpStub;
import com.mirth.connect.smoketest.stubs.JdbcSink;
import com.mirth.connect.smoketest.stubs.SmtpStub;
import com.mirth.connect.smoketest.stubs.SoapStub;

/**
 * Standalone proof that all four D-07 endpoint stubs work, with NO BridgeLink server
 * running. Each test allocates an ephemeral 127.0.0.1 port itself (bind-port-0 trick,
 * matching smoke-tests/run-smoke-test.sh's port allocation pattern) so this class can run
 * in complete isolation from the harness.
 */
public class StubSelfTest {

    /** Binds an ephemeral port on 127.0.0.1, releases it immediately, and returns the number. */
    private static int allocatePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    // ------------------------------------------------------------------
    // Test 1: SmtpStub (GreenMail)
    // ------------------------------------------------------------------

    @Test
    public void smtpStubReceivesAndReadsMail() throws Exception {
        int port = allocatePort();
        SmtpStub stub = new SmtpStub(port);
        stub.start();
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", "127.0.0.1");
            props.put("mail.smtp.port", String.valueOf(port));
            Session session = Session.getInstance(props);

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("sender@smoke-test.local"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("recipient@smoke-test.local"));
            message.setSubject("Smoke test");
            message.setText("Hello " + Hl7Messages.EXPECTED_PATIENT);
            Transport.send(message);

            MimeMessage[] received = pollForMessages(stub, 5);
            assertEquals(1, received.length);
            assertTrue(SmtpStub.getBody(received[0]).contains(Hl7Messages.EXPECTED_PATIENT));
        } finally {
            stub.stop();
        }
    }

    private static MimeMessage[] pollForMessages(SmtpStub stub, int timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        MimeMessage[] received = stub.getReceivedMessages();
        while (received.length == 0 && System.nanoTime() < deadline) {
            Thread.sleep(100);
            received = stub.getReceivedMessages();
        }
        return received;
    }

    // ------------------------------------------------------------------
    // Test 2: SoapStub (JAX-WS Endpoint.publish + A2 fallback)
    // ------------------------------------------------------------------

    @Test
    public void soapStubPublishesServesWsdlAndReceives() throws Exception {
        int port = allocatePort();
        SoapStub stub = new SoapStub(port);
        stub.start();
        try {
            assertWsdlAndEcho(stub);
        } finally {
            stub.stop();
        }
    }

    @Test
    public void soapStubFallbackServesCannedResponse() throws Exception {
        int port = allocatePort();
        SoapStub stub = new SoapStub(port, true);
        stub.start();
        try {
            assertWsdlAndEcho(stub);
        } finally {
            stub.stop();
        }
    }

    private static void assertWsdlAndEcho(SoapStub stub) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> wsdlResponse = client.send(
                HttpRequest.newBuilder(URI.create(stub.getUrl() + "?wsdl")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, wsdlResponse.statusCode());
        assertTrue(wsdlResponse.body().contains("definitions"));

        String envelope = "<?xml version=\"1.0\"?>"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "xmlns:tns=\"http://stubs.smoketest.connect.mirth.com/\">"
                + "<soapenv:Body><tns:receive><payload>hello-soap-stub</payload></tns:receive></soapenv:Body>"
                + "</soapenv:Envelope>";

        SOAPConnection connection = SOAPConnectionFactory.newInstance().createConnection();
        try {
            MessageFactory messageFactory = MessageFactory.newInstance();
            SOAPMessage request = messageFactory.createMessage();
            request.getSOAPPart().setContent(new StreamSource(new StringReader(envelope)));
            request.saveChanges();
            SOAPMessage response = connection.call(request, stub.getUrl());
            assertNotNull(response);
        } finally {
            connection.close();
        }
        // Real JAX-WS mode unmarshals to exactly the payload text; the A2 fallback mode
        // records the whole raw envelope (no XML parsing, by design) — either way the
        // recorded content must contain what was sent (D-07: "records the received
        // envelope body for later L2 assertions").
        assertTrue(stub.getLastReceivedPayload().contains("hello-soap-stub"));
    }

    // ------------------------------------------------------------------
    // Test 3: JdbcSink (SQLite cross-connection read-after-write)
    // ------------------------------------------------------------------

    @Test
    public void jdbcSinkCrossConnectionReadAfterWrite() throws Exception {
        File dbFile = File.createTempFile("smoke-jdbc-sink", ".db");
        dbFile.delete();
        dbFile.deleteOnExit();
        JdbcSink sink = new JdbcSink(dbFile);
        sink.createSchema();
        sink.insertRow(Hl7Messages.EXPECTED_PATIENT, Hl7Messages.ORU_R01_LF);

        // A second connection (simulating the harness re-open) reads the row back.
        List<String> names = sink.readPatientNames();
        assertEquals(1, names.size());
        assertEquals(Hl7Messages.EXPECTED_PATIENT, names.get(0));
    }

    // ------------------------------------------------------------------
    // Test 4: DicomScpStub (embedded DcmRcv)
    // ------------------------------------------------------------------

    @Test
    public void dicomScpStubStartsListensAndStopsCleanly() throws Exception {
        int port = allocatePort();
        File storageDir = Files.createTempDirectory("smoke-dicom-scp").toFile();
        DicomScpStub stub = new DicomScpStub(port, "SMOKE_SCP", storageDir);
        stub.start();
        try {
            assertTrue("DICOM SCP port should accept a TCP connection while running", stub.isListening());
        } finally {
            stub.stop();
        }
        // Clean stop: give the OS a moment to release the socket, then confirm no listener remains.
        Thread.sleep(300);
        assertFalse("DICOM SCP port should be closed after stop()", stub.isListening());
    }
}
