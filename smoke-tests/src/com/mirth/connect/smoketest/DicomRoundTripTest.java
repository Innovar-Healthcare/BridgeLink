package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.net.NoPresentationContextException;
import org.dcm4che2.tool.dcmsnd.DcmSnd;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.smoketest.stubs.DicomScpStub;

/**
 * NET-08 dedicated JUnit suite closing out the DICOM connector's round-trip (Listener +
 * Sender) and non-default DIMSE/PDU parameter surface. Drives a real inbound C-STORE into
 * the deployed DICOM Listener using the PLAIN vendor {@code org.dcm4che2.tool.dcmsnd.DcmSnd}
 * (never the server-side subclass that wraps a live DICOM configuration — that subclass
 * requires a live-server Guice-injected configuration context that does not exist in this
 * standalone driver JVM; mirrors {@link DicomScpStub}'s own plain-vendor-{@code DcmRcv}
 * precedent), then verifies
 * the deployed channel's DICOM Sender forwards the (transformed) object to a SECOND,
 * dedicated {@link DicomScpStub} instance (own port/dir — RESEARCH Pitfall 7: sharing the
 * existing {@code StubChannelsTest}/{@code dicom-test.xml} stub's storage directory would
 * race that test's order-dependent {@code received[0]} assertion) with storage-commitment
 * same-association reply enabled.
 *
 * <p>Rides the same single server boot as {@link StubChannelsTest}/{@link HttpParamsTest} (no
 * extra boot cost). Own {@code @BeforeClass} reads two new {@code -D} properties locally via
 * {@link #requireDicomProperty(String)} — never added to {@link SmokeTestBase#baseSetUp()} —
 * so {@code StubChannelsTest}/{@code NativePumpChannelsTest} keep running unmodified without
 * them (mirrors {@code HttpParamsTest}'s own-property convention).
 *
 * <p>This supersedes {@link StubChannelsTest#dicom()}'s weak
 * {@code received[0].length() > 0} check WITHOUT touching that existing test or channel
 * (D-02) — this class lives entirely on its own, dedicated channel
 * ({@code dicom-roundtrip-test.xml}, id {@value #ROUNDTRIP_CHANNEL_ID}).
 *
 * <p><b>Scope lineage:</b> this is the regression baseline guarding the dcm4che DIMSE
 * message-encoding path against future dcm4che jar bumps. It is NOT the yardstick for the
 * BouncyCastle CVE bump (TLS — Phase 18.4/NET-09) nor the xstream bump (channel-import
 * shape, caught at deploy by the existing {@code --deploy-only} mechanism).
 */
public class DicomRoundTripTest extends SmokeTestBase {

    private static final String ROUNDTRIP_CHANNEL_ID = "00000024-0000-0000-0000-000000000024";

    /**
     * Matches the round-trip channel's blank Listener {@code applicationEntity} (accepts any
     * called AET, {@code DICOMReceiver.java:92-94}) — kept as an explicit constant so the
     * SCU's calling identity is documented rather than incidental.
     */
    private static final String LISTENER_CALLED_AET = "SMOKEHARNESS";

    /**
     * D-04(3), principled constant — NOT self-fulfilling / auto-pinned. This is DERIVED
     * deliberately, not observed: the Listener's {@code nativeData=true} restricts inbound
     * negotiation to Implicit VR LE (downgrading the Explicit-VR-LE source fixture), and the
     * round-trip is expected to carry Implicit VR LE end-to-end (18.3-01-SUMMARY.md). If a
     * future run observes a different transfer syntax here, that is a FINDING to investigate
     * (Listener decode restriction or a Sender-leg re-encode change) — never auto-pin this
     * constant to the observed value without a written rationale.
     */
    private static final String EXPECTED_TS_UID = "1.2.840.10008.1.2";

    private static final File SOURCE_FIXTURE = new File("fixtures", "smoke-test.dcm");

