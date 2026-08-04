/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.userutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.ParseException;
import org.apache.http.message.BasicLineParser;
import org.apache.http.util.CharArrayBuffer;
import org.junit.Test;

/**
 * CVE-06/D-05 byte-identical equivalence suite for {@link HTTPUtil#parseHeaders(String)}.
 * <p>
 * {@code HTTPUtil.parseHeaders(String)->Map<String,String>} is a customer-scriptable API (channel
 * JavaScript calls it directly). Its PUBLIC signature and behavior must stay identical after the
 * internal swap from commons-httpclient's {@code HttpParser.parseHeaders} to
 * {@code org.apache.http.message.BasicLineParser}. commons-httpclient's parser unfolded RFC-822
 * continuation lines (a line beginning with a space/tab is a continuation of the previous
 * header's value) -- this is the subtle, load-bearing behavior a naive per-line replacement could
 * silently drop (22-RESEARCH.md Pitfall 4). Each case below asserts the returned {@code Map} is
 * exactly what the pre-swap parser would have produced for the same raw header block.
 */
public class HttpUtilParseHeadersTest {

    @Test
    public void testSingleHeader() throws Exception {
        String raw = "Content-Type: text/plain\r\n";

        Map<String, String> expected = new HashMap<String, String>();
        expected.put("Content-Type", "text/plain");

        assertEquals(expected, HTTPUtil.parseHeaders(raw));
    }

    @Test
    public void testDuplicateHeaderNames() throws Exception {
        // commons-httpclient's HttpParser.parseHeaders returned an array (not deduplicated); the
        // pre-swap HTTPUtil.parseHeaders assembled the Map by sequential Map.put(), so the LAST
        // occurrence of a duplicate header name wins. The replacement must reproduce that.
        String raw = "X-Custom: first\r\nX-Custom: second\r\n";

        Map<String, String> expected = new HashMap<String, String>();
        expected.put("X-Custom", "second");

        assertEquals(expected, HTTPUtil.parseHeaders(raw));
    }

    @Test
    public void testFoldedContinuationLine() throws Exception {
        // LOAD-BEARING CASE (Pitfall 4): "X-Folded: firstpart" followed by a leading-whitespace
        // continuation line " secondpart" is ONE logical header whose value is
        // "firstpart secondpart" -- RFC-822 unfolds a folded header by joining the continuation
        // with a single space. commons-httpclient's HttpParser did this unfolding; the
        // BasicLineParser-based replacement must reproduce it explicitly (BasicLineParser itself
        // has no folding awareness -- HTTPUtil.unfoldHeaderLines performs the join beforehand).
        String raw = "X-Folded: firstpart\r\n secondpart\r\n";

        Map<String, String> expected = new HashMap<String, String>();
        expected.put("X-Folded", "firstpart secondpart");

        assertEquals(expected, HTTPUtil.parseHeaders(raw));
    }

    @Test
    public void testFoldedContinuationLineWithTabIndent() throws Exception {
        // Continuation lines may also be indented with a tab (RFC 822 permits either SP or TAB).
        String raw = "X-Folded: firstpart\r\n\tsecondpart\r\n";

        Map<String, String> expected = new HashMap<String, String>();
        expected.put("X-Folded", "firstpart secondpart");

        assertEquals(expected, HTTPUtil.parseHeaders(raw));
    }

    @Test
    public void testEmptyValue() throws Exception {
        String raw = "X-Empty:\r\n";

        Map<String, String> expected = new HashMap<String, String>();
        expected.put("X-Empty", "");

        assertEquals(expected, HTTPUtil.parseHeaders(raw));
    }

    @Test
    public void testRepresentativeMultiHeaderBlock() throws Exception {
        // A realistic raw header block mixing all of the above cases in one pass.
        String raw = "Content-Type: text/plain\r\n" + "X-Custom: first\r\n" + "X-Custom: second\r\n" + "X-Folded: firstpart\r\n" + " secondpart\r\n" + "X-Empty:\r\n";

        Map<String, String> expected = new HashMap<String, String>();
        expected.put("Content-Type", "text/plain");
        expected.put("X-Custom", "second");
        expected.put("X-Folded", "firstpart secondpart");
        expected.put("X-Empty", "");

        assertEquals(expected, HTTPUtil.parseHeaders(raw));
    }

    @Test
    public void testHeaderBlockFollowedByBodyContent() throws Exception {
        // REGRESSION (WR-01): commons-httpclient's HttpParser.parseHeaders read header lines only
        // until the first blank line, treating it as the header/body boundary terminator --
        // standard HTTP semantics. A naive replacement that iterates every line and merely skips
        // blank ones (rather than stopping at the first one) would keep feeding the trailing
        // non-header content to BasicLineParser, which throws ParseException on any line lacking
        // a ':'. Assert the parser stops at the blank line and returns only the preceding headers.
        String raw = "A: b\r\n\r\nnot-a-header\r\n";

        Map<String, String> expected = new HashMap<String, String>();
        expected.put("A", "b");

        assertEquals(expected, HTTPUtil.parseHeaders(raw));
    }

    /**
     * Falsifiability of the load-bearing folding case (D-05 acceptance criterion): proves that
     * SKIPPING the RFC-822 unfold step -- i.e. feeding each raw line independently to
     * {@code BasicLineParser} without joining continuation lines first, which is exactly what a
     * naive per-line replacement of {@code HttpParser.parseHeaders} would do -- breaks on a
     * folded header. The continuation line " secondpart" has no colon, so
     * {@code BasicLineParser.parseHeader} cannot split it into a name/value pair and throws
     * {@link ParseException}. This demonstrates the folding case in
     * {@link #testFoldedContinuationLine()} is genuinely load-bearing: removing
     * {@code HTTPUtil.unfoldHeaderLines}'s join step would turn that test red, not leave it
     * silently passing.
     */
    @Test
    public void testFoldingIsLoadBearing_naiveUnfoldedParseFails() {
        String raw = "X-Folded: firstpart\r\n secondpart\r\n";
        String continuationLine = " secondpart";

        CharArrayBuffer buffer = new CharArrayBuffer(continuationLine.length());
        buffer.append(continuationLine);

        try {
            BasicLineParser.INSTANCE.parseHeader(buffer);
            fail("Expected BasicLineParser to fail on a bare RFC-822 continuation line " + "(no colon) when unfolding is skipped -- this is what proves the fold " + "join in HTTPUtil.parseHeaders is load-bearing, not incidental.");
        } catch (ParseException e) {
            // Expected: without the unfold step, the continuation line cannot be parsed as a
            // standalone header, confirming the fold-join is required for testFoldedContinuationLine()
            // to pass. (raw block retained above for readability of what would otherwise be fed in.)
        }
    }
}
