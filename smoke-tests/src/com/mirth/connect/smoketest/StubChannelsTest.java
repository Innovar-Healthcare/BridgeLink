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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.Document;
import javax.swing.text.rtf.RTFEditorKit;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
        // doc-writer-test.xml has EIGHT destinations (PDF at metaDataId 1, RTF at metaDataId 2,
        // Complex RTF at metaDataId 3, Encrypted PDF at metaDataId 4, Large Multi-Page PDF at
        // metaDataId 5, Large Multi-Table RTF at metaDataId 6, Discharge Summary PDF at
        // metaDataId 7, Discharge Summary RTF at metaDataId 8, Phase 22.4 discharge port) — all
        // eight must be targeted explicitly (Rule 1 fix, see RestClient.processMessage javadoc).
        rest.processMessage(DOC_WRITER_CHANNEL_ID, Hl7Messages.ORU_R01_LF, java.util.List.of(1, 2, 3, 4, 5, 6, 7, 8));
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

    // Phase 22.3 (SC-3): tokens mirror DocRenderSeamTest's Wave-1 tracer (22.3-01)
    // MPAGE_TABLE_TOKENS/MPAGE_LAST_ROW_TOKEN_22P3/MPAGE_ACCENTED_EARLY/MPAGE_ACCENTED_LATE
    // verbatim, and the doc-writer-test.xml source-transformer generator step (metaDataId
    // 5/6 destinations) that builds the large document, so the end-to-end smoke assertions
    // below reuse the exact same fixture shape/tokens as the seam-level test they extend.
    private static final String LARGE_LAST_ROW_TOKEN = "MPAGE_LAST_ROW_TOKEN_22P3";
    private static final String[] LARGE_TABLE_TOKENS = { "MPAGE_TABLE_TOKEN_1_22P3", "MPAGE_TABLE_TOKEN_2_22P3",
            "MPAGE_TABLE_TOKEN_3_22P3", "MPAGE_TABLE_TOKEN_4_22P3", "MPAGE_TABLE_TOKEN_5_22P3" };
    private static final String LARGE_ACCENTED_EARLY = "José";
    private static final String LARGE_ACCENTED_LATE = "Müller";

    // Phase 22.4 (CVE-07/IRT-1491, D-05/D-06/D-09): tokens and the footer pattern mirror the
    // discharge transformer step ported verbatim from the out-of-repo UAT export
    // (docwriter-uat.xml) into doc-writer-test.xml's metaDataId 7/8 destinations. The end
    // marker and the accented attending surname are asserted to survive read-back in both the
    // Discharge PDF and Discharge RTF outputs; the footer pattern is PDF-only (CSS3 paged-media
    // counters rendered by openhtmltopdf — the RTF path has no footer).
    private static final String DISCHARGE_END_MARKER = "ZZ-END-9999";
    private static final String DISCHARGE_ACCENTED_SURNAME = "Müller-Bergström";
    private static final Pattern DISCHARGE_FOOTER_PATTERN = Pattern.compile("Page \\d+ of \\d+");

    /**
     * D-06 logo image-XObject triangulation ("right picture, right place, no regression"):
     * walks every page's resource XObjects, collecting each {@link PDImageXObject} found along
     * with the zero-based page index it appears on. Asserts exactly one image XObject exists
     * across the whole document, that it appears on page index 0 and on no later page (the logo
     * lives in the header table, not a running header — only the CSS footer repeats per page),
     * and that its intrinsic raster dimensions are exactly 96x96 px (the base64 PNG source,
     * regardless of the 54px CSS display size) — proving it is the real logo raster, not a
     * corrupted/blank/swapped image.
     */
    private static void assertSingleLogoXObject(PDDocument doc, int expectedWidthPx, int expectedHeightPx)
            throws java.io.IOException {
        int imageCount = 0;
        int firstImagePageIndex = -1;
        for (int pageIndex = 0; pageIndex < doc.getNumberOfPages(); pageIndex++) {
            PDPage page = doc.getPage(pageIndex);
            PDResources resources = page.getResources();
            if (resources == null) {
                continue;
            }
            for (COSName xObjectName : resources.getXObjectNames()) {
                PDXObject xObject = resources.getXObject(xObjectName);
                if (xObject instanceof PDImageXObject) {
                    PDImageXObject image = (PDImageXObject) xObject;
                    imageCount++;
                    if (firstImagePageIndex == -1) {
                        firstImagePageIndex = pageIndex;
                    }
                    assertEquals("Logo image XObject on page " + pageIndex + " should be "
                            + expectedWidthPx + "px wide", expectedWidthPx, image.getWidth());
                    assertEquals("Logo image XObject on page " + pageIndex + " should be "
                            + expectedHeightPx + "px tall", expectedHeightPx, image.getHeight());
                    assertTrue("Logo image XObject should only appear on page 0 (the header), "
                            + "not page " + pageIndex, pageIndex == 0);
                }
            }
        }
        assertEquals("Discharge PDF should carry exactly one image XObject (the logo)", 1, imageCount);
        assertEquals("Logo image XObject should be present on page 0", 0, firstImagePageIndex);
    }

    @Test
    public void docWriter() throws Exception {
        // Phase 22.4/D-10: raised from 6 to 8 — all eight enabled destinations (PDF, RTF,
        // Complex RTF, Encrypted PDF, Large Multi-Page PDF, Large Multi-Table RTF, Discharge
        // Summary PDF, Discharge Summary RTF) must reach SENT status. getSentCount() aggregates
        // the "sent" statistic across every destination connector
        // (DonkeyEngineController#addConnectorToChannelStatistics sums per-destination SENT
        // counts into the channel-level total), so one pumped message fanning out to eight
        // destinations produces a channel-level sent count of 8 — a silently-dropped destination
        // would fail this >= 8 gate.
        assertThreeLevels(DOC_WRITER_CHANNEL_ID, 8, () -> {
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

            // Large Multi-Page PDF (SC-3, Phase 22.3): metaDataId 5's destination writes the
            // 5-table/80-row-per-table document generated by doc-writer-test.xml's source
            // transformer to a DISTINCT on-disk file. Proves the large document survives the
            // real deploy -> pump -> DocumentDispatcher.createPDF() -> disk round trip, not
            // just the DocRenderSeamTest reflective seam (22.3-01).
            Path largePdfPath = pollForFile(Paths.get(outDir, "doc", "output-large.pdf"), 60);
            try (PDDocument largePdf = PDDocument.load(largePdfPath.toFile())) {
                // Never == N (D-04, mirrors 22.3-01): a library reflow must not falsely break
                // this gate. If a future bump ever drops the page count below 5, the fix is to
                // raise the row/table count in doc-writer-test.xml's generator script, never to
                // lower this threshold.
                assertTrue("Large PDF must paginate to at least 5 pages, got "
                        + largePdf.getNumberOfPages(), largePdf.getNumberOfPages() >= 5);
                String largePdfText = new PDFTextStripper().getText(largePdf);
                assertTrue("Large PDF text should contain the last-row truncation tripwire token",
                        largePdfText.contains(LARGE_LAST_ROW_TOKEN));
                for (String token : LARGE_TABLE_TOKENS) {
                    assertTrue("Large PDF text should contain table token " + token, largePdfText.contains(token));
                }
                assertTrue("Large PDF text should preserve the accented token 'José' at document scale",
                        largePdfText.contains(LARGE_ACCENTED_EARLY));
                assertTrue("Large PDF text should preserve the accented token 'Müller' at document scale",
                        largePdfText.contains(LARGE_ACCENTED_LATE));
            }

            // Large Multi-Table RTF (SC-3, Phase 22.3): metaDataId 6's destination writes the
            // same large document to a DISTINCT on-disk RTF file. No page-count assertion
            // (D-06, mirrors 22.3-01): RTFEditorKit reads into a Swing Document model and never
            // paginates.
            Path largeRtfPath = pollForFile(Paths.get(outDir, "doc", "output-large.rtf"), 60);
            RTFEditorKit largeRtfKit = new RTFEditorKit();
            Document largeRtfDoc = largeRtfKit.createDefaultDocument();
            try (FileInputStream largeRtfIn = new FileInputStream(largeRtfPath.toFile())) {
                largeRtfKit.read(largeRtfIn, largeRtfDoc, 0);
            }
            String largeRtfText = largeRtfDoc.getText(0, largeRtfDoc.getLength());
            for (String token : LARGE_TABLE_TOKENS) {
                assertTrue("Large RTF text should contain table token " + token, largeRtfText.contains(token));
            }
            assertTrue("Large RTF text should contain the last-row truncation tripwire token",
                    largeRtfText.contains(LARGE_LAST_ROW_TOKEN));
            assertTrue("Large RTF text should preserve the accented token 'José'",
                    largeRtfText.contains(LARGE_ACCENTED_EARLY));
            assertTrue("Large RTF text should preserve the accented token 'Müller'",
                    largeRtfText.contains(LARGE_ACCENTED_LATE));

            // Discharge Summary PDF (Phase 22.4/D-05/D-06/D-07/D-09): metaDataId 7's destination
            // renders the realistic, fully-styled clinical discharge summary (logo header,
            // demographics/diagnoses/meds tables, hospital-course narrative, 60-row serial-lab
            // table, 14 daily progress notes) ported verbatim from the out-of-repo UAT export
            // into doc-writer-test.xml. Proves the new fixture deploys and paginates on disk via
            // the real deploy -> pump -> DocumentDispatcher.createPDF() -> disk round trip, and
            // that the logo/footer/token features rendered as intended (not just "the file
            // exists").
            Path dischargePdfPath = pollForFile(Paths.get(outDir, "doc", "output-discharge.pdf"), 60);
            try (PDDocument dischargePdf = PDDocument.load(dischargePdfPath.toFile())) {
                assertTrue("Discharge PDF must paginate to at least 5 pages, got "
                        + dischargePdf.getNumberOfPages(), dischargePdf.getNumberOfPages() >= 5);

                String dischargePdfText = new PDFTextStripper().getText(dischargePdf);

                // D-05: well-formed "Page N of M" footer, regex match (not a substring check).
                Matcher footerMatcher = DISCHARGE_FOOTER_PATTERN.matcher(dischargePdfText);
                assertTrue("Discharge PDF text should carry a well-formed 'Page N of M' footer",
                        footerMatcher.find());

                // D-09: end marker and accented attending surname survive PDFTextStripper
                // read-back.
                assertTrue("Discharge PDF text should contain the end-marker token",
                        dischargePdfText.contains(DISCHARGE_END_MARKER));
                assertTrue("Discharge PDF text should preserve the accented attending surname",
                        dischargePdfText.contains(DISCHARGE_ACCENTED_SURNAME));

                // D-06: exactly one logo image XObject, present on page 1 only, 96x96 intrinsic
                // dimensions.
                assertSingleLogoXObject(dischargePdf, 96, 96);
            }

            // Discharge Summary RTF (Phase 22.4/D-08/D-09): metaDataId 8's destination renders
            // the same discharge content with <img> stripped and thead/tbody flattened by the
            // transformer (the legacy com.lowagie.text.html.HtmlParser RTF path throws
            // FileNotFoundException on a data: <img> URI and does not paginate). Proves the RTF
            // renders cleanly with the logo removed rather than failing on the data: URI --
            // "img-stripped, not failed." The reaches-SENT-not-ERROR half of D-08 is already
            // enforced by the assertThreeLevels(DOC_WRITER_CHANNEL_ID, 8, ...) L1/L2 gate above.
            Path dischargeRtfPath = pollForFile(Paths.get(outDir, "doc", "output-discharge.rtf"), 60);
            RTFEditorKit dischargeRtfKit = new RTFEditorKit();
            Document dischargeRtfDoc = dischargeRtfKit.createDefaultDocument();
            try (FileInputStream dischargeRtfIn = new FileInputStream(dischargeRtfPath.toFile())) {
                dischargeRtfKit.read(dischargeRtfIn, dischargeRtfDoc, 0);
            }
            String dischargeRtfText = dischargeRtfDoc.getText(0, dischargeRtfDoc.getLength());

            // D-09: end marker and accented attending surname survive RTFEditorKit read-back.
            assertTrue("Discharge RTF text should contain the end-marker token",
                    dischargeRtfText.contains(DISCHARGE_END_MARKER));
            assertTrue("Discharge RTF text should preserve the accented attending surname",
                    dischargeRtfText.contains(DISCHARGE_ACCENTED_SURNAME));

            // D-08: no embedded image. Raw \pict byte-scan on the file's raw text (the
            // transformer strips every <img> tag before generating the RTF variant, so the
            // RTF writer should never emit a \pict picture group); chosen over the
            // RTFEditorKit document-model probe for a more direct, version-independent check
            // (D-08 accepts either mechanism).
            String dischargeRtfRaw = readFile(dischargeRtfPath);
            assertTrue("Discharge RTF should carry no embedded image (\\pict) group",
                    !dischargeRtfRaw.contains("\\pict"));
        });
    }
}
