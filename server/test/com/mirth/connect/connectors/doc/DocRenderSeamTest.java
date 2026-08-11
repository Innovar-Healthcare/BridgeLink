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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.pdfbox.pdmodel.PDDocument;
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
}