    /**
     * Phase 22 / CVE-09 / D-13 hard gate: genuinely-compressed DICOM fixtures (produced via
     * GDCM 3.2.6 {@code gdcmconv} from a real 64x64 8-bit MONOCHROME2 pixel array — see
     * 22-04-SUMMARY.md D-12 spike). Each carries its own compressed Transfer Syntax UID
     * (verified via dcm4che-core parse, not assumed from the DICOM standard's registry).
     */
    private static final File JPEG2000_FIXTURE = new File("fixtures", "dicom-jpeg2000.dcm");
    private static final File JPEG_LOSSLESS_FIXTURE = new File("fixtures", "dicom-jpeg-lossless.dcm");

    /**
     * D-13, principled constant — NOT self-fulfilling / auto-pinned (mirrors
     * {@link #EXPECTED_TS_UID}'s discipline). JPEG 2000 Image Compression (Lossless Only).
     * A mismatch here is a FINDING: it means the compressed leg's Listener negotiated (or the
     * relay re-encoded) a DIFFERENT transfer syntax than the fixture's own, which would mean
     * the DIMSE connector silently transcoded/downgraded a compressed object rather than
     * relaying it byte-for-byte.
     */
    private static final String EXPECTED_JPEG2000_TS_UID = "1.2.840.10008.1.2.4.90";

    /**
     * D-13, principled constant — NOT self-fulfilling / auto-pinned. JPEG Lossless,
     * Non-Hierarchical, First-Order Prediction (Process 14 [Selection Value 1]). A mismatch
     * here is a FINDING (see {@link #EXPECTED_JPEG2000_TS_UID} javadoc) — this is the exact
     * transfer syntax whose codec-coverage regression risk (Pitfall 2) this gate exists to
     * catch, even though the D-12 spike proved the actual pixel-decode SPI is unreachable via
     * any production code path (DICOMMessageUtil/DICOMUtil/DICOMViewer's shared
     * {@code ij.plugin.DICOM} decoder rejects ANY compressed transfer syntax outright, both
     * before and after the CVE-09 jar swap — see 22-04-SUMMARY.md). What THIS gate proves
     * instead: the DIMSE connector's negotiation + relay of a compressed object is unaffected
     * by the CVE-09 jar swap, since dcm4che (not the swapped-out codec jar) owns
     * transport-level negotiation and DICOMSerializer treats PixelData as opaque.
     */
    private static final String EXPECTED_JPEG_LOSSLESS_TS_UID = "1.2.840.10008.1.2.4.70";

    /** The source fixture's original (untransformed) PatientName — proves mutation, not mere presence. */
    private static final String SOURCE_PATIENT_NAME_UNTRANSFORMED = "McDoogal^Hattie";

    /** The value the round-trip channel's tag-tree-mutating transform writes (dicom-roundtrip-test.xml). */
    private static final String EXPECTED_MUTATED_PATIENT_NAME = "RoundTripPatient";

    /** D-04 STRENGTHENED: every non-mutated dataset tag asserted for full source-vs-received preservation. */
    private static final int[] PRESERVED_TAGS = {
            Tag.SOPClassUID, Tag.SOPInstanceUID, Tag.Modality, Tag.PatientID,
            Tag.StudyInstanceUID, Tag.SeriesInstanceUID, Tag.SamplesPerPixel, Tag.BitsAllocated
    };

    private static String dicomListenerPort;
    private static String dicomRoundtripScpPort;
    private static DicomScpStub roundtripScpStub;
    private static DicomObject sourceObj;

    /**
     * D-13 hard gate (CVE-09): own Listener/SCP port pair (Pitfall 7), never shared with the
     * plaintext round-trip ports above. A single {@link DicomScpStub} instance serves BOTH
     * {@link #jpeg2000RoundTrip()} and {@link #jpegLosslessRoundTrip()} — safe to share
     * because dcm4che2's {@code DcmRcv} names each received file after its
     * {@code SOPInstanceUID}, and the two fixtures carry distinct, hand-assigned
     * SOPInstanceUIDs, so each test's poll for its own uniquely-named file cannot observe the
     * other test's object regardless of JUnit method-execution order.
     */
    private static String dicomCompressedListenerPort;
    private static String dicomCompressedScpPort;
    private static DicomScpStub compressedScpStub;
    private static DicomObject jpeg2000SourceObj;
    private static DicomObject jpegLosslessSourceObj;

