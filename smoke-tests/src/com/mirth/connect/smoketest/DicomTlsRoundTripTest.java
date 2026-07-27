package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.NoPresentationContextException;
import org.dcm4che2.tool.dcmsnd.DcmSnd;
import org.junit.Test;

import com.mirth.connect.smoketest.stubs.DicomScpStub;

/**
 * NET-09 dedicated JUnit suite closing out Phase 18.4 — the DICOM connector's TLS/security
 * surface. Drives a real mutual-TLS inbound C-STORE into each deployed TLS Listener
 * ({@code dicom-tls-aes-roundtrip-test.xml} / {@code dicom-tls-3des-roundtrip-test.xml}, Plan
 * 02/03), verifies the deployed channel's Sender forwards the transformed object to a
 * dedicated TLS-configured {@link DicomScpStub}, and positively proves TLS engaged (D-03): the
 * negotiated cipher suite read reflectively from the SCU's own live {@link SSLSession}, PLUS a
 * rejected-plaintext negative probe against both TLS Listener ports.
 *
 * <p>This is a NEW, dedicated class — {@link DicomRoundTripTest} (18.3, plaintext) is untouched
 * (additive per that class's own principle).
 *
 * <p><b>CRITICAL (empirical finding, Plan 01/02):</b> dcm4che2's {@code NetworkConnection}
 * defaults its {@code tlsProtocol} field to {@code {"TLSv1", "SSLv3", "SSLv2Hello"}} — all
 * disabled by the JDK's own {@code jdk.tls.disabledAlgorithms} baseline on any JDK 17+ target.
 * The embedded {@link DcmSnd} driver below therefore explicitly calls
 * {@code setTlsProtocol({"TLSv1.3","TLSv1.2"})}, mirroring
 * {@code DICOMConfigurationUtil}/{@code DicomScpStub}/{@code DicomTlsProviderDiagnostic} — or
 * every handshake fails with {@code SSLHandshakeException: No appropriate protocol}, for BOTH
 * aes and 3des.
 *
 * <p><b>Pitfall 1 tell (mutual-auth-not-enforced):</b> the round trip must NOT pass with only
 * one peer's keystore configured. {@code setTlsNeedClientAuth(true)} is set on both the
 * embedded SCU (this class) and the embedded SCP ({@link DicomScpStub#setTls}) — a one-way-TLS
 * misconfiguration would either fail the handshake outright (server requires a client cert
 * that was never presented) or, if silently permissive, still be caught by this test's
 * cipher-suite + files-sent assertions failing rather than a bare "did a file arrive" check.
 */
public class DicomTlsRoundTripTest extends SmokeTestBase {

    private static final String AES_CHANNEL_ID = "00000025-0000-0000-0000-000000000025";
    private static final String DES_CHANNEL_ID = "00000026-0000-0000-0000-000000000026";

    private static final String EXPECTED_AES_CIPHER_SUITE = "TLS_RSA_WITH_AES_128_CBC_SHA";
    private static final String EXPECTED_3DES_CIPHER_SUITE = "SSL_RSA_WITH_3DES_EDE_CBC_SHA";

    private static final File SOURCE_FIXTURE = new File("fixtures", "smoke-test.dcm");

    /** The source fixture's original (untransformed) PatientName — proves mutation, not mere presence. */
    private static final String SOURCE_PATIENT_NAME_UNTRANSFORMED = "McDoogal^Hattie";

    /** The value both TLS round-trip channels' tag-tree-mutating transform writes. */
    private static final String EXPECTED_MUTATED_PATIENT_NAME = "RoundTripPatient";

    /** D-03: every non-mutated dataset tag asserted for full source-vs-received preservation (mirrors 18.3). */
    private static final int[] PRESERVED_TAGS = {
            Tag.SOPClassUID, Tag.SOPInstanceUID, Tag.Modality, Tag.PatientID,
            Tag.StudyInstanceUID, Tag.SeriesInstanceUID, Tag.SamplesPerPixel, Tag.BitsAllocated
    };

