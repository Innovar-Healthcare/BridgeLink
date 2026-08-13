package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import javax.swing.text.Document;
import javax.swing.text.rtf.RTFEditorKit;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.smoketest.stubs.DicomScpStub;
import com.mirth.connect.smoketest.stubs.SmtpStub;
import com.mirth.connect.smoketest.stubs.SoapStub;

/**
 * D-08 three-level assertions for the five stub-destination channels — SMTP, SOAP, JDBC, DICOM,
 * Document Writer (plan 18-07 Task 2). Each stub must be RUNNING (bound to the exact
 * ports/paths the deployed fixtures reference) before its channel processes a message; stubs are
 * started in {@link #startStubs()} and stopped in the base class's {@code @AfterClass} registry
 * (T-18-13) via {@link SmokeTestBase#registerStubStop}.
 *
 * <p><b>Phase 22 fidelity baseline (D-08, Phase 20 D-05):</b> the {@link #docWriter()}
 * assertions below (PDFBox {@code PDFTextStripper} content extraction + JDK
 * {@code RTFEditorKit} content extraction) are Phase 22's OpenPDF/OpenRTF before/after fidelity
 * baseline — they run against itext 2.1.7 + itext-rtf 2.1.7 output NOW, and MUST run UNCHANGED
 * against OpenPDF 2.0.5 / OpenRTF 1.2.1 output after that library swap. Do not modify these two
 * assertions when Phase 22 lands; if they fail against the new library output, that failure IS
 * the fidelity regression Phase 22's gate exists to catch.
 */
public class StubChannelsTest extends SmokeTestBase {

    private static final String JDBC_CHANNEL_ID = "00000004-0000-0000-0000-000000000004";
    private static final String SMTP_CHANNEL_ID = "00000007-0000-0000-0000-000000000007";
    private static final String SOAP_CHANNEL_ID = "00000008-0000-0000-0000-000000000008";
    private static final String DICOM_CHANNEL_ID = "00000009-0000-0000-0000-000000000009";
    private static final String DOC_WRITER_CHANNEL_ID = "00000010-0000-0000-0000-000000000010";

    private static SmtpStub smtpStub;
    private static SoapStub soapStub;
    private static DicomScpStub dicomScpStub;

    @BeforeClass
    public static void startStubs() throws Exception {
        smtpStub = new SmtpStub(Integer.parseInt(smtpPort));
        smtpStub.start();
        registerStubStop(smtpStub::stop);

        URI soapUri = URI.create(soapUrl);
        String soapPath = soapUri.getPath().startsWith("/") ? soapUri.getPath().substring(1) : soapUri.getPath();
        soapStub = new SoapStub(soapUri.getPort(), soapPath, false /* real JAX-WS mode — see SoapStub javadoc */);
        soapStub.start();
        registerStubStop(soapStub::stop);

        File dicomStorageDir = Files.createTempDirectory("smoke-dicom-received").toFile();
        dicomScpStub = new DicomScpStub(Integer.parseInt(scpPort), "SMOKEHARNESS", dicomStorageDir);
        dicomScpStub.start();
        registerStubStop(dicomScpStub::stop);

        pumpAll();
    }

    @AfterClass
    public static void tearDownDone() {
        // Stub stop is handled by SmokeTestBase's registered stoppers (LIFO, run in
        // baseTearDown()); nothing additional needed here.
    }

    private static void pumpAll() throws Exception {
        rest.processMessage(SMTP_CHANNEL_ID, Hl7Messages.ORU_R01_LF);
        rest.processMessage(SOAP_CHANNEL_ID, Hl7Messages.ORU_R01_LF);
        rest.processMessage(JDBC_CHANNEL_ID, Hl7Messages.ORU_R01_LF);
        rest.processMessageBytes(DICOM_CHANNEL_ID, Files.readAllBytes(Paths.get("fixtures", "smoke-test.dcm")));
        // doc-writer-test.xml has FOUR destinations (PDF at metaDataId 1, RTF at metaDataId 2,
        // Complex RTF at metaDataId 3, Encrypted PDF at metaDataId 4) — all four must be
        // targeted explicitly (Rule 1 fix, see RestClient.processMessage javadoc).
        rest.processMessage(DOC_WRITER_CHANNEL_ID, Hl7Messages.ORU_R01_LF, java.util.List.of(1, 2, 3, 4));
    }