    @BeforeClass
    public static void dicomRoundTripSetUp() throws Exception {
        dicomListenerPort = requireDicomProperty("DICOM_LISTENER_PORT");
        dicomRoundtripScpPort = requireDicomProperty("DICOM_ROUNDTRIP_SCP_PORT");
        dicomCompressedListenerPort = requireDicomProperty("DICOM_COMPRESSED_LISTENER_PORT");
        dicomCompressedScpPort = requireDicomProperty("DICOM_COMPRESSED_SCP_PORT");

        // Dedicated second DicomScpStub instance — own port/dir (Pitfall 7). Storage-
        // commitment same-association reply MUST be enabled BEFORE start() (HIGH #2): the
        // deployed channel's Sender-side SCU binds no local listening port and its
        // wait-for-storage-commitment-result call has no timeout, so the stub's default
        // reply (a NEW association back to port 104, where nothing listens) would hang the
        // Sender forever and the message would never reach SENT.
        File roundtripStorageDir = Files.createTempDirectory("smoke-dicom-roundtrip-received").toFile();
        roundtripScpStub = new DicomScpStub(Integer.parseInt(dicomRoundtripScpPort), "SMOKEHARNESS2", roundtripStorageDir);
        roundtripScpStub.setStgCmtReuseFrom(true);
        roundtripScpStub.start();
        registerStubStop(roundtripScpStub::stop);

        // D-13 hard gate: third DicomScpStub, own port/dir. stgcmt is NOT needed here —
        // dicom-compressed-roundtrip-test.xml's Sender leaves <stgcmt>false</stgcmt>
        // (unlike the plaintext round-trip channel), so setStgCmtReuseFrom is unnecessary.
        // DicomScpStub.start() always calls DcmRcv#initTransferCapability() with NO
        // setTransferSyntax restriction, so this stub accepts compressed transfer syntaxes
        // by default (live-verified in the D-12 spike — see 22-04-SUMMARY.md).
        File compressedStorageDir = Files.createTempDirectory("smoke-dicom-compressed-received").toFile();
        compressedScpStub = new DicomScpStub(Integer.parseInt(dicomCompressedScpPort), "SMOKEHARNESS3", compressedStorageDir);
        compressedScpStub.start();
        registerStubStop(compressedScpStub::stop);

        sourceObj = parseDicomObject(SOURCE_FIXTURE);
        jpeg2000SourceObj = parseDicomObject(JPEG2000_FIXTURE);
        jpegLosslessSourceObj = parseDicomObject(JPEG_LOSSLESS_FIXTURE);
    }

