import org.dcm4che2.data.UID;
import org.dcm4che2.net.Association;
import org.dcm4che2.tool.dcmrcv.DcmRcv;
import org.dcm4che2.tool.dcmsnd.DcmSnd;

import javax.crypto.Cipher;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;

/**
 * Phase 18.4 Plan 01 — standalone (non-harness) DICOM TLS diagnostic.
 *
 * Stands up TWO real, ephemeral, loopback DICOM TLS handshakes (one {@code aes}, one
 * {@code 3des}) using the embedded dcm4che {@link DcmRcv}/{@link DcmSnd} tool classes,
 * mirroring the exact setter -> initTLS() -> start()/open() call order verified against
 * {@code DICOMConfigurationUtil} (RESEARCH Patterns 2/3, Pitfall 6).
 *
 * Answers two RESEARCH open questions empirically, BEFORE any Plan 03/04 harness-wiring
 * investment:
 *
 * <ol>
 *   <li>Task 1 (Open Question 1 / Assumption A4): which JCA/JSSE provider serves the
 *       handshake's {@code Cipher}/{@code Signature} primitives — does BouncyCastle ever
 *       participate, or does the JDK's own {@code SunJSSE}/{@code SunJCE}/{@code SunRsaSign}
 *       serve it entirely?</li>
 *   <li>Task 2 (Open Question 2 / Assumption A2 / Pitfall 4): do the legacy RSA-CBC DICOM
 *       cipher suites (aes, 3des) negotiate DOWN to TLSv1.2 without a
 *       {@code handshake_failure}, given the connector's default protocol list includes
 *       TLSv1.3?</li>
 * </ol>
 *
 * Deliberately does NOT restrict the protocol list to TLSv1.2-only — the whole point of
 * Task 2 is to observe the DEFAULT negotiation behavior. IMPORTANT (discovered empirically
 * running this diagnostic, not anticipated by RESEARCH): dcm4che2's own {@code
 * NetworkConnection} class has a hardcoded hard-coded 2010-era default {@code tlsProtocol}
 * field of {@code {"TLSv1", "SSLv3", "SSLv2Hello"}} — completely independent of, and NOT
 * inherited from, the JVM's own TLS defaults. On any JDK where those legacy protocol names
 * are disabled (every JDK 17+ target in this milestone), leaving that default untouched
 * fails EVERY leg immediately with "No appropriate protocol", regardless of cipher choice —
 * this is NOT the TLSv1.3-preferring-list-vs-legacy-cipher question RESEARCH Pitfall 4
 * anticipated. The production connector (verified: {@code DICOMConfigurationUtil}) NEVER
 * relies on this default — it unconditionally calls {@code setTlsProtocol(...)} with
 * {@code MirthSSLUtil.DEFAULT_HTTPS_CLIENT_PROTOCOLS} ({@code {"TLSv1.3", "TLSv1.2"}}) run
 * through {@code getEnabledHttpsProtocols()}. To ask the question RESEARCH actually intended
 * (does a modern, TLSv1.3-inclusive protocol list still let the legacy RSA-CBC ciphers
 * negotiate down to TLSv1.2?), this diagnostic mirrors that same override rather than
 * leaving dcm4che's own ancient default in place — see {@link #DEFAULT_PROTOCOLS} below.
 *
 * This class lives OUTSIDE {@code smoke-tests/src/} on purpose (see the runner script's
 * header comment) so {@code build.xml}'s compile target never picks it up.
 */
public class DicomTlsProviderDiagnostic {

    /**
     * Mirrors {@code com.mirth.connect.util.MirthSSLUtil.DEFAULT_HTTPS_CLIENT_PROTOCOLS} —
     * the protocol list the production connector actually installs via {@code
     * DICOMConfigurationUtil.configureDcmSnd/configureDcmRcv} before every real TLS DICOM
     * handshake. NOT dcm4che2's own hardcoded {@code NetworkConnection} default (see the
     * class-level Javadoc above).
     */
    private static final String[] DEFAULT_PROTOCOLS = { "TLSv1.3", "TLSv1.2" };

    private static final class LegResult {
        String cipherSuite;
        String protocol;
        Exception failure;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: DicomTlsProviderDiagnostic <keystore.p12> <storepass>");
            System.exit(2);
        }
        String keystorePath = args[0];
        String keystorePw = args[1];

        System.out.println("PROVIDERS:");
        for (Provider p : Security.getProviders()) {
            System.out.println("  " + p.getName() + " v" + p.getVersionStr());
        }

        // Proxy for which provider serves the default JSSE/JCA crypto path — the same
        // primitives (AES-CBC cipher, RSA signature) a TLS_RSA_WITH_AES_128_CBC_SHA
        // handshake uses internally.
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        System.out.println("CIPHER_PROVIDER: " + cipher.getProvider().getName());
        Signature signature = Signature.getInstance("SHA256withRSA");
        System.out.println("SIGNATURE_PROVIDER: " + signature.getProvider().getName());

        LegResult aes = runLeg("aes", keystorePath, keystorePw);
        System.out.println("AES_LEG: " + describe(aes));