    @Test
    public void smtp() throws Exception {
        assertThreeLevels(SMTP_CHANNEL_ID, 1, () -> {
            boolean arrived = smtpStub.waitForIncomingEmail(30_000, 1);
            assertTrue("SmtpStub should have received at least one email within 30s", arrived);
            String body = SmtpStub.getBody(smtpStub.getReceivedMessages()[0]);
            assertTrue("SMTP mail body should contain the transformed patient token",
                    body.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    @Test
    public void soap() throws Exception {
        assertThreeLevels(SOAP_CHANNEL_ID, 1, () -> {
            pollUntil("SoapStub last received payload", 30, () -> soapStub.getLastReceivedPayload() != null);
            String payload = soapStub.getLastReceivedPayload();
            assertNotNull("SoapStub should have recorded a received payload", payload);
            assertTrue("SOAP recorded envelope body should contain the transformed patient token",
                    payload.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    @Test
    public void jdbc() throws Exception {
        assertThreeLevels(JDBC_CHANNEL_ID, 1, () -> {
            // JDBC read follows the L1 SENT-count assertion above in code order (Pitfall 4 —
            // a fresh connection re-opens the SQLite file only after the Database Writer has
            // committed, never concurrently with it).
            String patientName = pollForJdbcPatientName(30);
            assertEquals(Hl7Messages.EXPECTED_PATIENT, patientName);
        });
    }

    private static String pollForJdbcPatientName(int timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            String name = readLatestJdbcPatientName();
            if (name != null) {
                return name;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("dest_records row did not appear within " + timeoutSeconds + "s");
    }

    private static String readLatestJdbcPatientName() {
        String jdbcUrl = "jdbc:sqlite:" + sqlitePath;
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT patient_name FROM dest_records ORDER BY id DESC LIMIT 1")) {
            if (rs.next()) {
                return rs.getString("patient_name");
            }
            return null;
        } catch (Exception e) {
            // Table may not exist yet on the very first poll iterations (deployScript race) —
            // treat as "not yet" rather than a hard failure.
            return null;
        }
    }

    @Test
    public void dicom() throws Exception {
        assertThreeLevels(DICOM_CHANNEL_ID, 1, () -> {
            pollUntil("DicomScpStub received file", 30,
                    () -> dicomScpStub.listReceivedFiles().length > 0);
            File[] received = dicomScpStub.listReceivedFiles();
            assertTrue("DicomScpStub should have stored at least one received DICOM object",
                    received.length > 0);
            assertTrue("Received DICOM object should be non-empty", received[0].length() > 0);
        });
    }

    // Phase 22.2 (SC-2/SC-3/D-05): tokens/password mirror the exact fixture choices authored
    // into doc-writer-test.xml's metaDataId 3/4 templates and DocRenderSeamTest's Wave-1
    // tracer (22.2-01), so the end-to-end smoke assertions below stay consistent with the
    // seam-level differential fixture they extend.
    private static final String VITALS_TABLE_TOKEN = "VITALS_TABLE_TOKEN_22P2";
    private static final String MEDS_TABLE_TOKEN = "MEDS_TABLE_TOKEN_22P2";
    private static final String ENCRYPTED_PDF_PASSWORD = "s3cret";

    @Test
    public void docWriter() throws Exception {
        // Phase 22.2/D-05: raised from 1 to 4 — all four enabled destinations (PDF, RTF,
        // Complex RTF, Encrypted PDF) must reach SENT status. getSentCount() aggregates the
        // "sent" statistic across every destination connector (DonkeyEngineController
        // #addConnectorToChannelStatistics sums per-destination SENT counts into the
        // channel-level total), so one pumped message fanning out to four destinations
        // produces a channel-level sent count of 4 — a silently-dropped destination would
        // fail this >= 4 gate.
        assertThreeLevels(DOC_WRITER_CHANNEL_ID, 4, () -> {
            Path pdfPath = pollForFile(Paths.get(outDir, "doc", "output.pdf"), 60);
            Path rtfPath = pollForFile(Paths.get(outDir, "doc", "output.rtf"), 60);

            // PDF: PDFBox PDFTextStripper content extraction (D-08 — not byte-exact). This is
            // the exact Phase 22 OpenPDF fidelity baseline assertion (see class javadoc).
            try (PDDocument pdf = PDDocument.load(pdfPath.toFile())) {
                String pdfText = new PDFTextStripper().getText(pdf);
                assertTrue("PDF text extraction should contain the transformed patient token",
                        pdfText.contains(Hl7Messages.EXPECTED_PATIENT));
            }

            // RTF: JDK built-in RTFEditorKit content extraction (D-08 — not byte-exact). This is
            // the exact Phase 22 OpenRTF fidelity baseline assertion (see class javadoc).
            RTFEditorKit rtfKit = new RTFEditorKit();
            Document rtfDoc = rtfKit.createDefaultDocument();
            try (FileInputStream rtfIn = new FileInputStream(rtfPath.toFile())) {
                rtfKit.read(rtfIn, rtfDoc, 0);
            }
            String rtfText = rtfDoc.getText(0, rtfDoc.getLength());
            assertTrue("RTF text extraction should contain the transformed patient token",
                    rtfText.contains(Hl7Messages.EXPECTED_PATIENT));

            // Complex RTF (SC-2/D-03, Phase 22.2): metaDataId 3's multi-table + heading
            // destination writes a DISTINCT on-disk file from the baseline output.rtf above.
            // Asserts both sibling-table tokens survive the real end-to-end
            // deploy -> pump -> DocumentDispatcher.createRTF() -> disk round trip.
            Path complexRtfPath = pollForFile(Paths.get(outDir, "doc", "output-complex.rtf"), 60);
            RTFEditorKit complexRtfKit = new RTFEditorKit();
            Document complexRtfDoc = complexRtfKit.createDefaultDocument();
            try (FileInputStream complexRtfIn = new FileInputStream(complexRtfPath.toFile())) {
                complexRtfKit.read(complexRtfIn, complexRtfDoc, 0);
            }
            String complexRtfText = complexRtfDoc.getText(0, complexRtfDoc.getLength());
            assertTrue("Complex RTF text extraction should contain the first table's token",
                    complexRtfText.contains(VITALS_TABLE_TOKEN));
            assertTrue("Complex RTF text extraction should contain the second table's token "
                    + "(proving both sibling tables rendered, not just the first)",
                    complexRtfText.contains(MEDS_TABLE_TOKEN));

            // Encrypted PDF (SC-3/D-04, Phase 22.2): metaDataId 4's encrypt=true destination
            // writes a DISTINCT on-disk file from the baseline output.pdf above. Mirrors
            // DocRenderSeamTest#testEncryptPdfPasswordRoundTrip's assertion shape, proving the
            // end-to-end channel-deployed encrypt path (not just the reflective seam) on disk.
            Path encPdfPath = pollForFile(Paths.get(outDir, "doc", "output-enc.pdf"), 60);
            try {
                PDDocument.load(encPdfPath.toFile()).close();
                fail("loading the encrypted PDF without a password should throw");
            } catch (InvalidPasswordException expected) {
                // pass
            }
            try (PDDocument encPdf = PDDocument.load(encPdfPath.toFile(), ENCRYPTED_PDF_PASSWORD)) {
                assertTrue("document encrypted with a password must report isEncrypted() == true",
                        encPdf.isEncrypted());
            }
        });
    }
}
