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

import java.nio.charset.Charset;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link CharsetUtils}, focused on how DEFAULT_ENCODING resolves and on the IRT-1780
 * change that lets the single-argument overload fall back to the server-wide
 * {@code ca.uhn.hl7v2.llp.charset} override.
 */
public class CharsetUtilsTest {

    private static final String CHARSET_PROPERTY = "ca.uhn.hl7v2.llp.charset";

    private String savedProperty;

    @Before
    public void snapshotProperty() {
        savedProperty = System.getProperty(CHARSET_PROPERTY);
        System.clearProperty(CHARSET_PROPERTY);
    }

    @After
    public void restoreProperty() {
        if (savedProperty == null) {
            System.clearProperty(CHARSET_PROPERTY);
        } else {
            System.setProperty(CHARSET_PROPERTY, savedProperty);
        }
    }

    // -------------------------------------------------------------------------
    // Two-argument overload
    // -------------------------------------------------------------------------

    @Test
    public void testExplicitCharsetReturnedVerbatim() {
        assertEquals("UTF-8", CharsetUtils.getEncoding("UTF-8", "windows-1252"));
    }

    @Test
    public void testDefaultEncodingUsesProvidedDefault() {
        assertEquals("windows-1252", CharsetUtils.getEncoding(CharsetUtils.DEFAULT_ENCODING, "windows-1252"));
    }

    @Test
    public void testDefaultEncodingWithNullDefaultFallsBackToPlatformCharset() {
        assertEquals(Charset.defaultCharset().name(), CharsetUtils.getEncoding(CharsetUtils.DEFAULT_ENCODING, null));
    }

    @Test
    public void testDefaultEncodingWithBlankDefaultFallsBackToPlatformCharset() {
        assertEquals(Charset.defaultCharset().name(), CharsetUtils.getEncoding(CharsetUtils.DEFAULT_ENCODING, "   "));
    }

    @Test
    public void testBlankCharsetTreatedAsDefaultEncoding() {
        assertEquals("windows-1252", CharsetUtils.getEncoding("", "windows-1252"));
    }

    @Test
    public void testNullCharsetTreatedAsDefaultEncoding() {
        assertEquals("windows-1252", CharsetUtils.getEncoding(null, "windows-1252"));
    }

    // -------------------------------------------------------------------------
    // Single-argument overload (IRT-1780)
    // -------------------------------------------------------------------------

    @Test
    public void testSingleArgDefaultEncodingWithoutPropertyFallsBackToPlatformCharset() {
        // Property unset (cleared in @Before) -> behavior identical to before IRT-1780.
        assertEquals(Charset.defaultCharset().name(), CharsetUtils.getEncoding(CharsetUtils.DEFAULT_ENCODING));
    }

    @Test
    public void testSingleArgDefaultEncodingHonorsCharsetProperty() {
        System.setProperty(CHARSET_PROPERTY, "windows-1252");
        assertEquals("windows-1252", CharsetUtils.getEncoding(CharsetUtils.DEFAULT_ENCODING));
    }

    @Test
    public void testSingleArgBlankCharsetHonorsCharsetProperty() {
        System.setProperty(CHARSET_PROPERTY, "windows-1252");
        assertEquals("windows-1252", CharsetUtils.getEncoding(""));
    }

    @Test
    public void testSingleArgExplicitCharsetWinsOverProperty() {
        // An explicit charset on the connector is never overridden by the server-wide property.
        System.setProperty(CHARSET_PROPERTY, "windows-1252");
        assertEquals("UTF-8", CharsetUtils.getEncoding("UTF-8"));
    }

    @Test
    public void testSingleArgNullCharsetHonorsCharsetProperty() {
        // A connector whose charset property was never set reaches getEncoding with null; the
        // server-wide override must still apply, same as for DEFAULT_ENCODING and blank.
        System.setProperty(CHARSET_PROPERTY, "windows-1252");
        assertEquals("windows-1252", CharsetUtils.getEncoding((String) null));
    }

    @Test
    public void testSingleArgNullCharsetWithoutPropertyFallsBackToPlatformCharset() {
        // Property unset (cleared in @Before) -> behavior identical to before IRT-1780.
        assertEquals(Charset.defaultCharset().name(), CharsetUtils.getEncoding((String) null));
    }
}
