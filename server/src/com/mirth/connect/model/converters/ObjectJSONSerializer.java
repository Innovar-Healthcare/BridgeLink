package com.mirth.connect.model.converters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.donkey.util.xstream.SerializerException;
import com.mirth.connect.util.JsonXmlUtil;

public class ObjectJSONSerializer {

    private static final ObjectJSONSerializer instance = new ObjectJSONSerializer();

    private Logger logger = LogManager.getLogger(getClass());

    public static ObjectJSONSerializer getInstance() {
        return instance;
    }

    // Object -> XML -> JSON
    public void serialize(Object object, OutputStream outputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter xmlWriter = new OutputStreamWriter(baos, "UTF-8");
        ObjectXMLSerializer.getInstance().serialize(object, xmlWriter);

        String xmlString = baos.toString();
        String jsonString = "";

        // Our xml util xml to json
        try {
            // IRT-1925: neutralize forbidden C0 control-character references before the strict StAX
            // reader in xmlToJson rejects them, then restore them as JSON escapes. Only when the scrub
            // actually changed something do we run the reverse pass (see the control-char note below).
            String scrubbed = scrubXmlControlCharRefs(xmlString);
            jsonString = JsonXmlUtil.xmlToJson(scrubbed);
            if (scrubbed != xmlString) {
                jsonString = unscrubJsonControlChars(jsonString);
            }
        } catch (Exception e) {
            logger.error(e);
            throw new SerializerException(e);
        }

        outputStream.write(jsonString.getBytes());
    }

    /* Converts a source JSON string to XML then calls ObjectXMLSerializer.deserialize(...)
     * JSON -> XML -> Object
     */
    public <T> T deserialize(String serializedObject, Class<T> expectedClass) {
        String xmlSerializedObject = "";

        // Our json -> xml
        try {
            xmlSerializedObject = jsonToXmlPreservingControlChars(serializedObject);
        } catch (Exception e) {
            logger.error(e);
            throw new SerializerException(e);
        }

        return ObjectXMLSerializer.getInstance().deserialize(xmlSerializedObject, expectedClass);
    }

    /**
     * Converts a source JSON string to XML then calls ObjectXMLSerializer.deserializeList(...).
     * JSON -> XML -> Object
     */
    public <T> List<T> deserializeList(String serializedObject, Class<T> expectedListItemClass) {
        String xmlSerializedObject = "";

        // Our json -> xml
        try {
            xmlSerializedObject = jsonToXmlPreservingControlChars(serializedObject);
        } catch (Exception e) {
            logger.error(e);
            throw new SerializerException(e);
        }

        return ObjectXMLSerializer.getInstance().deserializeList(xmlSerializedObject, expectedListItemClass);
    }

    /**
     * JSON -> XML for the inbound deserialize paths, IRT-1925: scrub forbidden control characters to
     * sentinels before the conversion and translate them back to XStream-style references afterward,
     * but only when the scrub actually changed the input (see the control-char note below). Shared by
     * deserialize and deserializeList so the two cannot drift.
     */
    private static String jsonToXmlPreservingControlChars(String serializedObject) throws Exception {
        String scrubbed = scrubJsonControlChars(serializedObject);
        String xml = JsonXmlUtil.jsonToXml(scrubbed);
        return scrubbed != serializedObject ? unscrubXmlControlCharRefs(xml) : xml;
    }

