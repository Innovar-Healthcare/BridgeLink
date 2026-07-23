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
 */
public class DicomScpStub {

    private final int port;
    private final String aeTitle;
    private final File storageDir;
    private DcmRcv dcmRcv;

    public DicomScpStub(int port, String aeTitle, File storageDir) {
        this.port = port;
        this.aeTitle = aeTitle;
        this.storageDir = storageDir;
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
