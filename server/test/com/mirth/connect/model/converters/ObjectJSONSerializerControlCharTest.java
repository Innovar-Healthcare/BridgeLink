package com.mirth.connect.model.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ChannelSummary;

/**
 * IRT-1925: a channel carrying a C0 control character (e.g. STX / U+0002) in a script blanked the
 * entire WebAdmin channel list with a 500. XStream stores the character as a numeric reference that
 * XML 1.0 forbids; the strict StAX reader that {@link ObjectJSONSerializer} uses to build JSON then
 * rejected the whole payload. These tests exercise the JSON serializer over the full forbidden set
 * and the {@code _getSummary} shape (a full Channel embedded in a ChannelSummary).
 */
public class ObjectJSONSerializerControlCharTest {

    @BeforeClass
    public static void setup() throws Exception {
        try {
            ObjectXMLSerializer.getInstance().init(Version.getLatest().toString());
        } catch (Exception e) {
            // Ignore if it has already been initialized
        }
    }

    /** Every C0 control character XML 1.0 forbids: all below 0x20 except tab (0x09), LF (0x0A), CR (0x0D). */
    private static int[] forbiddenControlChars() {
        List<Integer> chars = new ArrayList<>();
        for (int c = 0x00; c <= 0x1F; c++) {
            if (c != 0x09 && c != 0x0A && c != 0x0D) {
                chars.add(c);
            }
        }
        int[] result = new int[chars.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = chars.get(i);
        }
        return result;
    }

