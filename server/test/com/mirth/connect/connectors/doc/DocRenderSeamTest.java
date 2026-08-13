/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.doc;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import javax.swing.text.Document;
import javax.swing.text.rtf.RTFEditorKit;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Test;

import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

/**
 * Content-faithful regression seam for the Document Writer connector's iText -&gt; OpenPDF /
 * OpenRTF migration (CVE-07, Phase 22.1). Drives {@link DocumentDispatcher}'s three private
 * render seams ({@code createPDF}, {@code createRTF}, {@code encryptPDF}) via reflection inside a
 * {@code mockStatic(ControllerFactory.class)} construction scope, and asserts real content
 * fidelity rather than mere library-loadability. Supersedes the hollow
 * {@code DocumentDispatcherPdfTest}.
 * <p>
 * No {@code Assume}/skip guard is used anywhere in this class -- it runs unconditionally in the
 * standard {@code **&#47;*Test.class} batch on JDK 17 and 21 (SC-5, D-05).
 */
public class DocRenderSeamTest {

    private static final String EXPECTED_TOKEN = "PATIENT_TOKEN_22P1";

    /** Complete, DOCTYPE-free HTML fixture -- createPDF sets disallow-doctype-decl=true. */
    private static final String HTML = "<html><body><h1>Patient Report</h1><p>" + EXPECTED_TOKEN
            + "</p><table><tr><td>A</td></tr></table></body></html>";

    /** Well-formed but content-empty HTML fixture for the CVE-07/empty structural-validity edge. */
    private static final String EMPTY_HTML = "<html><body></body></html>";

    /**
     * Realistic multi-table + heading fixture (SC-1, D-01, Phase 22.2). A single {@code <h1>}
     * heading followed by TWO separate (non-nested) {@code <table>} elements, each carrying a
     * distinct greppable token in a {@code <td>}. This is the exact template shape that threw
     * {@code ClassCastException: Table cannot be cast to TextElementArray} under iText 2.1.7 --
     * the old library flattened sibling tables into a single {@code TextElementArray} and choked
     * on the second one. Phase 22.1's {@link #HTML} fixture is a single trivial one-cell table
     * that renders identically on both libraries and never exercises this divergent path. OpenRTF
     * renders this fixture without throwing -- a strict superset of iText 2.1.7's capability (an
     * improvement, not a regression). The historical iText crash cannot be re-run here because
     * iText/itext-rtf has been removed from the tree (CVE-07); this fixture instead asserts the
     * NEW path renders content-faithfully.
     */
    private static final String COMPLEX_MULTI_TABLE_HEADING = "<html><body><h1>Multi-Table Report</h1>"
            + "<table><tr><td>VITALS_TABLE_TOKEN_22P2</td></tr></table>"
            + "<table><tr><td>MEDS_TABLE_TOKEN_22P2</td></tr></table></body></html>";

    /**
     * Non-ASCII / accented fixture (DW-1, D-06, Phase 22.2). Latin-1-range accented glyphs only
     * (no CJK -- openhtmltopdf's default font may not embed CJK glyphs, which would make the PDF
     * {@code PDFTextStripper} assertion font-dependent and brittle). Exercises
     * {@code DocumentDispatcher.java:192}'s platform-default {@code getBytes()} charset encode on
     * the RTF leg and the live {@code //TODO verify the character encoding} seam (~line 308).
     */
    private static final String NON_ASCII_TOKENS = "<html><body><p>José Müller</p></body></html>";

    /**
     * Action invoked with a freshly-constructed {@link DocumentDispatcher} while the
     * {@code mockStatic(ControllerFactory.class)} scope from {@link #withDispatcher} is still
     * open, so any statics the dispatcher's field initializer touched remain stubbed for the
     * lifetime of the render call.
     */
    private interface DispatcherAction {
        void run(DocumentDispatcher dispatcher) throws Exception;
    }

