package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonValue;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * D-08 three-level assertions for the five native/simple-pump channels - HTTP, MLLP, File, VM,
 * JS (plan 18-07 Task 1). Consumes 18-06's {@link RestClient}/{@link Hl7Messages} contracts and
 * 18-05's committed channel fixtures ({@code http-test.xml}, {@code tcp-mllp-test.xml},
 * {@code file-test.xml}, {@code vm-test.xml}, {@code js-test.xml}). Only executes meaningfully
 * against a live harness (smoke-tests/run-smoke-test.sh) - see {@link SmokeTestBase} for the
 * required system properties.
 *
 * <p>All applicable messages are pumped once, up front, in {@link #pumpAll()} - so the five
 * channels' server-side processing overlaps in wall-clock time (D-04 budget) instead of each
 * {@code @Test} method pumping-then-waiting serially. Each {@code @Test} method then only polls
 * for its own channel's already-in-flight output and asserts (D-08's three levels).
 */
public class NativePumpChannelsTest extends SmokeTestBase {

    private static final String HTTP_CHANNEL_ID = "00000001-0000-0000-0000-000000000001";
    private static final String MLLP_CHANNEL_ID = "00000002-0000-0000-0000-000000000002";
    private static final String FILE_CHANNEL_ID = "00000003-0000-0000-0000-000000000003";
    private static final String VM_CHANNEL_ID = "00000005-0000-0000-0000-000000000005";
    private static final String JS_CHANNEL_ID = "00000006-0000-0000-0000-000000000006";

    private static volatile int mllpAckExitCode = -1;

    @BeforeClass
    public static void pumpAll() throws Exception {
        pumpHttp();
        pumpMllp();
        pumpFile();
        pumpVm();
        // JS: js-test.xml's JavaScript Reader has pollOnStart=true + pollingFrequency=3000ms and
        // its own embedded script that returns a canned HL7v2 message - it auto-generates and
        // processes a message the moment the channel started (during run-smoke-test.sh's
        // import/deploy stage, well before this driver runs), and keeps re-firing every 3s.
        // There is no separate "send" action for a poll-based Reader; the js() test below only
        // polls for the output its own auto-poll already produced (or will shortly produce).
    }

