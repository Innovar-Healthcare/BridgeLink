package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;

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
 * negotiated cipher suite read reflectively from the SCU's own live {@link SSLSession}. Two
 * dedicated falsifiable negative tests close out the anti-regression coverage (Plan 05, CR-01/
 * WR-01): a real non-TLS DICOM association attempt against each TLS-only Listener port (catches
 * silent downgrade-to-plaintext), and an untrusted-client-cert C-STORE attempt against a
 * mutual-TLS Listener (independently proves {@code setTlsNeedClientAuth(true)} enforcement).
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
 * <p><b>Pitfall 1 tell (mutual-auth-not-enforced) — CORRECTED coverage claim (closes WR-01):</b>
 * the round trip must NOT pass with only one peer's keystore configured.
 * {@code setTlsNeedClientAuth(true)} is set on both the embedded SCU (this class) and the
 * embedded SCP ({@link DicomScpStub#setTls}), but the negotiated cipher suite and files-sent
 * count in {@code runTlsRoundTrip} are NOT proof that mutual client authentication is enforced
 * — both are independent of whether the server actually validated the client's certificate, so
 * a {@code setTlsNeedClientAuth(true)->false} regression would leave every assertion in
 * {@code runTlsRoundTrip} passing. Mutual-auth enforcement is instead proven independently by
 * {@link #mutualAuthEnforced_untrustedClientCertRejected()}: an SCU presenting a client
 * certificate the server does not trust must fail its C-STORE association, which goes red iff
 * the server stops requiring a trusted client cert.
 *
 * <p><b>Rejected-plaintext negative probe — CORRECTED mechanism (closes CR-01):</b> silent
 * TLS-to-plaintext downgrade of a Listener port is caught by
 * {@link #plaintextRejectedOnBothTlsListeners()}, which drives a real, genuinely plaintext
 * {@code DcmSnd} (no TLS setters, no {@code initTLS()}) and asserts {@code open()} fails
 * against each TLS-only Listener port — NOT by observing a raw-byte read timeout (the prior
 * mechanism could not distinguish "TLS rejected me" from "nothing responded" and would have
 * passed vacuously against an actual plaintext-downgrade regression).
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
     * WR-01 mutual-auth negative test (Task 2): password for the freshly-generated,
     * never-registered self-signed client keystore used to realize "no server-trusted
     * client cert." PKCS12 requires storepass == keypass — never pass a separate key
     * password (RESEARCH Pitfall 5).
     */
    private static final String UNTRUSTED_CLIENT_KEYSTORE_PASSWORD = "smoketest-untrusted-tls";

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
     * Falsifiable plaintext-downgrade probe (SC-3/T-18.4-01, closes CR-01): a real, NON-TLS
     * DICOM association attempt against each TLS-only Listener port must fail to
     * {@code open()}. Replaces the unsound truncated-byte / read-timeout-as-rejection probe
     * (code review CR-01) — that probe could not distinguish "TLS rejected me" from
     * "nothing responded within the timeout," so it would have vacuously passed even if a
     * port silently served plaintext. This probe is genuinely falsifiable: if either port
     * ever accepted the plaintext association, {@code open()} would succeed and this test
     * would go red.
     */
    @Test
    public void plaintextRejectedOnBothTlsListeners() throws Exception {
        int aesListenerPort = Integer.parseInt(requireDicomTlsProperty("DICOM_TLS_AES_LISTENER_PORT"));
        int desListenerPort = Integer.parseInt(requireDicomTlsProperty("DICOM_TLS_3DES_LISTENER_PORT"));

        assertNonTlsAssociationRejected(aesListenerPort, "aes");
        assertNonTlsAssociationRejected(desListenerPort, "3des");
    }

    /**
     * WR-01 falsifiable negative probe: an SCU that presents a client certificate NOT in the
     * server's truststore must fail its C-STORE association against a mutual-TLS Listener
     * that enforces {@code setTlsNeedClientAuth(true)}. The probe SCU still trusts the
     * server (its truststore is the SAME shared {@code DICOM_TLS_KEYSTORE} the Listener's
     * keystore comes from), so a failure here is caused SPECIFICALLY by the server refusing
     * an untrusted client identity — independent of cipher-suite negotiation or
     * files-sent, which is why {@code runTlsRoundTrip}'s positive assertions cannot catch a
     * {@code setTlsNeedClientAuth(true)->false} regression (closes WR-01). Because
     * {@code DcmSnd.initTLS()} requires a loadable keystore, "no server-trusted client
     * cert" is realized as a freshly-generated, never-registered self-signed keystore. One
     * cipher leg (aes) is sufficient — mutual-auth enforcement is cipher-independent — to
     * stay inside the SC-4 duration budget.
     */
    @Test
    public void mutualAuthEnforced_untrustedClientCertRejected() throws Exception {
        String sharedKeystore = requireDicomTlsProperty("DICOM_TLS_KEYSTORE");
        String sharedKeystorePw = requireDicomTlsProperty("DICOM_TLS_KEYSTORE_PW");
        int listenerPort = Integer.parseInt(requireDicomTlsProperty("DICOM_TLS_AES_LISTENER_PORT"));

        File untrustedKeystore = generateUntrustedClientKeystore();

        assertMutualAuthRejectsUntrustedClientCert(listenerPort,
                untrustedKeystore.getAbsolutePath(), UNTRUSTED_CLIENT_KEYSTORE_PASSWORD,
                sharedKeystore, sharedKeystorePw);
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
     * Falsifiable non-TLS negative-association probe (SC-3/T-18.4-01, closes CR-01 —
     * replaces the unsound truncated-byte / read-timeout-as-rejection probe). Mirrors
     * {@link #driveInboundCStore}'s SCU construction MINUS every TLS setter and MINUS
     * {@code initTLS()} — a genuinely plaintext SCU. Bounded with short connect/accept
     * timeouts (SC-4) so a hang cannot reintroduce the old multi-second-per-port wait. If
     * the TLS-only port ever silently accepted this plaintext association, {@code open()}
     * would succeed and this assertion would go red — the probe is falsifiable against the
     * exact regression it exists to catch.
     */
    private static void assertNonTlsAssociationRejected(int tlsPort, String cipherLabel) throws IOException {
        DcmSnd plainProbe = new DcmSnd("TLSPLAINPROBE-" + cipherLabel.toUpperCase());
        plainProbe.setCalling("aes".equals(cipherLabel) ? "PLAINPROBEAES" : "PLAINPROBE3DES");
        // Matches the TLS round-trip channels' blank Listener applicationEntity (accepts any
        // called AET, see driveInboundCStore's comment) — the Listener never validates it.
        plainProbe.setCalledAET("aes".equals(cipherLabel) ? "TLSAESLISTENER" : "TLS3DESLISTENR");
        plainProbe.setRemoteHost("127.0.0.1");
        plainProbe.setRemotePort(tlsPort);
        plainProbe.addFile(SOURCE_FIXTURE);
        plainProbe.setPriority(0);
        // Bound the attempt (SC-4) — no setTls*()/initTLS() call below, so this SCU never
        // attempts a TLS handshake; it is a genuinely plaintext DICOM association.
        plainProbe.setConnectTimeout(1500);
        plainProbe.setAcceptTimeout(1500);

        plainProbe.configureTransferCapability();
        plainProbe.start();

        boolean opened = false;
        try {
            plainProbe.open();
            opened = true;
            fail("TLS-only Listener port " + tlsPort + " (tls=" + cipherLabel + ") accepted a "
                    + "PLAINTEXT DICOM association — silent TLS downgrade regression "
                    + "(T-18.4-01, CR-01)");
        } catch (Exception expected) {
            // expected: a TLS-only Listener must refuse a plaintext association attempt —
            // any exception here (handshake/protocol/IO/timeout) is proof of rejection.
        } finally {
            // Only release an association that actually opened (mirrors driveInboundCStore's
            // opened guard — assoc is null otherwise, and close() would NPE and mask the
            // real open() result).
            if (opened) {
                plainProbe.close();
            }
            plainProbe.stop();
        }
    }

    /**
     * Generates a fresh, self-signed PKCS12 keystore that is NEVER added to the shared
     * {@code DICOM_TLS_KEYSTORE} the Listener trusts (WR-01) — mirrors {@code StubSelfTest}'s
     * {@code generateDicomTlsSelfTestKeystore()} keytool invocation pattern. Lives under a
     * dedicated {@link Files#createTempDirectory}, never committed (only under
     * {@code smoke-tests/}-relative temp paths, never checked in).
     */
    private static File generateUntrustedClientKeystore() throws IOException, InterruptedException {
        File dir = Files.createTempDirectory("smoke-dicom-tls-untrusted-client-keystore").toFile();
        File keystoreFile = new File(dir, "untrusted-client.p12");
        // PKCS12 requires storepass == keypass — never pass a separate -keypass (Pitfall 5).
        ProcessBuilder pb = new ProcessBuilder(
                "keytool", "-genkeypair",
                "-alias", "dicom-tls-untrusted-client",
                "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1",
                "-dname", "CN=dicom-tls-untrusted-client,O=BridgeLink Smoke Harness (untrusted)",
                "-keystore", keystoreFile.getAbsolutePath(),
                "-storetype", "PKCS12",
                "-storepass", UNTRUSTED_CLIENT_KEYSTORE_PASSWORD);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        byte[] output = proc.getInputStream().readAllBytes();
        int exit = proc.waitFor();
        if (exit != 0) {
            throw new IOException("keytool untrusted-client-cert keystore generation failed "
                    + "(exit " + exit + "): " + new String(output, StandardCharsets.UTF_8));
        }
        return keystoreFile;
    }

    /**
     * WR-01 falsifiable mutual-auth negative probe. Configures an SCU exactly like
     * {@link #driveInboundCStore} (cipher, {@code setTlsNeedClientAuth(true)},
     * {@code setTlsProtocol}, {@code initTLS()} before {@code start()}/{@code open()})
     * EXCEPT the keystore is the untrusted, never-registered self-signed cert while the
     * truststore remains the shared keystore the Listener's own cert comes from — so the
     * SCU still trusts the server, and the ONLY broken trust direction is server-side
     * client-cert validation. Asserts the association/C-STORE fails; a redundant
     * files-sent-count assertion covers the case where a handshake were ever silently
     * permitted despite the untrusted cert (independent of cipher-suite/files-sent, per
     * WR-01). FALSIFIABLE: a {@code setTlsNeedClientAuth(true)->false} regression on the
     * Listener would let this untrusted cert through, and this probe would go red.
     */
    private static void assertMutualAuthRejectsUntrustedClientCert(int listenerPort,
            String untrustedKeystore, String untrustedKeystorePw,
            String trustedKeystore, String trustedKeystorePw) throws IOException {
        DcmSnd untrustedProbe = new DcmSnd("TLSUNTRUSTEDPROBE-AES");
        untrustedProbe.setCalling("UNTRUSTEDCLIENT");
        untrustedProbe.setCalledAET("TLSAESLISTENER");
        untrustedProbe.setRemoteHost("127.0.0.1");
        untrustedProbe.setRemotePort(listenerPort);
        untrustedProbe.addFile(SOURCE_FIXTURE);
        untrustedProbe.setPriority(0);

        // One cipher leg (aes) is sufficient — mutual-auth enforcement is cipher-independent.
        untrustedProbe.setTlsAES_128_CBC();
        // Untrusted client identity: keyStore is the fresh self-signed cert the Listener
        // never saw; trustStore is the SHARED keystore, so the SCU still trusts the
        // server — the only broken trust direction is server->client validation (WR-01).
        untrustedProbe.setKeyStoreURL(untrustedKeystore);
        untrustedProbe.setKeyStorePassword(untrustedKeystorePw);
        untrustedProbe.setTrustStoreURL(trustedKeystore);
        untrustedProbe.setTrustStorePassword(trustedKeystorePw);
        untrustedProbe.setTlsNeedClientAuth(true);
        untrustedProbe.setTlsProtocol(new String[] { "TLSv1.3", "TLSv1.2" });
        // Bound the attempt (SC-4) so a hung handshake cannot reintroduce a multi-second wait.
        untrustedProbe.setConnectTimeout(1500);
        untrustedProbe.setAcceptTimeout(1500);

        untrustedProbe.configureTransferCapability();
        try {
            untrustedProbe.initTLS();
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to initialize TLS for the untrusted-client-cert probe", e);
        }
        untrustedProbe.start();

        boolean opened = false;
        try {
            untrustedProbe.open();
            opened = true;
            untrustedProbe.send();
            assertNotEquals("A client cert NOT in the server truststore must NOT be able to "
                    + "complete a C-STORE against a mutual-TLS Listener enforcing "
                    + "setTlsNeedClientAuth(true) — silent client-auth-not-enforced "
                    + "regression (T-18.4-02, closes WR-01)",
                    untrustedProbe.getNumberOfFilesToSend(), untrustedProbe.getNumberOfFilesSent());
        } catch (Exception expected) {
            // expected: the server must reject an untrusted client certificate — most
            // likely the TLS handshake itself fails during open() (setTlsNeedClientAuth(true)
            // requires a server-trusted client cert during the handshake); if the handshake
            // were ever silently permitted, the assertion above still fails red instead.
        } finally {
            // Only release an association that actually opened (mirrors driveInboundCStore's
            // opened guard).
            if (opened) {
                untrustedProbe.close();
            }
            untrustedProbe.stop();
        }
    }
}
