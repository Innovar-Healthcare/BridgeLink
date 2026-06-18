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

import java.awt.Color;

import org.junit.Test;

/**
 * Tests for ColorUtil — headless-safe methods only.
 * toBufferedImage and tint are excluded (may fail in headless JVM, per RESEARCH.md A3).
 */
public class ColorUtilTest {

    // -------------------------------------------------------------------------
    // convertToHex
    // -------------------------------------------------------------------------

    @Test
    public void testConvertToHexRed() {
        assertEquals("#FF0000", ColorUtil.convertToHex(Color.RED));
    }

    @Test
    public void testConvertToHexBlack() {
        assertEquals("#000000", ColorUtil.convertToHex(Color.BLACK));
    }

    @Test
    public void testConvertToHexWhite() {
        assertEquals("#FFFFFF", ColorUtil.convertToHex(Color.WHITE));
    }

    @Test
    public void testConvertToHexNull() {
        assertEquals("", ColorUtil.convertToHex(null));
    }

    @Test
    public void testConvertToHexBlue() {
        assertEquals("#0000FF", ColorUtil.convertToHex(Color.BLUE));
    }

    @Test
    public void testConvertToHexCustomColor() {
        // new Color(r, g, b): 0x12, 0xAB, 0xCD
        Color c = new Color(0x12, 0xAB, 0xCD);
        assertEquals("#12ABCD", ColorUtil.convertToHex(c));
    }

    // -------------------------------------------------------------------------
    // getForegroundColor
    // -------------------------------------------------------------------------

    @Test
    public void testGetForegroundColorBlackBackgroundReturnsWhite() {
        // Black background (0,0,0) -> high luminance -> WHITE text
        assertEquals(Color.WHITE, ColorUtil.getForegroundColor(Color.BLACK));
    }

    @Test
    public void testGetForegroundColorWhiteBackgroundReturnsBlack() {
        // White background (255,255,255) -> low luminance -> BLACK text
        assertEquals(Color.BLACK, ColorUtil.getForegroundColor(Color.WHITE));
    }

    @Test
    public void testGetForegroundColorDarkColorReturnsWhite() {
        // Dark navy-like color should produce white foreground
        Color darkBlue = new Color(0, 0, 128);
        assertEquals(Color.WHITE, ColorUtil.getForegroundColor(darkBlue));
    }

    @Test
    public void testGetForegroundColorLightYellowReturnsBlack() {
        // Light yellow is bright: luminance < 0.5 -> BLACK
        Color lightYellow = new Color(255, 255, 200);
        assertEquals(Color.BLACK, ColorUtil.getForegroundColor(lightYellow));
    }

    // -------------------------------------------------------------------------
    // getNewColor
    // -------------------------------------------------------------------------

    @Test
    public void testGetNewColorReturnsNonNull() {
        Color c = ColorUtil.getNewColor();
        assertNotNull("getNewColor() must not return null", c);
    }

    @Test
    public void testGetNewColorCyclesThroughPalette() {
        // The palette has 12 colors and selection is static.
        // Call getNewColor 13 times; all results must be non-null (cyclic modulo).
        for (int i = 0; i < 13; i++) {
            assertNotNull("getNewColor() should never return null (call " + i + ")", ColorUtil.getNewColor());
        }
    }
}