    private static void pumpHttp() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpListenerPort + "/"))
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(Hl7Messages.ORU_R01_LF, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue("HTTP pump expected a 2xx response, got " + response.statusCode(),
                response.statusCode() >= 200 && response.statusCode() < 300);
    }

    private static void pumpMllp() throws IOException, InterruptedException {
        // ORU_R01_CR already uses real \r segment separators - send_mllp.py's `\n`-literal
        // replacement is a no-op on this content; ProcessBuilder passes the argument verbatim
        // (no shell re-interpretation), so the \r bytes reach the script intact.
        ProcessBuilder pb = new ProcessBuilder("python3", "send_mllp.py", "127.0.0.1", mllpPort, Hl7Messages.ORU_R01_CR);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        mllpAckExitCode = finished ? process.exitValue() : -1;
        if (mllpAckExitCode != 0) {
            throw new AssertionError("send_mllp.py did not return an AA ack (exit=" + mllpAckExitCode + "): " + output);
        }
    }

    private static void pumpFile() throws IOException {
        Path fileIn = Paths.get(inDir, "file", UUID.randomUUID() + ".hl7");
        Files.write(fileIn, Hl7Messages.ORU_R01_LF.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void pumpVm() throws IOException, InterruptedException {
        rest.processMessage(VM_CHANNEL_ID, Hl7Messages.ORU_R01_LF);
    }

    @Test
    public void http() throws Exception {
        assertThreeLevels(HTTP_CHANNEL_ID, 1, () -> {
            Path out = pollForFile(Paths.get(outDir, "http", "output.hl7"), 60);
            String content = readFile(out);
            assertTrue("HTTP destination content should contain the transformed patient token",
                    content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    @Test
    public void mllp() throws Exception {
        assertEquals("send_mllp.py must have returned an AA ack (exit 0)", 0, mllpAckExitCode);
        assertThreeLevels(MLLP_CHANNEL_ID, 1, () -> {
            Path out = pollForFile(Paths.get(outDir, "mllp", "output.hl7"), 60);
            String content = readFile(out);
            assertTrue("MLLP destination content should contain the transformed patient token",
                    content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    @Test
    public void file() throws Exception {
        assertThreeLevels(FILE_CHANNEL_ID, 1, () -> {
            Path out = pollForFile(Paths.get(outDir, "file", "output.hl7"), 60);
            String content = readFile(out);
            assertTrue("File destination content should contain the transformed patient token",
                    content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    @Test
    public void vm() throws Exception {
        assertThreeLevels(VM_CHANNEL_ID, 1, () -> {
            Path out = pollForFile(Paths.get(outDir, "vm", "output.hl7"), 60);
            String content = readFile(out);
            assertTrue("VM destination content should contain the transformed patient token",
                    content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    @Test
    public void js() throws Exception {
        assertThreeLevels(JS_CHANNEL_ID, 1, () -> {
            Path out = pollForFile(Paths.get(outDir, "js", "output.hl7"), 60);
            String content = readFile(out);
            assertTrue("JS destination content should contain the transformed patient token, "
                    + "proving the Rhino transformer ran", content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    /**
     * IRT-2217 (Phase 26.12 plan 04, criterion 4): non-ASCII (0xE9) MLLP round trip through the
     * DEFAULT_ENCODING channel, proving the Default-encoding upgrade-safety behavior with and
     * without {@code server.defaultencoding = windows-1252} pinned. 0xE9 is the accented Latin
     * small letter e ("e" with acute, U+00E9) under windows-1252/latin-1, but an invalid lone
     * byte under UTF-8, where {@code new String(bytes, charset)} silently substitutes the
     * replacement character (U+FFFD) instead of throwing. That divergence is the asserted signal
     * (26.12-PATTERNS.md CRITERION 4 / 26.12-RESEARCH.md Pitfall 4). Empirically confirmed
     * live against a real DEFAULT_ENCODING channel this plan (both legs, fresh build).
     *
     * <p>Sends via the true MLLP wire ({@code send_mllp.py}'s latin-1 mode, added by Task 1 of
     * this plan) rather than the {@link RestClient#processMessageBytes} REST fallback -- the
     * TCP/MLLP fixture channel ({@code tcp-mllp-test.xml}) already sets
     * {@code charsetEncoding=DEFAULT_ENCODING} on both its source and destination, so no new
     * fixture is needed.
     *
     * <p>{@code SERVER_DEFAULT_ENCODING} (forwarded by Task 2's run-smoke-test.sh knob) selects
     * the assertion branch: {@code windows-1252} runs the pinned leg, anything else (including
     * absent, the normal case) runs the UTF-8-default leg. The patient-name token and both
     * divergent code points are written as Java unicode escapes, never literal non-ASCII
     * glyphs, keeping this source file ASCII-only.
     *
     * <p>Message identity is never assumed from array position, sent-count, or timing: pumpAll's
     * plain-ASCII {@link #mllp()} pump also targets this same channel and this class's
     * {@code @Test} methods have no guaranteed relative execution order, so a sent-count-based
     * "wait for count to increase" poll can be satisfied by that OTHER pump's message finishing
     * first (found live, this plan -- a sent-count poll picked up pumpAll's plain-ASCII message
     * instead of this test's own). Instead, this message carries a unique per-invocation MSH-10
     * control ID, and {@link #pollUntil} polls the message store directly for that ID's
     * appearance, then the response is scanned by content for the metaDataId-0 (source)
     * {@code connectorMessage} whose RAW content contains that ID -- deterministic regardless of
     * how many other messages the channel holds or in what order they were sent. The response's
     * {@code "message"} field is also normalized defensively: the server's JSON serializer
     * collapses it to a bare object instead of a one-element array when only one message
     * matches, the same single-vs-array ambiguity {@code RestClient} already defends against for
     * {@code /channels/statuses}.
     */
    @Test
    public void mllpNonAscii() throws Exception {
        String serverDefaultEncoding = System.getProperty("SERVER_DEFAULT_ENCODING");
        boolean pinnedWindows1252 = "windows-1252".equalsIgnoreCase(serverDefaultEncoding);

        // Synthetic patient name carrying the 0xE9-derived character; never customer PHI
        // (26.12-PATTERNS.md CRITERION 4 PHI note). U+00E9 latin-1-encodes to exactly the
        // 0xE9 byte send_mllp.py's --encoding latin-1 mode (Task 1) needs to frame it on the
        // wire. Expressed here as a unicode escape, never a literal glyph (ASCII-only source).
        String nonAsciiToken = "D\u00e9e";
        // Unique per invocation so message-store identification below cannot collide with any
        // other message on this channel (pumpAll's own plain-ASCII pump included).
        String controlId = "IRT2217E9" + (System.nanoTime() % 100000);
        String msg = "MSH|^~\\&|LABNET|Acme Labs|||20090601105700||ORU^R01|" + controlId + "|D|2.2\r"
                + "PID|1|8890088|8890088^^^72777||" + nonAsciiToken + "^Test||19350118|F\r";

        ProcessBuilder pb = new ProcessBuilder("python3", "send_mllp.py", "127.0.0.1", mllpPort, msg,
                "--encoding", "latin-1");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        int exitCode = finished ? process.exitValue() : -1;
        assertEquals("send_mllp.py --encoding latin-1 must have returned an AA ack (exit 0): " + output,
                0, exitCode);

        String[] messagesWithContentHolder = new String[1];
        pollUntil("MLLP channel message store contains control ID " + controlId, 30, () -> {
            try {
                String json = rest.getMessagesWithContent(MLLP_CHANNEL_ID);
                if (json.contains(controlId)) {
                    messagesWithContentHolder[0] = json;
                    return true;
                }
                return false;
            } catch (Exception e) {
                return false;
            }
        });

        JsonObject sourceConnectorMessage = sourceConnectorMessageByControlId(
                messagesWithContentHolder[0], controlId);

        String rawContent = sourceConnectorMessage.getJsonObject("raw").getString("content");
        String transformedContent = sourceConnectorMessage.getJsonObject("transformed").getString("content");

        if (pinnedWindows1252) {
            // Byte-exact round trip, defined relative to the pinned encoding: re-encoding the
            // stored RAW content via windows-1252 must reproduce the original 0xE9 byte.
            byte[] reencoded = rawContent.getBytes(Charset.forName("windows-1252"));
            assertTrue("RAW content re-encoded via windows-1252 should reproduce the original 0xE9 byte "
                    + "(byte-exact round trip); raw content was: " + rawContent,
                    containsByte(reencoded, (byte) 0xE9));
            assertTrue("Decoded content should contain the correctly-decoded accented e (U+00E9) when "
                    + "server.defaultencoding=windows-1252", transformedContent.contains("\u00e9"));
        } else {
            assertTrue("RAW content should show the lossy UTF-8 replacement character (U+FFFD) for a "
                    + "lone 0xE9 byte when server.defaultencoding is unset (UTF-8 default)",
                    rawContent.contains("\ufffd"));
            assertFalse("Decoded content should NOT contain the correctly-decoded accented e (U+00E9) "
                    + "when server.defaultencoding is unset -- a lone 0xE9 byte is invalid UTF-8",
                    transformedContent.contains("\u00e9"));
        }
    }

    /**
     * Parses {@code getMessagesWithContent}'s raw JSON body and returns the metaDataId-0
     * (source) {@code connectorMessage} object whose RAW content contains {@code controlId} --
     * never assumes array order, position, or recency (see {@link #mllpNonAscii} javadoc for
     * why a sent-count or "most recent" heuristic is not safe here).
     */
    private static JsonObject sourceConnectorMessageByControlId(String messagesWithContentJson, String controlId) {
        JsonStructure root = Json.createReader(new StringReader(messagesWithContentJson)).read();
        JsonObject list = ((JsonObject) root).getJsonObject("list");
        JsonArray messages = normalizeToArray(list == null ? null : list.get("message"));
        assertFalse("getMessagesWithContent should return at least one message, not an empty list",
                messages.isEmpty());

        for (JsonValue v : messages) {
            JsonObject messageObj = (JsonObject) v;
            JsonObject connectorMessages = messageObj.getJsonObject("connectorMessages");
            JsonArray entries = normalizeToArray(connectorMessages == null ? null : connectorMessages.get("entry"));
            for (JsonValue e : entries) {
                JsonObject entryObj = (JsonObject) e;
                if (entryObj.getInt("int") == 0) {
                    JsonObject connectorMessage = entryObj.getJsonObject("connectorMessage");
                    JsonObject raw = connectorMessage.getJsonObject("raw");
                    if (raw != null && raw.getString("content", "").contains(controlId)) {
                        return connectorMessage;
                    }
                }
            }
        }
        throw new AssertionError("No source (metaDataId 0) connectorMessage found whose RAW content "
                + "contains control ID " + controlId);
    }

    /**
     * Normalizes a JSON value that may be a bare object (single element) or a JSON array
     * (multiple elements) into an array -- the same single-vs-array ambiguity the REST API's
     * {@code /channels/statuses} endpoint has, defended against the same way here.
     */
    private static JsonArray normalizeToArray(JsonValue v) {
        if (v == null) {
            return Json.createArrayBuilder().build();
        }
        if (v.getValueType() == JsonValue.ValueType.ARRAY) {
            return (JsonArray) v;
        }
        return Json.createArrayBuilder().add(v).build();
    }

    private static boolean containsByte(byte[] bytes, byte target) {
        for (byte b : bytes) {
            if (b == target) {
                return true;
            }
        }
        return false;
    }
}
