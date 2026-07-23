package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * Shared base for the smoke-test harness's per-channel assertion JUnit classes (plan 18-07).
 *
 * <p>Reads the ports/paths {@code run-smoke-test.sh} passes as {@code -D} system properties
 * (Task 3), logs into the REST API once per test class, and provides:
 * <ul>
 *   <li>the D-08 three-level assertion helper ({@link #assertThreeLevels}) — L1 (zero
 *       ERROR-status + minimum SENT count via {@link RestClient}), L2 (caller-supplied
 *       destination-artifact content check). L3 (the mirth.log ERROR scan) is script-side,
 *       in {@code run-smoke-test.sh}, not here.</li>
 *   <li>poll-with-timeout helpers ({@link #pollUntil}, {@link #pollForFile}) — Pitfall 7:
 *       no fixed multi-second sleeps anywhere in this harness.</li>
 *   <li>a stub lifecycle registry ({@link #registerStubStop}) so {@code @AfterClass} always
 *       stops whatever {@code @BeforeClass} started, even if a test fails (T-18-13).</li>
 * </ul>
 */
public abstract class SmokeTestBase {

    protected static RestClient rest;

    protected static String httpsPort;
    protected static String httpListenerPort;
    protected static String mllpPort;
    protected static String inDir;
    protected static String outDir;
    protected static String sqlitePath;
    protected static String smtpPort;
    protected static String soapUrl;
    protected static String scpPort;

    private static final List<Runnable> STUB_STOPPERS = new ArrayList<>();

    @BeforeClass
    public static void baseSetUp() throws Exception {
        httpsPort = requireProperty("HTTPS_PORT");
        httpListenerPort = requireProperty("HTTP_LISTENER_PORT");
        mllpPort = requireProperty("MLLP_PORT");
        inDir = requireProperty("IN_DIR");
        outDir = requireProperty("OUT_DIR");
        sqlitePath = requireProperty("SQLITE_PATH");
        smtpPort = requireProperty("SMTP_PORT");
        soapUrl = requireProperty("SOAP_URL");
        scpPort = requireProperty("SCP_PORT");

        rest = new RestClient("https://127.0.0.1:" + httpsPort + "/api");
        rest.login("admin", "admin");
    }

    @AfterClass
    public static void baseTearDown() {
        // Stop stubs in reverse-registration (LIFO) order; one failure must never block
        // the rest from stopping (T-18-13 — cleanup must be unconditional).
        for (int i = STUB_STOPPERS.size() - 1; i >= 0; i--) {
            try {
                STUB_STOPPERS.get(i).run();
            } catch (Exception e) {
                System.err.println("Stub stop failed (continuing cleanup): " + e);
            }
        }
        STUB_STOPPERS.clear();
    }

    /** Registers a stub-stop action to run (LIFO) during {@code @AfterClass}. */
    protected static void registerStubStop(Runnable stop) {
        STUB_STOPPERS.add(stop);
    }

    private static String requireProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required system property '" + name + "' not set. "
                    + "These per-channel assertion tests only run against a live harness — invoke via "
                    + "smoke-tests/run-smoke-test.sh, which passes harness ports/paths as -D properties "
                    + "to `ant -f smoke-tests/build.xml test-run` (Task 3).");
        }
        return value;
    }

    /**
     * D-08 three-level assertion helper for a single channel:
     * <ul>
     *   <li>L1 — zero ERROR-status messages, and at least {@code minSent} SENT messages.</li>
     *   <li>L2 — caller-supplied destination-artifact content check (never byte-exact).</li>
     * </ul>
     * (L3 — the mirth.log ERROR scan — is script-side in run-smoke-test.sh, not here.)
     *
     * <p>L1 is poll-with-timeout, not a one-shot check (Pitfall 7 / Rule 1 fix): message
     * processing is asynchronous ({@code ExecuteType.ASYNC} on the REST injection endpoint —
     * {@code processMessage}/{@code processMessageBytes} return as soon as the message is
     * accepted, before the channel finishes processing it), so a one-shot statistics read
     * immediately after pumping raced the server and produced false "sent count 0" failures.
     */
    protected static void assertThreeLevels(String channelId, int minSent, ThrowingRunnable artifactCheck) throws Exception {
        pollUntil("Channel " + channelId + " sent count >= " + minSent, 30, () -> {
            try {
                return rest.getSentCount(channelId) >= minSent;
            } catch (Exception e) {
                return false;
            }
        });

        long errorCount = rest.getErrorCount(channelId);
        assertEquals("Channel " + channelId + " has ERROR-status messages", 0, errorCount);

        long sentCount = rest.getSentCount(channelId);
        assertTrue("Channel " + channelId + " expected sent count >= " + minSent + ", was " + sentCount,
                sentCount >= minSent);

        artifactCheck.run();
    }

    /** A {@link Runnable}-shaped lambda target that is allowed to throw checked exceptions. */
    @FunctionalInterface
    protected interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Polls until {@code condition} is true or {@code timeoutSeconds} elapses (Pitfall 7: no fixed sleeps). */
    protected static void pollUntil(String description, int timeoutSeconds, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError(description + " did not become true within " + timeoutSeconds + "s");
    }

    /** Polls (no fixed sleep) for a non-empty file to appear at {@code path}, up to {@code timeoutSeconds}. */
    protected static Path pollForFile(Path path, int timeoutSeconds) throws InterruptedException {
        pollUntil("File " + path, timeoutSeconds, () -> Files.exists(path) && path.toFile().length() > 0);
        return path;
    }

    protected static String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
