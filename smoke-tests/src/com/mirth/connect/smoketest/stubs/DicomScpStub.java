package com.mirth.connect.smoketest.stubs;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;

import org.dcm4che2.tool.dcmrcv.DcmRcv;

/**
 * D-07 DICOM SCP endpoint stub, embedding {@code org.dcm4che2.tool.dcmrcv.DcmRcv}
 * programmatically (18-RESEARCH.md Assumption A1). Embeddability confirmed by decompiling
 * the shipped {@code dcm4che-tool-dcmrcv-2.0.29.jar}: {@code DcmRcv.main()} follows exactly
 * the sequence used here — constructor, {@code setAEtitle}/{@code setPort}/
 * {@code setDestination}, {@code initTransferCapability()}, then {@code start()} — so no
 * child-process fallback was needed.
 *
 * <p><b>A1 outcome:</b> embedded (no child process).
 *
 * <p>StubSelfTest verifies clean start/stop and that the port accepts a TCP connection
 * (association-level handshake correctness). A full dcmsnd DICOM object round-trip is
 * intentionally NOT exercised here — the behavior block's fallback allowance covers this:
 * the harness's DICOM channel in 18-07 performs the real end-to-end send/receive proof
 * against a deployed server; duplicating that here would add no confidence.
 *
 * <p><b>18.3-02 additive change (opt-in storage-commitment reuse, HIGH #2 / RESEARCH
 * Pitfall 3):</b> callers that need this stub to reply to a Storage Commitment N-ACTION on
 * the SAME association (rather than opening a NEW association back to the SCU, which the
 * Mirth Sender's {@code MirthDcmSnd} cannot accept — it binds no local listening port and
 * its {@code waitForStgCmtResult()} has no timeout) should call
 * {@link #setStgCmtReuseFrom(boolean)} with {@code true} BEFORE {@link #start()}. Default is
 * {@code false}, preserving this class's existing behavior exactly for every current caller
 * (e.g. {@code StubChannelsTest.dicom()}'s stub, whose channel has {@code stgcmt=false} and
 * never exercises this return path).
 *
 * <p><b>18.4-02 additive change (opt-in TLS, Phase 18.4/NET-09, RESEARCH Pattern 3):</b>
 * callers that need this stub's embedded {@code DcmRcv} to require TLS (mutual client
 * auth — the corrected {@code setTlsNeedClientAuth(true)} semantics, D-02) should call
 * {@link #setTls(String, String, String, String, String)} with {@code "aes"} or
 * {@code "3des"} BEFORE {@link #start()}. Default {@code tlsMode} is {@code "notls"},
 * preserving this class's existing plaintext behavior exactly (byte-for-byte-equivalent
 * {@code start()} body) for every current caller that never calls {@code setTls}.
 *
 * <p><b>Rule 1 fix (found live during this plan's own TDD GREEN run):</b> dcm4che2's
 * {@code NetworkConnection} defaults {@code tlsProtocol} to {@code {"TLSv1", "SSLv3",
 * "SSLv2Hello"}} (verified via bytecode disassembly of the vendored
 * {@code dcm4che-net-2.0.29.jar}'s static initializer, field {@code TLS_AND_SSLv2}) — every
 * one of those three protocols is itself disabled by the JDK's own default
 * {@code jdk.tls.disabledAlgorithms} policy (independent of the separate 3DES-cipher
 * question D-04 addresses), so a TLS handshake fails with
 * {@code SSLHandshakeException: No appropriate protocol} unless a modern protocol list is
 * set explicitly. {@link #start()} therefore calls
 * {@code rcv.setTlsProtocol(new String[] {"TLSv1.3", "TLSv1.2"})}, mirroring
 * {@code DICOMConfigurationUtil.configureDcmRcv()}'s own
 * {@code dcmrcv.setTlsProtocol(MirthSSLUtil.getEnabledHttpsProtocols(protocols))} call
 * (server default protocols, {@code nossl2}-equivalent — no {@code SSLv2Hello}).
 */
public class DicomScpStub {

    private final int port;
    private final String aeTitle;
    private final File storageDir;
    private DcmRcv dcmRcv;
    private boolean stgCmtReuseFrom = false;
    private String tlsMode = "notls";
    private String keyStoreUrl;
    private String keyStorePassword;
    private String trustStoreUrl;
    private String trustStorePassword;

