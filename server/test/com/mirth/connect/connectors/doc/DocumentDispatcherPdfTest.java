/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.doc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Test;

/**
 * API-surface smoke test for pdfbox 2.0.36.
 *
 * Exercises {@link PDDocument} lifecycle — instantiation, page-count query, and close — to
 * confirm the upgraded JAR is API-compatible with the DocumentDispatcher connector.
 */
public class DocumentDispatcherPdfTest {

    @Test
    public void testPdfDocumentCreateAndClose() throws Exception {
        PDDocument doc = new PDDocument();
        try {
            assertNotNull(doc);
        } finally {
            doc.close();
        }
    }

    @Test
    public void testPdfDocumentPageCount() throws Exception {
        PDDocument doc = new PDDocument();
        try {
            assertEquals(0, doc.getNumberOfPages());
        } finally {
            doc.close();
        }
    }
}