        LegResult des = runLeg("3des", keystorePath, keystorePw);
        System.out.println("3DES_LEG: " + describe(des));

        if (aes.failure != null || des.failure != null) {
            // A handshake_failure / no-cipher-suites-in-common here IS the Task 2 finding —
            // surface it with a non-zero exit rather than swallowing it.
            System.exit(1);
        }
    }

    private static String describe(LegResult r) {
        if (r.failure != null) {
            return "FAILED (" + r.failure.getClass().getSimpleName() + ": " + r.failure.getMessage() + ")";
        }
        return "protocol=" + r.protocol + " cipherSuite=" + r.cipherSuite;
    }

    /**
     * Stands up one loopback DICOM TLS leg: an embedded {@code DcmRcv} SCP and an embedded
     * {@code DcmSnd} SCU, both configured identically (same cipher mode, same shared
     * keystore-as-truststore, mutual client auth ON). After {@code open()} succeeds,
     * reflects the SCU's private {@code assoc} field to reach the live
     * {@code Association.getSocket()} and read the negotiated {@link SSLSession}.
     */
    private static LegResult runLeg(String cipherMode, String keystorePath, String keystorePw) throws Exception {
        LegResult result = new LegResult();
        int port = findFreePort();

        DcmRcv scp = new DcmRcv("DIAGSCP" + cipherMode.toUpperCase());
        DcmSnd scu = new DcmSnd("DIAGSCU" + cipherMode.toUpperCase());
        try {
            // --- SCP side (mirrors DicomScpStub + Pattern 3: setters -> initTLS() -> start()) ---
            scp.setAEtitle("DIAGSCP");
            scp.setHostname("127.0.0.1");
            scp.setPort(port);
            scp.initTransferCapability();
            configureTls(scp, cipherMode, keystorePath, keystorePw);
            scp.initTLS();
            scp.start();

            // --- SCU side (mirrors Pattern 2: setters -> configureTransferCapability() ->
            //     initTLS() -> start() -> open()) ---
            scu.setCalledAET("DIAGSCP");
            scu.setRemoteHost("127.0.0.1");
            scu.setRemotePort(port);
            // Verification SOP class / ImplicitVRLittleEndian is always in the SCP's default
            // transfer capability (DcmRcv.initTransferCapability()'s tc[0]) — propose the same
            // so a presentation context negotiates successfully without needing a real .dcm file.
            scu.addTransferCapability(UID.VerificationSOPClass, UID.ImplicitVRLittleEndian);
            scu.configureTransferCapability();
            configureTls(scu, cipherMode, keystorePath, keystorePw);
            scu.initTLS();
            scu.start();
            scu.open();

            Field assocField = DcmSnd.class.getDeclaredField("assoc");
            assocField.setAccessible(true);
            Association assoc = (Association) assocField.get(scu);
            Socket socket = assoc.getSocket();
            if (!(socket instanceof SSLSocket)) {
                throw new AssertionError("Expected an SSLSocket on this association — TLS "
                        + "was not engaged (actual class: " + socket.getClass().getName() + ")");
            }
            SSLSession session = ((SSLSocket) socket).getSession();
            result.cipherSuite = session.getCipherSuite();
            result.protocol = session.getProtocol();

            scu.close();
        } catch (Exception e) {
            result.failure = e;
        } finally {
            scu.stop();
            scp.stop();
        }
        return result;
    }

    private static void configureTls(DcmRcv rcv, String mode, String ksPath, String ksPw) {
        if ("aes".equals(mode)) {
            rcv.setTlsAES_128_CBC();
        } else if ("3des".equals(mode)) {
            rcv.setTls3DES_EDE_CBC();
        }
        rcv.setKeyStoreURL(ksPath);
        rcv.setKeyStorePassword(ksPw);
        rcv.setTrustStoreURL(ksPath);
        rcv.setTrustStorePassword(ksPw);
        // Mutual auth ON (RESEARCH D-02/noClientAuth correction: setTlsNeedClientAuth(true),
        // NOT the misnamed connector field's literal "false" wording).
        rcv.setTlsNeedClientAuth(true);
        // Override dcm4che2's own ancient default protocol list — see class Javadoc.
        rcv.setTlsProtocol(DEFAULT_PROTOCOLS);
    }

    private static void configureTls(DcmSnd snd, String mode, String ksPath, String ksPw) {
        if ("aes".equals(mode)) {
            snd.setTlsAES_128_CBC();
        } else if ("3des".equals(mode)) {
            snd.setTls3DES_EDE_CBC();
        }
        snd.setKeyStoreURL(ksPath);
        snd.setKeyStorePassword(ksPw);
        snd.setTrustStoreURL(ksPath);
        snd.setTrustStorePassword(ksPw);
        snd.setTlsNeedClientAuth(true);
        // Override dcm4che2's own ancient default protocol list — see class Javadoc.
        snd.setTlsProtocol(DEFAULT_PROTOCOLS);
    }

    private static int findFreePort() throws java.io.IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