    /**
     * Opens a try-with-resources {@code mockStatic(ControllerFactory.class)} scope, stubs
     * {@code ControllerFactory::getFactory} to a mock whose {@code createEventController()}
     * returns a {@code mock(EventController.class)}, constructs a fresh {@code
     * new DocumentDispatcher()} (satisfying the line-67 field initializer without the heavy
     * env-dependent {@code ExtensionLoader} bootstrap), and runs {@code action} against it --
     * entirely inside the scope.
     */
    private void withDispatcher(DispatcherAction action) throws Exception {
        try (var mockedControllerFactory = mockStatic(ControllerFactory.class)) {
            ControllerFactory mockFactory = mock(ControllerFactory.class);
            mockedControllerFactory.when(ControllerFactory::getFactory).thenReturn(mockFactory);
            when(mockFactory.createEventController()).thenReturn(mock(EventController.class));

            DocumentDispatcher dispatcher = new DocumentDispatcher();
            action.run(dispatcher);
        }
    }

    /**
     * Looks up the named declared method on {@link DocumentDispatcher}, makes it accessible, and
     * invokes it reflectively, unwrapping {@link InvocationTargetException} so a real library
     * break surfaces as the underlying exception rather than an opaque reflection wrapper.
     */
    private Object invokePrivate(DocumentDispatcher dispatcher, String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method method = DocumentDispatcher.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        try {
            return method.invoke(dispatcher, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    private void invokeCreatePdf(DocumentDispatcher dispatcher, Reader reader, OutputStream outputStream, DocumentDispatcherProperties props) throws Exception {
        invokePrivate(dispatcher, "createPDF", new Class<?>[] { Reader.class, OutputStream.class, DocumentDispatcherProperties.class }, new Object[] { reader, outputStream, props });
    }

    private void invokeCreateRtf(DocumentDispatcher dispatcher, InputStream inputStream, OutputStream outputStream, DocumentDispatcherProperties props) throws Exception {
        invokePrivate(dispatcher, "createRTF", new Class<?>[] { InputStream.class, OutputStream.class, DocumentDispatcherProperties.class }, new Object[] { inputStream, outputStream, props });
    }

    private void invokeEncryptPdf(DocumentDispatcher dispatcher, InputStream inputStream, OutputStream outputStream, String password) throws Exception {
        invokePrivate(dispatcher, "encryptPDF", new Class<?>[] { InputStream.class, OutputStream.class, String.class }, new Object[] { inputStream, outputStream, password });
    }

    // ------------------------------------------------------------------------------------------
    // Tracer: PDF render seam -- end-to-end content-faithful PDF assertion (SC-1, D-01, CVE-07)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testPdfRenderContentFaithful() throws Exception {
        withDispatcher(dispatcher -> {
            // Keep the ctor page defaults (pageWidth="8.5", pageHeight="11", pageUnit=INCHES).
            DocumentDispatcherProperties props = new DocumentDispatcherProperties();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            invokeCreatePdf(dispatcher, new StringReader(HTML), out, props);

            byte[] pdfBytes = out.toByteArray();
            try (PDDocument pdf = PDDocument.load(pdfBytes)) {
                assertTrue("PDF must have at least one page", pdf.getNumberOfPages() >= 1);
                String pdfText = new PDFTextStripper().getText(pdf);
                assertTrue("PDF text must contain the fixture token", pdfText.contains(EXPECTED_TOKEN));
            }
        });
    }

    // ------------------------------------------------------------------------------------------
    // RTF render seam -- content-faithful RTF assertion, true OpenPDF RtfWriter2 leg
    // (SC-2, D-01, CVE-07)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testRtfRenderContentFaithful() throws Exception {
        withDispatcher(dispatcher -> {
            DocumentDispatcherProperties props = new DocumentDispatcherProperties();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            invokeCreateRtf(dispatcher, new ByteArrayInputStream(HTML.getBytes()), out, props);

            byte[] rtfBytes = out.toByteArray();
            assertTrue("RTF must start with the {\\rtf control header",
                    new String(rtfBytes, StandardCharsets.US_ASCII).startsWith("{\\rtf"));

            RTFEditorKit rtfKit = new RTFEditorKit();
            Document rtfDoc = rtfKit.createDefaultDocument();
            try (ByteArrayInputStream rtfIn = new ByteArrayInputStream(rtfBytes)) {
                rtfKit.read(rtfIn, rtfDoc, 0);
            }
            String rtfText = rtfDoc.getText(0, rtfDoc.getLength());
            assertTrue("RTF text must contain the fixture token", rtfText.contains(EXPECTED_TOKEN));
        });
    }

    // ------------------------------------------------------------------------------------------
    // Complex multi-table + heading RTF differential: the exact template shape that threw
    // ClassCastException under iText 2.1.7 -- OpenRTF renders it content-faithfully
    // (SC-1, D-01, Phase 22.2, CVE-07)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testComplexMultiTableRtfRendersContentFaithful() throws Exception {
        withDispatcher(dispatcher -> {
            DocumentDispatcherProperties props = new DocumentDispatcherProperties();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            invokeCreateRtf(dispatcher, new ByteArrayInputStream(COMPLEX_MULTI_TABLE_HEADING.getBytes()), out, props);

            byte[] rtfBytes = out.toByteArray();
            assertTrue("RTF must start with the {\\rtf control header",
                    new String(rtfBytes, StandardCharsets.US_ASCII).startsWith("{\\rtf"));

            RTFEditorKit rtfKit = new RTFEditorKit();
            Document rtfDoc = rtfKit.createDefaultDocument();
            try (ByteArrayInputStream rtfIn = new ByteArrayInputStream(rtfBytes)) {
                rtfKit.read(rtfIn, rtfDoc, 0);
            }
            String rtfText = rtfDoc.getText(0, rtfDoc.getLength());
            assertTrue("RTF text must contain the heading text", rtfText.contains("Multi-Table Report"));
            assertTrue("RTF text must contain the first table's token", rtfText.contains("VITALS_TABLE_TOKEN_22P2"));
            assertTrue("RTF text must contain the second table's token (proving both sibling tables rendered, not just the first)",
                    rtfText.contains("MEDS_TABLE_TOKEN_22P2"));
        });
    }