    /*
     * IRT-1925: round-trip-safe handling of C0 control characters across the XML<->JSON boundary.
     *
     * XStream serializes any ISO control character as a numeric character reference (STX -> "&#x2;").
     * XML 1.0 only permits references to 0x09/0x0A/0x0D within the C0 range, so every other one is
     * malformed XML. XStream's own lenient read path (MXParser) silently decodes it back, so a channel
     * carrying such a character deploys and runs -- but ObjectJSONSerializer converts XML to JSON with a
     * strict StAX reader (in JsonXmlUtil.xmlToJson) that rejects the reference and fails the whole JSON
     * payload. A single bad channel therefore blanked the entire WebAdmin channel list with a 500.
     *
     * We cannot decode the reference to the literal control byte: the strict reader rejects a raw 0x02
     * just as hard. Instead each forbidden reference is swapped for a Private-Use-Area sentinel
     * (U+E000 + value), a valid BMP XML character, and translated back after the conversion -- to a JSON
     * \\u00NN escape on the XML->JSON path, and to an XStream-style &#xN; reference on the JSON->XML
     * path. The mapping is a pure round-trip: control characters are preserved, never stripped (an
     * author may intentionally carry an STX or an MLLP framing byte).
     *
     * WHY HERE AND NOT IN JsonXmlUtil: JsonXmlUtil is also the engine behind the script-facing
     * userutil.XmlUtil.toJson / userutil.JsonUtil.fromXml, which convert arbitrary customer *message*
     * content on the data path. Scrubbing there would silently rewrite literal "&#2;" text inside a
     * CDATA section of a message -- data mutation in the highest-consequence code. The bug is confined
     * to object serialization for the REST API, which every JSON response reaches through this class
     * (JsonMessageBodyWriter -> serialize; the deserialize/deserializeList inbound path), so the fix
     * lives here and the message path is left exactly as it was.
     *
     * Two safeties: (1) the caller only runs the reverse pass when the scrub actually changed the
     * string, so genuine Private-Use-Area content (which XStream writes raw, never as a reference)
     * passes through untouched on any payload that carries no forbidden control character. (2) Scrubbing
     * is free of false positives on XStream output: XStream escapes every literal '&' to "&amp;", so a
     * bare "&#x2;" here is always an encoded control character, never author text. KNOWN LIMITATION: a
     * single payload that carries BOTH a forbidden control character AND genuine U+E000..U+E01F content
     * would have that content rewritten to the matching control character. This is vanishingly unlikely
     * (32 private-use codepoints) and is tracked separately rather than blocking this fix.
     */

    private static final int PUA_SENTINEL_BASE = 0xE000;

    // Matches an XML numeric character reference: hex (&#xN;) or decimal (&#N;), any length.
    private static final Pattern XML_CONTROL_CHAR_REF = Pattern.compile("&#(x?)([0-9A-Fa-f]+);");

    // Matches a JSON escape that can encode a forbidden control character: the \\uNNNN long form, or
    // the \\b (0x08) / \\f (0x0C) shorthands. JSON's other control shorthands \\t \\n \\r are the
    // allowed characters and are deliberately not matched. WebUI's JSON.stringify re-emits 0x08/0x0C as
    // the shorthands, so an inbound save takes this branch, not the \\uNNNN one.
    private static final Pattern JSON_CONTROL_ESCAPE = Pattern.compile("\\\\(u[0-9A-Fa-f]{4}|b|f)");

    /** C0 control characters that XML 1.0 forbids -- every one below 0x20 except tab, LF and CR. */
    static boolean isForbiddenXmlControlChar(int value) {
        return value >= 0x00 && value <= 0x1F && value != 0x09 && value != 0x0A && value != 0x0D;
    }

