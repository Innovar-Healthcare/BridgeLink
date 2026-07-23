package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * D-08 three-level assertions for the five native/simple-pump channels — HTTP, MLLP, File, VM,
 * JS (plan 18-07 Task 1). Consumes 18-06's {@link RestClient}/{@link Hl7Messages} contracts and
 * 18-05's committed channel fixtures ({@code http-test.xml}, {@code tcp-mllp-test.xml},
 * {@code file-test.xml}, {@code vm-test.xml}, {@code js-test.xml}). Only executes meaningfully
 * against a live harness (smoke-tests/run-smoke-test.sh) — see {@link SmokeTestBase} for the
 * required system properties.
 *
 * <p>All applicable messages are pumped once, up front, in {@link #pumpAll()} — so the five
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
        // its own embedded script that returns a canned HL7v2 message — it auto-generates and
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
        // ORU_R01_CR already uses real \r segment separators — send_mllp.py's `\n`-literal
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
}
