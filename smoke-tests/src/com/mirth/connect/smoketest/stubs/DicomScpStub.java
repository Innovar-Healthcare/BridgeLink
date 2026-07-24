package com.mirth.connect.smoketest.stubs;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

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
 */
public class DicomScpStub {

    private final int port;
    private final String aeTitle;
    private final File storageDir;
    private DcmRcv dcmRcv;
    private boolean stgCmtReuseFrom = false;

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

    public void start() throws IOException {
        storageDir.mkdirs();
        DcmRcv rcv = new DcmRcv(aeTitle);
        rcv.setAEtitle(aeTitle);
        // T-18-11: bind explicitly to 127.0.0.1, never all interfaces.
        rcv.setHostname("127.0.0.1");
        rcv.setPort(port);
        rcv.setDestination(storageDir.getAbsolutePath());
        rcv.initTransferCapability();
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
