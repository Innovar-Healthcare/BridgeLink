/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.http;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.junit.Test;

/**
 * API-surface smoke test for commons-fileupload 1.6.0.
 *
 * Exercises {@link ServletFileUpload#isMultipartContent(HttpServletRequest)} — the exact API
 * called by {@link HttpReceiver} in production — to confirm the upgraded JAR is API-compatible.
 */
public class MultipartDetectionTest {

    @Test
    public void testIsMultipartContentTrue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("multipart/form-data; boundary=----boundary");
        assertTrue(ServletFileUpload.isMultipartContent(request));
    }

    @Test
    public void testIsMultipartContentFalse() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("application/json");
        assertFalse(ServletFileUpload.isMultipartContent(request));
    }

    @Test
    public void testIsMultipartContentGetRequestReturnsFalse() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getContentType()).thenReturn("multipart/form-data; boundary=----boundary");
        assertFalse(ServletFileUpload.isMultipartContent(request));
    }
}
