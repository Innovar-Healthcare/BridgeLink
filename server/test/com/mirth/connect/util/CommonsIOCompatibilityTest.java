/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

/**
 * API-surface compatibility test for commons-io 2.16.1.
 *
 * Exercises the core {@link IOUtils} utility methods — copy, toByteArray, and toString — used
 * pervasively throughout BridgeLink connectors and utilities, to confirm the upgraded JAR is
 * API-compatible.
 */
public class CommonsIOCompatibilityTest {

    @Test
    public void testIOUtilsCopy() throws Exception {
        byte[] input = "hello-commons-io".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(input);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOUtils.copy(in, out);
        assertEquals("hello-commons-io", out.toString("UTF-8"));
    }

    @Test
    public void testIOUtilsToByteArray() throws Exception {
        byte[] input = "test-bytes".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(input);
        byte[] result = IOUtils.toByteArray(in);
        assertNotNull(result);
        assertEquals(input.length, result.length);
    }

    @Test
    public void testIOUtilsToString() throws Exception {
        byte[] input = "to-string-test".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(input);
        String result = IOUtils.toString(in, "UTF-8");
        assertEquals("to-string-test", result);
    }
}
