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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class StringUtilTest {

    // -------------------------------------------------------------------------
    // convertLineBreaks
    // -------------------------------------------------------------------------

    @Test
    public void testConvertLineBreaksCRLF() {
        String result = StringUtil.convertLineBreaks("hello\r\nworld", "\n");
        assertEquals("hello\nworld", result);
    }

    @Test
    public void testConvertLineBreaksCROnly() {
        String result = StringUtil.convertLineBreaks("hello\rworld", "\n");
        assertEquals("hello\nworld", result);
    }

    @Test
    public void testConvertLineBreaksLFOnly() {
        String result = StringUtil.convertLineBreaks("hello\nworld", "\n");
        assertEquals("hello\nworld", result);
    }

    @Test
    public void testConvertLineBreaksNoBreakReturnsSameInstance() {
        String input = "noline";
        String result = StringUtil.convertLineBreaks(input, "\n");
        assertSame("No line breaks — should return the exact same String instance", input, result);
    }

    @Test
    public void testConvertLineBreaksMultipleCRLF() {
        String result = StringUtil.convertLineBreaks("a\r\nb\r\nc", "<br>");
        assertEquals("a<br>b<br>c", result);
    }

    @Test
    public void testConvertLineBreaksTrailingCRLF() {
        String result = StringUtil.convertLineBreaks("text\r\n", "\n");
        assertEquals("text\n", result);
    }

    // -------------------------------------------------------------------------
    // unescape
    // -------------------------------------------------------------------------

    @Test
    public void testUnescapeNull() {
        assertNull(StringUtil.unescape(null));
    }

    @Test
    public void testUnescapeEmpty() {
        assertEquals("", StringUtil.unescape(""));
    }

    @Test
    public void testUnescapeHexNewline() {
        String result = StringUtil.unescape("0x0a");
        assertEquals("\n", result);
    }

    @Test
    public void testUnescapeBackslashT() {
        String result = StringUtil.unescape("\\t");
        assertEquals("\t", result);
    }

    @Test
    public void testUnescapeBackslashN() {
        String result = StringUtil.unescape("\\n");
        assertEquals("\n", result);
    }

    @Test
    public void testUnescapeBackslashR() {
        String result = StringUtil.unescape("\\r");
        assertEquals("\r", result);
    }

    @Test
    public void testUnescapeQuotedLiteral() {
        // Double-quoted string: strips the surrounding quotes, treats the rest as literal
        String result = StringUtil.unescape("\"hello\\n\"");
        assertEquals("hello\\n", result);
    }

    @Test
    public void testUnescapeHexTab() {
        String result = StringUtil.unescape("0x09");
        assertEquals("\t", result);
    }

    // -------------------------------------------------------------------------
    // equalsIgnoreNull
    // -------------------------------------------------------------------------

    @Test
    public void testEqualsIgnoreNullBothNull() {
        assertTrue(StringUtil.equalsIgnoreNull(null, null));
    }

    @Test
    public void testEqualsIgnoreNullNullAndEmpty() {
        assertTrue(StringUtil.equalsIgnoreNull(null, ""));
        assertTrue(StringUtil.equalsIgnoreNull("", null));
    }

    @Test
    public void testEqualsIgnoreNullBothEmpty() {
        assertTrue(StringUtil.equalsIgnoreNull("", ""));
    }

    @Test
    public void testEqualsIgnoreNullEqualStrings() {
        assertTrue(StringUtil.equalsIgnoreNull("abc", "abc"));
    }

    @Test
    public void testEqualsIgnoreNullDifferentStrings() {
        assertFalse(StringUtil.equalsIgnoreNull("abc", "def"));
    }

    @Test
    public void testEqualsIgnoreNullNullAndNonEmpty() {
        assertFalse(StringUtil.equalsIgnoreNull(null, "abc"));
        assertFalse(StringUtil.equalsIgnoreNull("abc", null));
    }

    // -------------------------------------------------------------------------
    // valueOf
    // -------------------------------------------------------------------------

    @Test
    public void testValueOfNull() {
        assertEquals("null", StringUtil.valueOf(null));
    }

    @Test
    public void testValueOfObjectArray() {
        Object[] arr = new Object[] { "a", "b", "c" };
        String result = StringUtil.valueOf(arr);
        assertEquals("[a, b, c]", result);
    }

    @Test
    public void testValueOfEmptyArray() {
        Object[] arr = new Object[] {};
        String result = StringUtil.valueOf(arr);
        assertEquals("[]", result);
    }

    @Test
    public void testValueOfMap() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("key1", "val1");
        String result = StringUtil.valueOf(map);
        assertTrue("Map representation should contain key=value", result.contains("key1=val1"));
    }

    @Test
    public void testValueOfString() {
        assertEquals("hello", StringUtil.valueOf("hello"));
    }

    @Test
    public void testValueOfInteger() {
        assertEquals("42", StringUtil.valueOf(42));
    }
}