    /**
     * Local replica of {@code SmokeTestBase}'s private {@code requireProperty}/
     * {@code HttpParamsTest}'s {@code requireHttpParamsProperty} — deliberately NOT added to
     * {@link SmokeTestBase#baseSetUp()}'s required-property list, so
     * {@code StubChannelsTest}/{@code NativePumpChannelsTest} keep running unmodified without
     * these two new {@code -D} properties set.
     */
    private static String requireDicomProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required system property '" + name + "' not set. "
                    + "DicomRoundTripTest only runs against a live harness — invoke via "
                    + "smoke-tests/run-smoke-test.sh, which passes harness ports/paths as -D "
                    + "properties to `ant -f smoke-tests/build.xml test-run`.");
        }
        return value;
    }

    @Test
    public void roundTrip() throws Exception {
        driveInboundCStore(Integer.parseInt(dicomListenerPort), SOURCE_FIXTURE);

        assertThreeLevels(ROUNDTRIP_CHANNEL_ID, 1, () -> {
            // HIGH #2 watchdog: DICOMDispatcher downgrades SENT->QUEUED whenever storage
            // commitment fails, so assertThreeLevels' 30s SENT>=1 poll above is itself proof
            // the stgcmt N-EVENT-REPORT round-trip completed. The predicate below additionally
            // requires a successful parse (not a bare file-presence check) to avoid reading a
            // half-written file — the file lands at the stub BEFORE the N-ACTION, so presence
            // alone can be green while the Sender is still wedged waiting on stgcmt.
            pollUntil("round-trip DicomScpStub received a fully-parseable file with a non-null PatientName", 30, () -> {
                File[] files = roundtripScpStub.listReceivedFiles();
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

            File received = roundtripScpStub.listReceivedFiles()[0];
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

                assertEquals("Transfer syntax should be the principled constant (Implicit VR LE) — "
                        + "see EXPECTED_TS_UID javadoc if this ever fails",
                        EXPECTED_TS_UID, dis.getTransferSyntax().uid());
            }
        });
    }

    /**
     * D-13 hard gate (CVE-09): pushes the genuinely-compressed JPEG2000 fixture through the
     * compressed-leg channel's Listener (dicom-compressed-roundtrip-test.xml, channel
     * 00000032) and asserts it is relayed to the Sender's dedicated stub with its transfer
     * syntax, SOP-class UID, and non-mutated tags preserved end to end. This does NOT (and
     * per the D-12 spike, cannot) exercise the swapped codec jar's ImageReaderSpi — see
     * {@link #EXPECTED_JPEG_LOSSLESS_TS_UID} javadoc for why. It proves the DIMSE connector's
     * negotiation/relay path is unaffected by the CVE-09 jar swap.
     */
    @Test
    public void jpeg2000RoundTrip() throws Exception {
        assertCompressedRoundTrip(JPEG2000_FIXTURE, jpeg2000SourceObj, EXPECTED_JPEG2000_TS_UID);
    }

    /**
     * D-13 hard gate (CVE-09): same as {@link #jpeg2000RoundTrip()} but for the JPEG-lossless
     * fixture — the exact transfer syntax the fork (jai-imageio-core + jai-imageio-jpeg2000)
     * dropped native decode support for (RESEARCH's flagged gap). Per the D-12 spike, that gap
     * is unreachable/moot for image DECODE (ij.plugin.DICOM already rejected it with the OLD
     * jar), but this leg still proves the swap did not regress the DIMSE connector's transport-
     * level negotiation and relay of a JPEG-lossless-encoded object.
     */
    @Test
    public void jpegLosslessRoundTrip() throws Exception {
        assertCompressedRoundTrip(JPEG_LOSSLESS_FIXTURE, jpegLosslessSourceObj, EXPECTED_JPEG_LOSSLESS_TS_UID);
    }

    /**
     * Shared machinery for {@link #jpeg2000RoundTrip()}/{@link #jpegLosslessRoundTrip()} —
     * drives the fixture through {@link #compressedScpStub} (shared across both legs; see the
     * field javadoc for why sharing is race-free) and asserts transfer-syntax/SOP-class/tag
     * preservation. Polls for a file named after the fixture's OWN SOPInstanceUID — dcm4che2's
     * DcmRcv names received files by SOPInstanceUID, so each leg's poll cannot observe the
     * other leg's object regardless of JUnit method-execution order (no shared received[0]
     * indexing, unlike {@link #roundTrip()}'s single-fixture stub).
     */
    private void assertCompressedRoundTrip(File fixture, DicomObject sourceFixtureObj, String expectedTsUid) throws Exception {
        driveInboundCStore(Integer.parseInt(dicomCompressedListenerPort), fixture);

        String sopInstanceUid = sourceFixtureObj.getString(Tag.SOPInstanceUID);

        // dcm4che2's DcmRcv names each received file after its SOPInstanceUID (live-verified
        // in the D-12 spike) — find OUR fixture's file by name rather than assuming index 0,
        // since compressedScpStub's storage dir is shared across both compressed legs.
        pollUntil("compressed DicomScpStub received a fully-parseable file for SOPInstanceUID " + sopInstanceUid, 30, () -> {
            for (File candidateFile : compressedScpStub.listReceivedFiles()) {
                if (!sopInstanceUid.equals(candidateFile.getName())) {
                    continue;
                }
                try {
                    DicomObject candidate = parseDicomObject(candidateFile);
                    if (candidate.getString(Tag.SOPInstanceUID) != null) {
                        return true;
                    }
                } catch (Exception e) {
                    // half-written file — keep polling
                }
            }
            return false;
        });

        File expectedReceivedFile = null;
        for (File candidateFile : compressedScpStub.listReceivedFiles()) {
            if (sopInstanceUid.equals(candidateFile.getName())) {
                expectedReceivedFile = candidateFile;
                break;
            }
        }
        assertNotNull("Received file for SOPInstanceUID " + sopInstanceUid + " must exist after the poll above succeeded",
                expectedReceivedFile);

        try (DicomInputStream dis = new DicomInputStream(expectedReceivedFile)) {
            dis.setAllocateLimit(-1);
            DicomObject obj = new BasicDicomObject();
            dis.readDicomObject(obj, -1);

            for (int tag : PRESERVED_TAGS) {
                assertEquals("Non-mutated tag " + Integer.toHexString(tag) + " must be preserved end-to-end",
                        sourceFixtureObj.getString(tag), obj.getString(tag));
            }

            assertEquals("Transfer syntax must be preserved byte-for-byte through the DIMSE relay — "
                    + "a mismatch means the connector re-encoded/downgraded the compressed object "
                    + "(the exact Pitfall 2 codec-coverage regression this gate exists to catch)",
                    expectedTsUid, dis.getTransferSyntax().uid());
        }
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
     * Embeds the PLAIN vendor {@code DcmSnd} (not the server-side, configuration-bound
     * subclass — Pitfall 1) to drive a real inbound C-STORE against the deployed DICOM
     * Listener. HIGH #1 negotiation guard:
     * the SCU's own {@code addTransferCapability} always offers Implicit VR LE alongside the
     * source file's own transfer syntax, so negotiation against a Listener with
     * {@code nativeData=true} (Implicit VR LE only) is expected to succeed. This method both
     * (a) wraps open/send in a try/catch for {@code NoPresentationContextException} so a
     * regression in that expectation fails loudly with an explicit message rather than a
     * silent no-op, and (b) positively asserts the file was actually transferred — vendor
     * {@code DcmSnd.send()} swallows {@code NoPresentationContextException} internally per
     * file (logs to stderr, never rethrows), so the file-count assertion is the guard that
     * actually fires if negotiation silently fails.
     */
    private void driveInboundCStore(int listenerPort, File fixture) throws Exception {
        DcmSnd dcmSnd = new DcmSnd("SMOKEHARNESS-SCU");
        dcmSnd.setCalledAET(LISTENER_CALLED_AET);
        dcmSnd.setRemoteHost("127.0.0.1");
        dcmSnd.setRemotePort(listenerPort);
        dcmSnd.addFile(fixture);
        // med priority (0) — DICOMDispatcher.java:171-176's own med=0/low=1/high=2 mapping
        // (inverted vs the DICOM standard's MEDIUM=0/HIGH=1/LOW=2 — harmless here, priority
        // is not asserted by this test).
        dcmSnd.setPriority(0);
        dcmSnd.configureTransferCapability();
        dcmSnd.start();
        boolean opened = false;
        try {
            dcmSnd.open();
            opened = true;
            dcmSnd.send();
        } catch (NoPresentationContextException e) {
            throw new AssertionError("inbound C-STORE negotiation failed — Listener offers only "
                    + "Implicit VR LE (nativeData=true); confirm the SCU offers it", e);
        } finally {
            // Only release an association that was actually opened (assoc is null otherwise;
            // DcmSnd.close() would NPE and mask the real open() failure).
            if (opened) {
                dcmSnd.close();
            }
            dcmSnd.stop();
        }

        assertEquals("inbound C-STORE negotiation failed — Listener offers only Implicit VR LE "
                + "(nativeData=true); confirm the SCU offers it (DcmSnd.send() swallows "
                + "NoPresentationContextException internally per-file, so 0 files sent here means "
                + "no presentation context was negotiated)",
                dcmSnd.getNumberOfFilesToSend(), dcmSnd.getNumberOfFilesSent());
    }
}