    /**
     * Local replica of {@code SmokeTestBase}'s private {@code requireProperty} /
     * {@code DicomRoundTripTest}'s {@code requireDicomProperty} — deliberately NOT added to
     * {@link SmokeTestBase#baseSetUp()}, so other test classes keep running unmodified without
     * these six new {@code -D} properties set (mirrors the established own-property convention).
     */
    private static String requireDicomTlsProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required system property '" + name + "' not set. "
                    + "DicomTlsRoundTripTest only runs against a live harness — invoke via "
                    + "smoke-tests/run-smoke-test.sh, which passes harness ports/paths as -D "
                    + "properties to `ant -f smoke-tests/build.xml test-run`.");
        }
        return value;
    }

    @Test
    public void aesRoundTrip() throws Exception {
        // scpAeTitle MUST match the deployed channel's destination
        // <applicationEntity> (dicom-tls-aes-roundtrip-test.xml) — the Sender uses this as
        // the CALLED AE title when it connects out to this stub; a mismatch is rejected by
        // dcm4che2 with A-ASSOCIATE-RJ[reason=7]: called-AE-title-not-recognized (Rule 1 bug,
        // caught live during this plan's own harness run).
        runTlsRoundTrip("aes", "DICOM_TLS_AES_LISTENER_PORT", "DICOM_TLS_AES_SCP_PORT",
                "SMOKEHARNESS3", AES_CHANNEL_ID, EXPECTED_AES_CIPHER_SUITE);
    }

    @Test
    public void desRoundTrip() throws Exception {
        // scpAeTitle MUST match dicom-tls-3des-roundtrip-test.xml's destination
        // <applicationEntity> — see aesRoundTrip()'s comment above.
        runTlsRoundTrip("3des", "DICOM_TLS_3DES_LISTENER_PORT", "DICOM_TLS_3DES_SCP_PORT",
                "SMOKEHARNESS4", DES_CHANNEL_ID, EXPECTED_3DES_CIPHER_SUITE);
    }

    /**
     * Rejected-plaintext probe (SC-3/T-18.4-01, RESEARCH Code Examples): a raw non-TLS byte
     * stream against both TLS Listener ports must yield connection reset/EOF/timeout, never a
     * valid DICOM response — catches silent downgrade-to-plaintext.
     */
    @Test
    public void plaintextRejectedOnBothTlsListeners() throws Exception {
        int aesListenerPort = Integer.parseInt(requireDicomTlsProperty("DICOM_TLS_AES_LISTENER_PORT"));
        int desListenerPort = Integer.parseInt(requireDicomTlsProperty("DICOM_TLS_3DES_LISTENER_PORT"));

        assertPlaintextRejected(aesListenerPort);
        assertPlaintextRejected(desListenerPort);
    }

    /**
     * Drives one full TLS round trip: starts a dedicated TLS-configured {@link DicomScpStub},
     * drives a real mutual-TLS inbound C-STORE into the deployed Listener with an embedded
     * plain-vendor {@link DcmSnd}, positively proves TLS engaged via reflection on the SCU's
     * own live {@link SSLSession}, then asserts the deployed channel's Sender forwarded the
     * transformed object to the stub with full SOP-UID/transform fidelity.
     */
    private void runTlsRoundTrip(String cipher, String listenerPortProperty, String scpPortProperty,
            String scpAeTitle, String channelId, String expectedCipherSuite) throws Exception {
        String keystore = requireDicomTlsProperty("DICOM_TLS_KEYSTORE");
        String keystorePw = requireDicomTlsProperty("DICOM_TLS_KEYSTORE_PW");
        String listenerPort = requireDicomTlsProperty(listenerPortProperty);
        String scpPort = requireDicomTlsProperty(scpPortProperty);

        // (1) Dedicated TLS-configured DicomScpStub — own port/dir, own fresh temp storage
        // (mirrors DicomRoundTripTest's Pitfall-7 dedicated-stub precedent). setTls(...) +
        // setStgCmtReuseFrom(true) MUST be called BEFORE start().
        File storageDir = Files.createTempDirectory("smoke-dicom-tls-" + cipher + "-received").toFile();
        DicomScpStub scpStub = new DicomScpStub(Integer.parseInt(scpPort), scpAeTitle, storageDir);
        scpStub.setTls(cipher, keystore, keystorePw, keystore, keystorePw);
        scpStub.setStgCmtReuseFrom(true);
        scpStub.start();
        registerStubStop(scpStub::stop);

        DicomObject sourceObj = parseDicomObject(SOURCE_FIXTURE);

        // (2)/(3) Drive the inbound C-STORE, capturing the live negotiated cipher suite.
        String negotiatedCipherSuite = driveInboundCStore(cipher, listenerPort, keystore, keystorePw);

        // (3) Positive TLS-engagement proof (D-03). Per Assumption A3, the aes leg is
        // expected to negotiate specifically to TLS_RSA_WITH_AES_128_CBC_SHA (confirmed
        // empirically by Plan 01's diagnostic) — if a future run ever observes the fallback
        // suite instead, that is a FINDING to investigate, never silently auto-pinned.
        assertEquals("Negotiated TLS cipher suite for tls=" + cipher + " must match the "
                + "expected value (D-03 positive TLS-engagement proof) — a mismatch here means "
                + "either the wrong cipher negotiated or TLS did not truly engage as configured",
                expectedCipherSuite, negotiatedCipherSuite);

        // (4)/(5)/(6) The deployed channel's Sender forwards the transformed object to the
        // stub — assert full SOP-UID/transform fidelity (mirrors DicomRoundTripTest's D-04
        // strengthened preserved-tag assertion).
        assertThreeLevels(channelId, 1, () -> {
            pollUntil("TLS DicomScpStub (" + cipher + ") received a fully-parseable file with a non-null PatientName",
                    30, () -> {
                        File[] files = scpStub.listReceivedFiles();
                        if (files.length == 0) {
                            return false;
                        }
                        try {
                            DicomObject candidate = parseDicomObject(files[0]);
                            return candidate.getString(Tag.PatientName) != null;
                        } catch (Exception e) {
                            return false;
                        }
                    });

            File received = scpStub.listReceivedFiles()[0];
            try (DicomInputStream dis = new DicomInputStream(received)) {
                dis.setAllocateLimit(-1);
                DicomObject obj = new BasicDicomObject();
                dis.readDicomObject(obj, -1);

                assertEquals("Transform must have landed: PatientName should be the mutated value",
                        EXPECTED_MUTATED_PATIENT_NAME, obj.getString(Tag.PatientName));
                assertNotEquals("Transform must have CHANGED PatientName, not merely left it present",
                        SOURCE_PATIENT_NAME_UNTRANSFORMED, obj.getString(Tag.PatientName));
                assertNotNull("SOPInstanceUID should be present/well-formed", obj.getString(Tag.SOPInstanceUID));

                for (int tag : PRESERVED_TAGS) {
                    assertEquals("Non-mutated tag " + Integer.toHexString(tag) + " must be preserved end-to-end",
                            sourceObj.getString(tag), obj.getString(tag));
                }
            }
        });
    }

    private static DicomObject parseDicomObject(File file) throws IOException {
        try (DicomInputStream dis = new DicomInputStream(file)) {
            dis.setAllocateLimit(-1);
            DicomObject obj = new BasicDicomObject();
            dis.readDicomObject(obj, -1);
            return obj;
        }
    }

    /**
     * Embeds the PLAIN vendor {@code DcmSnd} (never the server-side, configuration-bound
     * subclass — mirrors {@link DicomRoundTripTest}'s own Pitfall 1) to drive a real mutual-TLS
     * inbound C-STORE against the deployed TLS Listener. TLS init call order (RESEARCH Pattern
     * 2, verified against {@code DICOMConfigurationUtil}): set cipher -> keystore/truststore
     * URL+password -> {@code setTlsNeedClientAuth(true)} -> {@code setTlsProtocol(...)} ->
     * {@code configureTransferCapability()} -> {@code initTLS()} -> {@code start()} ->
     * {@code open()} -> {@code send()}. {@code initTLS()} MUST precede {@code start()}/
     * {@code open()} (Pitfall 6) — calling it after would silently use a plain socket or throw.
     *
     * <p>Returns the negotiated cipher suite, captured via reflection on the private
     * {@code assoc} field AFTER {@code open()} succeeds but BEFORE {@code close()} releases
     * the association (the socket is still live at that point).
     */
    private String driveInboundCStore(String cipher, String listenerPort, String keystore, String keystorePw)
            throws Exception {
        DcmSnd dcmSnd = new DcmSnd("SMOKEHARNESS-TLS-" + cipher.toUpperCase() + "-SCU");
        // DICOM AE titles are capped at 16 characters (AAssociateRQAC.setCallingAET) — the
        // constructed name above (e.g. "SMOKEHARNESS-TLS-AES-SCU" is 24 chars) would violate
        // that. Use an explicit, short local calling AE title instead of relying on the
        // constructor's device-name default.
        dcmSnd.setCalling(cipher.equals("aes") ? "TLSAESSCU" : "TLS3DESSCU");
        // Matches the TLS round-trip channels' blank Listener applicationEntity (accepts any
        // called AET, mirrors DicomRoundTripTest's LISTENER_CALLED_AET precedent) — an
        // arbitrary, non-empty, <=16-char AE title works since the Listener never validates it.
        dcmSnd.setCalledAET(cipher.equals("aes") ? "TLSAESLISTENER" : "TLS3DESLISTENR");
        dcmSnd.setRemoteHost("127.0.0.1");
        dcmSnd.setRemotePort(Integer.parseInt(listenerPort));
        dcmSnd.addFile(SOURCE_FIXTURE);
        // med priority (0) — DICOMDispatcher.java's own med=0/low=1/high=2 mapping; not
        // asserted by this test.
        dcmSnd.setPriority(0);

        if ("aes".equals(cipher)) {
            dcmSnd.setTlsAES_128_CBC();
        } else if ("3des".equals(cipher)) {
            dcmSnd.setTls3DES_EDE_CBC();
        } else {
            throw new IllegalArgumentException("Unknown cipher: " + cipher);
        }
        dcmSnd.setKeyStoreURL(keystore);
        dcmSnd.setKeyStorePassword(keystorePw);
        dcmSnd.setTrustStoreURL(keystore);
        dcmSnd.setTrustStorePassword(keystorePw);
        // Mutual auth ON — corrected noClientAuth semantics (D-02/RESEARCH Pitfall 1). Never
        // configure a separate key password (Pitfall 5) — PKCS12 requires keypass==storepass,
        // so only the keystore password setter above is ever called.
        dcmSnd.setTlsNeedClientAuth(true);
        // Rule 1 fix carried forward from Plan 01/02: dcm4che2's own TLS_AND_SSLv2 default
        // ({"TLSv1","SSLv3","SSLv2Hello"}) is entirely disabled by the JDK's default
        // jdk.tls.disabledAlgorithms policy — mirror DICOMConfigurationUtil's server-side
        // protocol list, or every handshake fails with "No appropriate protocol".
        dcmSnd.setTlsProtocol(new String[] { "TLSv1.3", "TLSv1.2" });

        dcmSnd.configureTransferCapability();
        // MUST precede start()/open() — createTLSSocket() happens inside open(), and requires
        // initTLS() to have already populated the KeyManagerFactory/TrustManagerFactory state.
        dcmSnd.initTLS();
        dcmSnd.start();

        boolean opened = false;
        String negotiatedCipherSuite;
        try {
            dcmSnd.open();
            opened = true;

            // Positive TLS-engagement proof (D-03) — read the live SSLSession BEFORE send()/
            // close() release the association.
            negotiatedCipherSuite = capturedCipherSuite(dcmSnd);

            dcmSnd.send();
        } catch (NoPresentationContextException e) {
            throw new AssertionError("inbound C-STORE negotiation failed for tls=" + cipher
                    + " — confirm the SCU offers a compatible transfer syntax", e);
        } finally {
            // Only release an association that was actually opened (assoc is null otherwise;
            // DcmSnd.close() would NPE and mask the real open()/TLS-handshake failure).
            if (opened) {
                dcmSnd.close();
            }
            dcmSnd.stop();
        }

        assertEquals("inbound C-STORE for tls=" + cipher + " failed to transfer the file — "
                + "DcmSnd.send() swallows NoPresentationContextException internally per-file, so "
                + "0 files sent here means no presentation context was negotiated (Pitfall 1 "
                + "tell: this must NOT silently pass with a broken/one-way TLS config)",
                dcmSnd.getNumberOfFilesToSend(), dcmSnd.getNumberOfFilesSent());

        return negotiatedCipherSuite;
    }

    /**
     * Reflective negotiated-cipher-suite proof (D-03 positive proof, RESEARCH Code Examples).
     * {@code Association.getSocket()} is public; {@code DcmSnd}'s {@code assoc} field is
     * private, so reflection is required to reach it from test code in a different package.
     */
    private static String capturedCipherSuite(DcmSnd dcmSnd) throws Exception {
        Field assocField = DcmSnd.class.getDeclaredField("assoc");
        assocField.setAccessible(true);
        Association assoc = (Association) assocField.get(dcmSnd);
        Socket socket = assoc.getSocket();
        assertTrue("Expected an SSLSocket on this association — TLS was not engaged "
                + "(actual class: " + socket.getClass().getName() + ")",
                socket instanceof SSLSocket);
        SSLSession session = ((SSLSocket) socket).getSession();
        return session.getCipherSuite();
    }

    /**
     * Rejected-plaintext negative probe (SC-3/T-18.4-01 — RESEARCH Code Examples, modeled on
     * the existing bare-TCP-connect probe pattern in {@code run-smoke-test.sh}'s
     * {@code wait_for_listener_ports()}). A raw byte sequence that is NOT a valid TLS
     * ClientHello and NOT a valid A-ASSOCIATE-RQ PDU must yield connection reset/EOF/timeout,
     * never a valid response — proves the port is TLS-only, catching silent
     * downgrade-to-plaintext regressions.
     */
    private static void assertPlaintextRejected(int tlsPort) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", tlsPort), 3000);
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(new byte[] { 0x01, 0x00, 0x00, 0x00 });
            int firstByte = socket.getInputStream().read();
            assertFalse("Expected the TLS-only port " + tlsPort + " to reject a raw plaintext "
                    + "probe, but it responded with byte " + firstByte + " — possible silent "
                    + "TLS downgrade regression (T-18.4-01)",
                    firstByte != -1);
        } catch (SocketTimeoutException | EOFException expected) {
            // expected: TLS-only listener drops/hangs on a non-TLS byte stream.
        }
    }
}