    private static Channel channelWithScript(String id, String script) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setName("ctrl-char-channel");
        channel.setPreprocessingScript(script);
        return channel;
    }

    private static String serializeToJson(Object object) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectJSONSerializer.getInstance().serialize(object, baos);
        return baos.toString("UTF-8");
    }

    private static JsonObject parse(String json) {
        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(json.getBytes()))) {
            return reader.readObject();
        }
    }

    /**
     * AC1/AC2: each forbidden control character serializes to valid JSON (no 500) and survives the
     * JSON->Object round-trip unchanged -- the character is preserved, never silently stripped.
     */
    @Test
    public void testEachForbiddenControlCharSerializesAndRoundTrips() throws Exception {
        for (int c : forbiddenControlChars()) {
            String label = String.format("0x%02X", c);
            String script = "before" + (char) c + "after";
            String channelId = "11111111-1111-1111-1111-1111111111ff";

            String json = serializeToJson(channelWithScript(channelId, script));

            // The character must be a JSON \\u00NN escape, never a raw control byte (illegal in JSON text).
            assertTrue("raw control byte leaked into JSON for " + label, json.indexOf(c) < 0);
            assertTrue("missing JSON escape for " + label, json.toLowerCase().contains(String.format("\\u%04x", c)));

            // Valid, parseable JSON -- and the parser decodes the \\u00NN escape back to the character.
            JsonObject parsed = parse(json);
            String decodedScript = parsed.getJsonObject("channel").getString("preprocessingScript");
            assertEquals("script mangled for " + label, script, decodedScript);

            // Full round-trip back to a Channel restores the exact control character.
            Channel roundTripped = ObjectJSONSerializer.getInstance().deserialize(json, Channel.class);
            assertEquals("round-trip lost control char " + label, script, roundTripped.getPreprocessingScript());
        }
    }

    /**
     * Tab and LF are legal XML and must pass through untouched -- this guards against over-scrubbing
     * the allowed control characters. CR is legal too, but the XML reader applies standard end-of-line
     * normalization (a lone CR becomes LF); that is a pre-existing XML behavior independent of this
     * fix, and is asserted here so the distinction stays documented.
     */
    @Test
    public void testAllowedControlCharsNotOverScrubbed() throws Exception {
        // Tab and LF survive verbatim.
        String tabLf = "a\tb\nc";
        Channel tabLfChannel = ObjectJSONSerializer.getInstance().deserialize(
                serializeToJson(channelWithScript("22222222-2222-2222-2222-222222222222", tabLf)), Channel.class);
        assertEquals(tabLf, tabLfChannel.getPreprocessingScript());

        // A lone CR is not scrubbed to a sentinel or dropped; XML EOL normalization turns it into LF.
        String withCr = "c\rd";
        Channel crChannel = ObjectJSONSerializer.getInstance().deserialize(
                serializeToJson(channelWithScript("33333333-3333-3333-3333-333333333333", withCr)), Channel.class);
        assertEquals("c\nd", crChannel.getPreprocessingScript());
    }

    /**
     * AC1: the WebAdmin path. A List&lt;ChannelSummary&gt; embeds a full Channel via ChannelStatus,
     * exactly as DefaultChannelController.getChannelSummary does. A single channel carrying a control
     * character must not blank the whole list -- the good channel still renders, and the bad one's
     * character survives the round-trip.
     */
    @Test
    public void testGetSummaryPathNotBlankedByControlCharChannel() throws Exception {
        String badId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String goodId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        String badScript = "hl7start" + (char) 0x02 + "mllpend" + (char) 0x1C;

        ChannelSummary badSummary = new ChannelSummary(badId);
        badSummary.getChannelStatus().setChannel(channelWithScript(badId, badScript));

        ChannelSummary goodSummary = new ChannelSummary(goodId);
        goodSummary.getChannelStatus().setChannel(channelWithScript(goodId, "return message;"));

        List<ChannelSummary> summaries = new ArrayList<>();
        summaries.add(badSummary);
        summaries.add(goodSummary);

        String json = serializeToJson(summaries);

        // Whole payload is valid JSON -- not a 500 -- and both channels are present.
        parse(json);
        assertTrue("bad channel missing from summary list", json.contains(badId));
        assertTrue("good channel blanked by the bad one", json.contains(goodId));

        // The embedded control characters round-trip through the list path too.
        List<ChannelSummary> roundTripped = ObjectJSONSerializer.getInstance().deserializeList(json, ChannelSummary.class);
        assertEquals(2, roundTripped.size());
        String restoredScript = roundTripped.get(0).getChannelStatus().getChannel().getPreprocessingScript();
        assertEquals(badScript, restoredScript);
    }

    /**
     * Genuine Private-Use-Area content (which XStream writes raw, not as a reference) must survive
     * untouched when a payload carries no forbidden control character -- the reverse pass only runs
     * when the scrub actually changed something, so the sentinel range is not corrupted here.
     */
    @Test
    public void testGenuinePrivateUseAreaContentPreserved() throws Exception {
        String script = "icon\ue005glyph";
        String json = serializeToJson(channelWithScript("cccccccc-cccc-cccc-cccc-cccccccccccc", script));
        Channel roundTripped = ObjectJSONSerializer.getInstance().deserialize(json, Channel.class);
        assertEquals(script, roundTripped.getPreprocessingScript());
    }

    // ---- Direct helper coverage (finding 4): the trickiest logic in the diff, pinned so a future
    // "simplification" that drops it goes red. Helpers are package-private in ObjectJSONSerializer. ----

    private static char sentinel(int value) {
        return (char) (0xE000 + value);
    }

    @Test
    public void testScrubXmlRefFormsHexDecimalZeroPaddedAndOverflow() {
        // Hex, decimal and zero-padded references to the same forbidden char all map to one sentinel.
        assertEquals(String.valueOf(sentinel(0x02)), ObjectJSONSerializer.scrubXmlControlCharRefs("&#x2;"));
        assertEquals(String.valueOf(sentinel(0x02)), ObjectJSONSerializer.scrubXmlControlCharRefs("&#2;"));
        assertEquals(String.valueOf(sentinel(0x02)), ObjectJSONSerializer.scrubXmlControlCharRefs("&#x0002;"));
        // Allowed refs and refs >= 0x20 are left exactly as-is (same string reference back).
        String allowed = "a&#x9;b&#xA;c&#xD;d&#x20;e";
        assertSame(allowed, ObjectJSONSerializer.scrubXmlControlCharRefs(allowed));
        // An overflowing reference is not a control character; NumberFormatException leaves it untouched.
        String overflow = "x&#x100000000000000000000;y";
        assertSame(overflow, ObjectJSONSerializer.scrubXmlControlCharRefs(overflow));
    }

    @Test
    public void testScrubJsonBackslashParity() {
        // A real escape (odd run of backslashes) is scrubbed to a sentinel.
        assertEquals("a" + sentinel(0x02) + "b", ObjectJSONSerializer.scrubJsonControlChars("a\\u0002b"));
        // Author-literal text: an escaped backslash then the characters "u0002" (even run) is NOT an
        // escape and must be left alone -- e.g. a JS source string like  var stx = "\\u0002";
        String literal = "a\\\\u0002b";
        assertSame(literal, ObjectJSONSerializer.scrubJsonControlChars(literal));
        // Escaped backslash followed by a real escape: only the real escape is scrubbed.
        assertEquals("a\\\\" + sentinel(0x02) + "b", ObjectJSONSerializer.scrubJsonControlChars("a\\\\\\u0002b"));
        // A raw control byte in the JSON text is scrubbed too.
        assertEquals("a" + sentinel(0x02) + "b", ObjectJSONSerializer.scrubJsonControlChars("a\u0002b"));
    }

    @Test
    public void testScrubJsonShorthandEscapes() {
        // \\b (0x08) and \\f (0x0C) are the forbidden shorthands and must be scrubbed to sentinels.
        assertEquals("x" + sentinel(0x08) + "y", ObjectJSONSerializer.scrubJsonControlChars("x\\by"));
        assertEquals("x" + sentinel(0x0C) + "y", ObjectJSONSerializer.scrubJsonControlChars("x\\fy"));
        // The allowed shorthands \\t \\n \\r map to 0x09/0x0A/0x0D and must be left untouched.
        String allowed = "a\\tb\\nc\\rd";
        assertSame(allowed, ObjectJSONSerializer.scrubJsonControlChars(allowed));
        // Backslash parity holds for shorthands: an escaped backslash then a literal 'b' is not \\b.
        String literalBackslashB = "a\\\\b";
        assertSame(literalBackslashB, ObjectJSONSerializer.scrubJsonControlChars(literalBackslashB));
    }

    /**
     * F3: WebUI's JSON.parse -> JSON.stringify re-emits 0x08/0x0C as the \\b / \\f shorthands, so a save
     * of such a channel arrives with shorthands rather than \\u0008. The inbound path must still
     * preserve the characters. Simulated by rewriting the escapes a serializer produced.
     */
    @Test
    public void testInboundShorthandEscapesRoundTrip() throws Exception {
        String script = "x" + (char) 0x08 + "y" + (char) 0x0C + "z";
        String json = serializeToJson(channelWithScript("44444444-4444-4444-4444-444444444444", script));
        // Emulate a WebUI re-save: the long escapes become shorthands.
        String reSaved = json.replace("\\u0008", "\\b").replace("\\u000c", "\\f");
        Channel roundTripped = ObjectJSONSerializer.getInstance().deserialize(reSaved, Channel.class);
        assertEquals(script, roundTripped.getPreprocessingScript());
    }

    @Test
    public void testUnscrubDirections() {
        // XML->JSON write side: sentinel -> 6-char JSON escape.
        assertEquals("a\\u0002b", ObjectJSONSerializer.unscrubJsonControlChars("a" + sentinel(0x02) + "b"));
        // JSON->XML write side: sentinel -> XStream-style lowercase hex reference (MXParser round-trips it).
        assertEquals("a&#x2;b&#xb;c", ObjectJSONSerializer.unscrubXmlControlCharRefs("a" + sentinel(0x02) + "b" + sentinel(0x0B) + "c"));
    }
}
