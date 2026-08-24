/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/**
 * Covers the code-point handling of {@link MirthXmlUtil#decode(String)} and the
 * {@link MirthXmlUtil#encode} overloads (IRT-1742 / IRT-1743). The two halves must round-trip, so
 * they are tested together.
 */
public class MirthXmlUtilTest {

    // U+1F50D LEFT-POINTING MAGNIFYING GLASS: a supplementary-plane character (emoji).
    private static final int MAGNIFYING_GLASS = 0x1F50D;
    private static final String MAGNIFYING_GLASS_STR = new String(Character.toChars(MAGNIFYING_GLASS));

    /* -------------------------------------------------------------------------------------------
     * decode()
     * ----------------------------------------------------------------------------------------- */

    @Test
    public void testDecodeDecimalSupplementary() {
        String decoded = MirthXmlUtil.decode("&#128269;");
        assertEquals(MAGNIFYING_GLASS_STR, decoded);
        // A supplementary character occupies two UTF-16 code units.
        assertEquals(2, decoded.length());
        assertEquals(MAGNIFYING_GLASS, decoded.codePointAt(0));
    }

    @Test
    public void testDecodeHexSupplementaryMatchesDecimal() {
        assertEquals(MirthXmlUtil.decode("&#128269;"), MirthXmlUtil.decode("&#x1F50D;"));
        assertEquals(MAGNIFYING_GLASS_STR, MirthXmlUtil.decode("&#x1F50D;"));
        // Uppercase radix prefix must behave identically.
        assertEquals(MAGNIFYING_GLASS_STR, MirthXmlUtil.decode("&#X1F50D;"));
    }

    @Test
    public void testDecodeBmpReference() {
        assertEquals("A", MirthXmlUtil.decode("&#65;"));
    }

    @Test
    public void testDecodeNamedEntity() {
        assertEquals("&", MirthXmlUtil.decode("&amp;"));
    }

    @Test
    public void testDecodeSurrogateReferenceThrows() {
        assertThrows(IllegalArgumentException.class, () -> MirthXmlUtil.decode("&#xD800;"));
    }

    @Test
    public void testDecodeOutOfRangeReferenceThrows() {
        assertThrows(IllegalArgumentException.class, () -> MirthXmlUtil.decode("&#x110000;"));
    }

    @Test
    public void testDecodeOverflowReferenceThrows() {
        // Larger than Integer.MAX_VALUE: parseInt overflows and must surface as IllegalArgumentException.
        assertThrows(IllegalArgumentException.class, () -> MirthXmlUtil.decode("&#99999999999;"));
    }

    @Test
    public void testDecodeNonNumericReferenceThrows() {
        // Unparseable hex token: must surface as IllegalArgumentException, not a raw NumberFormatException.
        assertThrows(IllegalArgumentException.class, () -> MirthXmlUtil.decode("&#xZZ;"));
    }

    @Test
    public void testDecodeControlAndNoncharacterReferencesSucceed() {
        /*
         * Intentional asymmetry with encode(): decode() is a general entity->character utility and
         * is scoped (per IRT-1743) to reject only surrogate/out-of-range/overflow references. C0
         * controls and XML noncharacters decode to their literal character rather than throwing.
         */
        assertEquals("\u0001", MirthXmlUtil.decode("&#1;"));
        assertEquals("\uFFFE", MirthXmlUtil.decode("&#xFFFE;"));
    }

    /* -------------------------------------------------------------------------------------------
     * encode()
     * ----------------------------------------------------------------------------------------- */

    @Test
    public void testEncodeSupplementaryAsSingleReference() {
        assertEquals("&#128269;", MirthXmlUtil.encode(MAGNIFYING_GLASS_STR));
    }

    @Test
    public void testEncodeBmpAboveAsciiAsNumericReference() {
        // U+2764 HEAVY BLACK HEART, a BMP character above the ASCII range.
        assertEquals("&#10084;", MirthXmlUtil.encode("\u2764"));
    }

    @Test
    public void testEncodeAsciiUnchanged() {
        assertEquals("Hello", MirthXmlUtil.encode("Hello"));
    }

    @Test
    public void testEncodeLineFeedAndCarriageReturn() {
        assertEquals("&#10;", MirthXmlUtil.encode("\n"));
        assertEquals("&#13;", MirthXmlUtil.encode("\r"));
    }

    @Test
    public void testEncodeTabPreservedRaw() {
        // Tab is a legal XML 1.0 character and has no named encoding, so it passes through.
        assertEquals("\t", MirthXmlUtil.encode("\t"));
    }

    @Test
    public void testEncodeNamedXmlEntities() {
        assertEquals("&lt;", MirthXmlUtil.encode("<"));
        assertEquals("&amp;", MirthXmlUtil.encode("&"));
    }

    @Test
    public void testEncodeControlCharacterThrows() {
        // U+0001, a C0 control other than tab/LF/CR, is not a valid XML 1.0 character.
        assertThrows(IllegalArgumentException.class, () -> MirthXmlUtil.encode("\u0001"));
    }

    @Test
    public void testEncodeUnpairedSurrogateThrows() {
        // A lone high surrogate cannot form a valid code point.
        assertThrows(IllegalArgumentException.class, () -> MirthXmlUtil.encode("\uD83D"));
    }

    @Test
    public void testEncodeNoncharacterThrows() {
        // U+FFFE and U+FFFF are excluded from the XML 1.0 Char production (the E000-FFFD range stops at FFFD).
        assertThrows(IllegalArgumentException.class, () -> MirthXmlUtil.encode("\uFFFE"));
        assertThrows(IllegalArgumentException.class, () -> MirthXmlUtil.encode("\uFFFF"));
    }

    @Test
    public void testEncodeCharArrayOverloadSupplementary() {
        char[] text = MAGNIFYING_GLASS_STR.toCharArray();
        assertEquals("&#128269;", MirthXmlUtil.encode(text, 0, text.length));
    }

    @Test
    public void testEncodeCharArrayOverloadWithOffset() {
        // The supplementary character sits at index 2..3; exercise the start/end index arithmetic.
        char[] text = ("XX" + MAGNIFYING_GLASS_STR + "YY").toCharArray();
        assertEquals("&#128269;", MirthXmlUtil.encode(text, 2, 2));
    }

    @Test
    public void testEncodeSingleCharOverload() {
        assertEquals("&#10084;", MirthXmlUtil.encode('\u2764'));
        assertEquals("A", MirthXmlUtil.encode('A'));
    }

    /* -------------------------------------------------------------------------------------------
     * round trip
     * ----------------------------------------------------------------------------------------- */

    @Test
    public void testRoundTripSupplementary() {
        String encoded = MirthXmlUtil.encode(MAGNIFYING_GLASS_STR);
        assertEquals(MAGNIFYING_GLASS_STR, MirthXmlUtil.decode(encoded));
    }
}
