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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
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

import org.dcm4che2.tool.dcmsnd.DcmSnd;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.smoketest.stubs.DicomScpStub;
import com.mirth.connect.smoketest.stubs.JdbcSink;
import com.mirth.connect.smoketest.stubs.RecordingHttpStub;
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

    // ------------------------------------------------------------------
    // Test 4b: DicomScpStub opt-in TLS extension (18.4-02, Phase 18.4/NET-09,
    // RESEARCH Pattern 3) — a shared self-signed PKCS12 keystore/truststore is generated
    // once via keytool for these standalone tests (mirrors RESEARCH Pattern 1, scoped
    // locally here — the harness's own per-run keystore-gen stage is Plan 03's concern).
    // ------------------------------------------------------------------

    private static File dicomTlsKeystore;
    private static final String DICOM_TLS_KEYSTORE_PASSWORD = "smoketest-tls-selftest";

    @BeforeClass
    public static void generateDicomTlsSelfTestKeystore() throws Exception {
        File dir = Files.createTempDirectory("smoke-dicom-tls-selftest-keystore").toFile();
        dicomTlsKeystore = new File(dir, "dicom-tls-selftest.p12");
        // PKCS12 requires storepass == keypass (never pass -keypass, RESEARCH Pitfall 5).
        ProcessBuilder pb = new ProcessBuilder(
                "keytool", "-genkeypair",
                "-alias", "dicom-tls-selftest",
                "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1",
                "-dname", "CN=dicom-tls-selftest,O=BridgeLink Smoke Harness",
                "-keystore", dicomTlsKeystore.getAbsolutePath(),
                "-storetype", "PKCS12",
                "-storepass", DICOM_TLS_KEYSTORE_PASSWORD);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        byte[] output = proc.getInputStream().readAllBytes();
        int exit = proc.waitFor();
        assertEquals("keytool keystore generation should succeed: " + new String(output, StandardCharsets.UTF_8),
                0, exit);
    }

    /**
     * Positive proof: after {@code setTls("aes", ...)}, the embedded {@code DcmRcv} actually
     * completes a mutual-TLS handshake (client cert required both ways — the corrected
     * {@code noClientAuth=true}/{@code setTlsNeedClientAuth(true)} semantics, RESEARCH D-02
     * correction) and delivers the file. Also proves {@code setStgCmtReuseFrom} and
     * {@code setTls} compose — both are set before {@link DicomScpStub#start()}.
     */
    @Test
    public void dicomScpStubTlsAesOptInDeliversFileOverMutualTls() throws Exception {
        int port = allocatePort();
        File storageDir = Files.createTempDirectory("smoke-dicom-tls-aes-scp").toFile();
        DicomScpStub stub = new DicomScpStub(port, "SMOKE_TLS_AES_SCP", storageDir);
        stub.setStgCmtReuseFrom(false); // composes with TLS opt-in without conflict
        stub.setTls("aes", dicomTlsKeystore.getAbsolutePath(), DICOM_TLS_KEYSTORE_PASSWORD,
                dicomTlsKeystore.getAbsolutePath(), DICOM_TLS_KEYSTORE_PASSWORD);
        stub.start();
        try {
            assertTrue("TLS-enabled DICOM SCP port should accept a TCP connection while running",
                    stub.isListening());

            DcmSnd dcmSnd = new DcmSnd("SMOKE_TLS_AES_SCU");
            dcmSnd.setCalledAET("SMOKE_TLS_AES_SCP");
            dcmSnd.setRemoteHost("127.0.0.1");
            dcmSnd.setRemotePort(port);
            dcmSnd.addFile(new File("fixtures", "smoke-test.dcm"));
            dcmSnd.setPriority(0);
            dcmSnd.setTlsAES_128_CBC();
            dcmSnd.setKeyStoreURL(dicomTlsKeystore.getAbsolutePath());
            dcmSnd.setKeyStorePassword(DICOM_TLS_KEYSTORE_PASSWORD);
            dcmSnd.setTrustStoreURL(dicomTlsKeystore.getAbsolutePath());
            dcmSnd.setTrustStorePassword(DICOM_TLS_KEYSTORE_PASSWORD);
            dcmSnd.setTlsNeedClientAuth(true);
            dcmSnd.configureTransferCapability();
            dcmSnd.initTLS();
            dcmSnd.start();
            boolean opened = false;
            try {
                dcmSnd.open();
                opened = true;
                dcmSnd.send();
            } finally {
                if (opened) {
                    dcmSnd.close();
                }
                dcmSnd.stop();
            }
            assertEquals("aes TLS opt-in round trip should deliver the file over mutual TLS",
                    dcmSnd.getNumberOfFilesToSend(), dcmSnd.getNumberOfFilesSent());

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            File[] received = stub.listReceivedFiles();
            while (received.length == 0 && System.nanoTime() < deadline) {
                Thread.sleep(100);
                received = stub.listReceivedFiles();
            }
            assertEquals(1, received.length);
        } finally {
            stub.stop();
        }
    }

    /**
     * Negative proof tying Task 1 (D-04 overlay) directly to Task 3's wiring: with the
     * default JDK policy ({@code 3DES_EDE_CBC} on {@code jdk.tls.disabledAlgorithms}), a
     * {@code setTls("3des", ...)}-enabled SCP still starts and accepts a bare TCP connection
     * (the TLS server socket binds fine), but an actual TLS handshake attempt FAILS — this is
     * exactly why the {@code dicom-tls-3des.security} overlay (Task 1) is required for the
     * harness's {@code tls=3des} channel, and this test demonstrates the failure mode without
     * needing to apply that JVM-wide override in-process.
     */
    @Test
    public void dicomScpStubTlsThreeDesOptInStartsButHandshakeFailsWithoutJdkOverlay() throws Exception {
        int port = allocatePort();
        File storageDir = Files.createTempDirectory("smoke-dicom-tls-3des-scp").toFile();
        DicomScpStub stub = new DicomScpStub(port, "SMOKE_TLS_3DES_SCP", storageDir);
        stub.setTls("3des", dicomTlsKeystore.getAbsolutePath(), DICOM_TLS_KEYSTORE_PASSWORD,
                dicomTlsKeystore.getAbsolutePath(), DICOM_TLS_KEYSTORE_PASSWORD);
        stub.start();
        try {
            assertTrue("3des TLS-enabled SCP should still accept a bare TCP connection (TLS server socket bound)",
                    stub.isListening());

            DcmSnd dcmSnd = new DcmSnd("SMOKE_TLS_3DES_SCU");
            dcmSnd.setCalledAET("SMOKE_TLS_3DES_SCP");
            dcmSnd.setRemoteHost("127.0.0.1");
            dcmSnd.setRemotePort(port);
            dcmSnd.addFile(new File("fixtures", "smoke-test.dcm"));
            dcmSnd.setPriority(0);
            dcmSnd.setTls3DES_EDE_CBC();
            dcmSnd.setKeyStoreURL(dicomTlsKeystore.getAbsolutePath());
            dcmSnd.setKeyStorePassword(DICOM_TLS_KEYSTORE_PASSWORD);
            dcmSnd.setTrustStoreURL(dicomTlsKeystore.getAbsolutePath());
            dcmSnd.setTrustStorePassword(DICOM_TLS_KEYSTORE_PASSWORD);
            dcmSnd.setTlsNeedClientAuth(true);
            dcmSnd.configureTransferCapability();
            dcmSnd.initTLS();
            dcmSnd.start();
            boolean opened = false;
            Exception handshakeFailure = null;
            try {
                dcmSnd.open();
                opened = true;
            } catch (Exception e) {
                handshakeFailure = e;
            } finally {
                if (opened) {
                    dcmSnd.close();
                }
                dcmSnd.stop();
            }
            assertNotNull("3des handshake should FAIL under the default JDK policy (3DES_EDE_CBC "
                    + "disabled) -- this is exactly why the D-04 java.security overlay is required "
                    + "for the harness's tls=3des channel", handshakeFailure);
        } finally {
            stub.stop();
        }
    }

    // ------------------------------------------------------------------
    // Test 5: RecordingHttpStub (plan 18.1-01) — /record, /auth (401-challenge), /stall
    // ------------------------------------------------------------------

    @Test
    public void recordingHttpStubRecordsMethodPathQueryHeadersAndBody() throws Exception {
        int port = allocatePort();
        RecordingHttpStub stub = new RecordingHttpStub(port);
        stub.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/record?smokeQ=q1"))
                    .header("X-Smoke-Req", "r1")
                    .POST(HttpRequest.BodyPublishers.ofString("smoke-body")).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());

            List<RecordingHttpStub.RecordedRequest> recorded = stub.getRequests("/record");
            assertEquals(1, recorded.size());
            RecordingHttpStub.RecordedRequest r = recorded.get(0);
            assertEquals("POST", r.method);
            assertEquals("/record", r.path);
            assertEquals("smokeQ=q1", r.query);
            assertTrue("Header lookup should be case-insensitive",
                    r.headers.containsKey("x-smoke-req"));
            assertEquals("r1", r.headers.get("x-smoke-req").get(0));
            assertEquals("smoke-body", new String(r.body, StandardCharsets.UTF_8));
        } finally {
            stub.stop();
        }
    }

    @Test
    public void recordingHttpStubChallengesThenAcceptsBasicAuth() throws Exception {
        int port = allocatePort();
        RecordingHttpStub stub = new RecordingHttpStub(port);
        stub.start();
        try {
            HttpClient client = HttpClient.newHttpClient();

            // Bare request: no Authorization header -> 401 + WWW-Authenticate challenge.
            HttpRequest bareRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> bareResponse = client.send(bareRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, bareResponse.statusCode());
            String challenge = bareResponse.headers().firstValue("WWW-Authenticate").orElse("");
            assertTrue("Expected a Basic challenge, got: " + challenge, challenge.startsWith("Basic"));

            // Resend with Authorization header -> 200.
            String credentials = Base64.getEncoder().encodeToString("smokeuser:smokepass".getBytes(StandardCharsets.UTF_8));
            HttpRequest authedRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth"))
                    .header("Authorization", "Basic " + credentials)
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> authedResponse = client.send(authedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, authedResponse.statusCode());

            List<RecordingHttpStub.RecordedRequest> recorded = stub.getRequests("/auth");
            assertEquals(2, recorded.size());
            assertFalse("First request should carry no Authorization header",
                    recorded.get(0).headers.containsKey("Authorization"));
            assertTrue("Second request should carry an Authorization header",
                    recorded.get(1).headers.containsKey("Authorization"));
        } finally {
            stub.stop();
        }
    }

    @Test
    public void recordingHttpStubStallsThenResponds() throws Exception {
        int port = allocatePort();
        // Short stall (200ms) to keep the self-test fast — the full ~5s stall is exercised by
        // the real D-06 timeout channel in plan 18.1-05, not here.
        RecordingHttpStub stub = new RecordingHttpStub(port, 200);
        stub.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/stall"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());

            List<RecordingHttpStub.RecordedRequest> recorded = stub.getRequests("/stall");
            assertEquals(1, recorded.size());
        } finally {
            stub.stop();
        }
    }
}