    /**
     * XML -> JSON, read side. Replaces every numeric character reference to a forbidden C0 control
     * character with a BMP Private-Use-Area sentinel so the strict StAX reader accepts the document.
     * References the reader already accepts (0x09/0x0A/0x0D and anything >= 0x20) are left intact.
     * Returns the original string reference unchanged when nothing forbidden was found.
     */
    static String scrubXmlControlCharRefs(String xml) {
        if (xml == null || xml.indexOf("&#") < 0) {
            return xml;
        }
        Matcher matcher = XML_CONTROL_CHAR_REF.matcher(xml);
        StringBuffer sb = null;
        while (matcher.find()) {
            int radix = matcher.group(1).isEmpty() ? 10 : 16;
            int value = -1;
            try {
                value = Integer.parseInt(matcher.group(2), radix);
            } catch (NumberFormatException e) {
                // Reference too long to be a control character; leave it untouched.
            }
            if (isForbiddenXmlControlChar(value)) {
                if (sb == null) {
                    sb = new StringBuffer(xml.length());
                }
                // Non-forbidden refs seen before this point are copied verbatim by appendReplacement.
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) (PUA_SENTINEL_BASE + value))));
            }
        }
        if (sb == null) {
            return xml;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * XML -> JSON, write side. Translates the Private-Use-Area sentinels back into JSON \\u00NN escapes.
     * The raw control byte is illegal in JSON text, so we emit the 6-character escape; a JSON parser
     * (e.g. WebUI's JSON.parse) decodes it back to the original control character.
     */
    static String unscrubJsonControlChars(String json) {
        if (json == null) {
            return json;
        }
        StringBuilder sb = null;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c >= PUA_SENTINEL_BASE && c <= PUA_SENTINEL_BASE + 0x1F) {
                if (sb == null) {
                    sb = new StringBuilder(json.length() + 16);
                    sb.append(json, 0, i);
                }
                sb.append(String.format("\\u%04x", c - PUA_SENTINEL_BASE));
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? json : sb.toString();
    }

    /**
     * JSON -> XML, read side. Replaces forbidden C0 control characters -- whether they arrive as a
     * \\u00NN JSON escape or as a raw byte -- with a Private-Use-Area sentinel before the value reaches
     * the XML writer, which would otherwise emit an unparseable raw control byte into the XML. Returns
     * the original string reference unchanged when nothing forbidden was found.
     */
    static String scrubJsonControlChars(String json) {
        if (json == null) {
            return json;
        }

        // Phase 1: raw control characters (defensive; well-formed JSON escapes them).
        StringBuilder rawScrubbed = null;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (isForbiddenXmlControlChar(c)) {
                if (rawScrubbed == null) {
                    rawScrubbed = new StringBuilder(json.length());
                    rawScrubbed.append(json, 0, i);
                }
                rawScrubbed.append((char) (PUA_SENTINEL_BASE + c));
            } else if (rawScrubbed != null) {
                rawScrubbed.append(c);
            }
        }
        String work = rawScrubbed == null ? json : rawScrubbed.toString();

        // Phase 2: JSON escapes of forbidden characters (\\u00NN long form and the \\b / \\f shorthands).
        if (work.indexOf('\\') < 0) {
            return work;
        }
        Matcher matcher = JSON_CONTROL_ESCAPE.matcher(work);
        StringBuffer sb = null;
        while (matcher.find()) {
            String token = matcher.group(1);
            int value = token.charAt(0) == 'u' ? Integer.parseInt(token.substring(1), 16)
                    : (token.charAt(0) == 'b' ? 0x08 : 0x0C);
            // Only replace a real escape: an odd run of backslashes ending at the match means this
            // backslash escapes the token; an even run means the token is literal author text.
            if (isForbiddenXmlControlChar(value) && precededByOddBackslashRun(work, matcher.start())) {
                if (sb == null) {
                    sb = new StringBuffer(work.length());
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) (PUA_SENTINEL_BASE + value))));
            }
        }
        if (sb == null) {
            return work;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // backslashIndex points at the leading backslash the regex matched. Count the contiguous run of
    // backslashes ending there; an odd count means it is a real escape introducer.
    static boolean precededByOddBackslashRun(String s, int backslashIndex) {
        int count = 0;
        for (int i = backslashIndex; i >= 0 && s.charAt(i) == '\\'; i--) {
            count++;
        }
        return (count % 2) == 1;
    }

    /**
     * JSON -> XML, write side. Translates the Private-Use-Area sentinels into XStream-style &#xN;
     * references -- the exact form MXParser decodes back to the original control character on
     * deserialize, matching how the value was stored to begin with.
     */
    static String unscrubXmlControlCharRefs(String xml) {
        if (xml == null) {
            return xml;
        }
        StringBuilder sb = null;
        for (int i = 0; i < xml.length(); i++) {
            char c = xml.charAt(i);
            if (c >= PUA_SENTINEL_BASE && c <= PUA_SENTINEL_BASE + 0x1F) {
                if (sb == null) {
                    sb = new StringBuilder(xml.length() + 16);
                    sb.append(xml, 0, i);
                }
                sb.append("&#x").append(Integer.toHexString(c - PUA_SENTINEL_BASE)).append(';');
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? xml : sb.toString();
    }
}