    // ------------------------------------------------------------------------------------------
    // Non-ASCII / accented fidelity across the createPDF (Reader/char path) and createRTF
    // (InputStream/getBytes() platform-default-charset path) render seams
    // (DW-1, D-06, Phase 22.2, CVE-07)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testNonAsciiFidelityAcrossPdfAndRtf() throws Exception {
        withDispatcher(dispatcher -> {
            DocumentDispatcherProperties props = new DocumentDispatcherProperties();

            // PDF leg: createPDF takes a Reader (char path, no getBytes() encode step).
            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            invokeCreatePdf(dispatcher, new StringReader(NON_ASCII_TOKENS), pdfOut, props);
            try (PDDocument pdf = PDDocument.load(pdfOut.toByteArray())) {
                String pdfText = new PDFTextStripper().getText(pdf);
                assertTrue("PDF text must preserve the accented token 'José'", pdfText.contains("José"));
                assertTrue("PDF text must preserve the accented token 'Müller'", pdfText.contains("Müller"));
            }

            // RTF leg: deliberately reproduce DocumentDispatcher.java:192's exact production
            // encode -- fixture.getBytes() with the platform-default charset -- rather than an
            // explicit UTF-8/Latin-1 encode, so this fixture exercises the same seam production
            // traffic goes through (including the live //TODO verify the character encoding).
            ByteArrayOutputStream rtfOut = new ByteArrayOutputStream();
            invokeCreateRtf(dispatcher, new ByteArrayInputStream(NON_ASCII_TOKENS.getBytes()), rtfOut, props);

            RTFEditorKit rtfKit = new RTFEditorKit();
            Document rtfDoc = rtfKit.createDefaultDocument();
            try (ByteArrayInputStream rtfIn = new ByteArrayInputStream(rtfOut.toByteArray())) {
                rtfKit.read(rtfIn, rtfDoc, 0);
            }
            String rtfText = rtfDoc.getText(0, rtfDoc.getLength());

            // D-06 EMPIRICAL FINDING (verified via a standalone probe against the real
            // HtmlParser/RtfWriter2 jars before writing this assertion): on this JVM the
            // accented glyphs SURVIVE this seam intact -- no mangling. `fixture.getBytes()`
            // encodes with the JVM's platform-default charset (UTF-8 on this environment), and
            // OpenRTF's HtmlParser decodes the InputStream using that same platform-default
            // charset, so encode and decode are self-consistently paired and the round-trip is
            // exact. This is NOT an unconditional guarantee, though: the `//TODO verify the
            // character encoding` at DocumentDispatcher.java ~308 is live precisely because
            // `getBytes()`/decode both riding the ambient platform-default charset (rather than a
            // pinned charset such as UTF-8) means the same production code could decode
            // differently on a JVM/OS whose platform-default charset is NOT UTF-8 -- a latent
            // portability risk, not a reproducible bug on THIS platform. No product-code change
            // is warranted from this test-only finding (D-06).
            assertTrue("RTF text must preserve the accented token 'José'", rtfText.contains("José"));
            assertTrue("RTF text must preserve the accented token 'Müller'", rtfText.contains("Müller"));
        });
    }

    // ------------------------------------------------------------------------------------------
    // Empty-input structural-validity edge: both render seams must not throw on an empty-body
    // document, and each must produce a structurally valid document (no token/page-count pin)
    // (edge CVE-07/empty)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testEmptyTemplateRendersWithoutThrowing() throws Exception {
        withDispatcher(dispatcher -> {
            DocumentDispatcherProperties props = new DocumentDispatcherProperties();

            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            invokeCreatePdf(dispatcher, new StringReader(EMPTY_HTML), pdfOut, props);
            // Structural validity only -- no token, no page-count pin (empty body must not make
            // this edge brittle). PDDocument.load throws on structurally invalid PDF bytes, so a
            // clean load + close is the whole assertion.
            PDDocument.load(pdfOut.toByteArray()).close();

            ByteArrayOutputStream rtfOut = new ByteArrayOutputStream();
            invokeCreateRtf(dispatcher, new ByteArrayInputStream(EMPTY_HTML.getBytes()), rtfOut, props);
            assertTrue("empty-input RTF must start with the {\\rtf control header",
                    new String(rtfOut.toByteArray(), StandardCharsets.US_ASCII).startsWith("{\\rtf"));
        });
    }

    // ------------------------------------------------------------------------------------------
    // encryptPDF password round-trip: correct password opens + reports encrypted; wrong/absent
    // password is rejected (SC-3, D-03, CVE-07)
    // ------------------------------------------------------------------------------------------

    @Test
    public void testEncryptPdfPasswordRoundTrip() throws Exception {
        withDispatcher(dispatcher -> {
            DocumentDispatcherProperties props = new DocumentDispatcherProperties();
            String fixturePassword = "s3cret";

            ByteArrayOutputStream plainOut = new ByteArrayOutputStream();
            invokeCreatePdf(dispatcher, new StringReader(HTML), plainOut, props);

            ByteArrayOutputStream encOut = new ByteArrayOutputStream();
            invokeEncryptPdf(dispatcher, new ByteArrayInputStream(plainOut.toByteArray()), encOut, fixturePassword);

            byte[] enc = encOut.toByteArray();

            // Correct password opens the output and reports it as encrypted.
            try (PDDocument ok = PDDocument.load(enc, fixturePassword)) {
                assertTrue("document encrypted with a password must report isEncrypted() == true", ok.isEncrypted());
            }

            // No password must be rejected.
            try {
                PDDocument.load(enc).close();
                fail("loading an encrypted PDF without a password should throw");
            } catch (InvalidPasswordException expected) {
                // pass
            }

            // Wrong password must also be rejected.
            try {
                PDDocument.load(enc, "wrong-password").close();
                fail("loading an encrypted PDF with the wrong password should throw");
            } catch (InvalidPasswordException expected) {
                // pass
            }
        });
    }
}