    public DicomScpStub(int port, String aeTitle, File storageDir) {
        this.port = port;
        this.aeTitle = aeTitle;
        this.storageDir = storageDir;
    }

    /**
     * Opt-in: when {@code true}, the embedded {@code DcmRcv}'s Storage Commitment
     * N-EVENT-REPORT reply reuses the SCU's still-open association instead of opening a new
     * one back to the caller (18.3-02, HIGH #2). Must be called before {@link #start()}.
     * Default {@code false} — additive, backward-compatible with every existing caller.
     */
    public void setStgCmtReuseFrom(boolean stgCmtReuseFrom) {
        this.stgCmtReuseFrom = stgCmtReuseFrom;
    }

    /**
     * Opt-in TLS (18.4-02, RESEARCH Pattern 3): {@code mode} is {@code "aes"} or
     * {@code "3des"}. The same file is passed as both {@code keyStoreUrl} and
     * {@code trustStoreUrl} (shared self-signed keystore doubles as truststore, D-02) — a
     * raw absolute {@code .p12} path works directly, dcm4che's {@code toKeyStoreType()}
     * selects PKCS12 purely by file extension. Never pass a distinct key password (Pitfall
     * 5) — PKCS12 requires {@code keypass == storepass}. Must be called before
     * {@link #start()}.
     */
    public void setTls(String mode, String keyStoreUrl, String keyStorePassword,
            String trustStoreUrl, String trustStorePassword) {
        this.tlsMode = mode;
        this.keyStoreUrl = keyStoreUrl;
        this.keyStorePassword = keyStorePassword;
        this.trustStoreUrl = trustStoreUrl;
        this.trustStorePassword = trustStorePassword;
    }

    public void start() throws IOException {
        storageDir.mkdirs();
        DcmRcv rcv = new DcmRcv(aeTitle);
        rcv.setAEtitle(aeTitle);
        // T-18-11: bind explicitly to 127.0.0.1, never all interfaces.
        rcv.setHostname("127.0.0.1");
        rcv.setPort(port);
        rcv.setDestination(storageDir.getAbsolutePath());
        rcv.initTransferCapability();
        if (!"notls".equals(tlsMode)) {
            if ("aes".equals(tlsMode)) {
                rcv.setTlsAES_128_CBC();
            } else if ("3des".equals(tlsMode)) {
                rcv.setTls3DES_EDE_CBC();
            }
            rcv.setKeyStoreURL(keyStoreUrl);
            rcv.setKeyStorePassword(keyStorePassword);
            rcv.setTrustStoreURL(trustStoreUrl);
            rcv.setTrustStorePassword(trustStorePassword);
            // Mutual auth ON — corrected noClientAuth semantics (D-02/RESEARCH Pitfall 1).
            rcv.setTlsNeedClientAuth(true);
            // Rule 1 fix: dcm4che2's own TLS_AND_SSLv2 default ({"TLSv1","SSLv3",
            // "SSLv2Hello"}) is entirely disabled by the JDK's default jdk.tls.disabledAlgorithms
            // policy — mirror DICOMConfigurationUtil's server-side protocol list.
            rcv.setTlsProtocol(new String[] { "TLSv1.3", "TLSv1.2" });
            // MUST precede start() — createTLSServerSocket() happens inside start(), and
            // requires initTLS() to have already populated the KeyManagerFactory/
            // TrustManagerFactory state (RESEARCH Pitfall 6).
            try {
                rcv.initTLS();
            } catch (GeneralSecurityException e) {
                throw new IOException("Failed to initialize TLS for DicomScpStub", e);
            }
        }
        rcv.setStgCmtReuseFrom(stgCmtReuseFrom);
        rcv.start();
        this.dcmRcv = rcv;
    }

    public void stop() {
        if (dcmRcv != null) {
            dcmRcv.stop();
            dcmRcv = null;
        }
    }

    /** True if a TCP connection can be established to 127.0.0.1:port (liveness check, no DIMSE association). */
    public boolean isListening() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public File[] listReceivedFiles() {
        File[] files = storageDir.listFiles();
        return files == null ? new File[0] : files;
    }
}
