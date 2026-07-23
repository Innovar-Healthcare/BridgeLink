package com.mirth.connect.smoketest.stubs;

import java.io.File;
import java.io.IOException;

/**
 * D-07 DICOM SCP stub skeleton (RED phase — see StubSelfTest Test 4). Real implementation
 * (embedding org.dcm4che2.tool.dcmrcv.DcmRcv, 18-RESEARCH.md Assumption A1) lands in the
 * GREEN commit.
 */
public class DicomScpStub {

    public DicomScpStub(int port, String aeTitle, File storageDir) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void start() throws IOException {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void stop() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public boolean isListening() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public File[] listReceivedFiles() {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
