package com.mirth.connect.smoketest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Live modern-SFTP verification for the File connector's jsch 2.28.5 transport (Phase
 * 25.1, plan 25.1-02, IRT-1541 / ROADMAP SC-2). Asserts read AND write end-to-end against
 * a pinned atmoz/sftp container (25.1-01's harness stage, {@code generate_sftp_fixtures()})
 * for every auth mode the acceptance criteria enumerate: password auth, key auth
 * (jsch's {@code addIdentity}), and known-hosts host-key verification.
 *
 * <p>Rides the same single server boot as {@link NativePumpChannelsTest}/
 * {@link HttpParamsTest} (JUnit's {@code batchtest} in {@code build.xml} auto-discovers
 * this class). All three seed files are written up front in {@link #seedAll()} — like
 * {@link NativePumpChannelsTest#pumpAll()} — so the three channels' SFTP polling cycles
 * overlap in wall-clock time instead of each {@code @Test} pumping-then-waiting serially.
 *
 * <p>All three fixture channels (modern/keyauth/knownhosts) share ONE SFTP server
 * directory ({@code upload/}, one atmoz/sftp container, one "smoke" user) — each fixture
 * uses a distinct input/output filename ({@code input-*.hl7}/{@code output-*.hl7}) so a
 * channel's own reader never re-processes its own writer's output or another fixture's
 * seed file (see {@code file-sftp-*-test.xml}'s {@code fileFilter}/{@code outputPattern}).
 */
public class SftpParamsTest extends SmokeTestBase {

    private static final String MODERN_PASSWORD_ID = "00000027-0000-0000-0000-000000000027";
    private static final String KEY_AUTH_ID = "00000028-0000-0000-0000-000000000028";
    private static final String KNOWN_HOSTS_ID = "00000029-0000-0000-0000-000000000029";
    private static final String LEGACY_NEGATIVE_ID = "00000030-0000-0000-0000-000000000030";
    private static final String LEGACY_WORKAROUND_ID = "00000031-0000-0000-0000-000000000031";

    private static String sftpModernPort;
    private static String sftpKeyPath;
    private static String sftpKnownHostsPath;
    private static String sftpUploadDir;
    /** 25.1-03 (SC-3, IRT-1541): empty when no external break-then-fix driver is running. */
    private static String sftpLegacyPort;
    private static String sftpLegacyUploadDir;
    private static String mirthLogPath;

    @BeforeClass
    public static void sftpParamsSetUp() throws Exception {
        sftpModernPort = requireSftpProperty("SFTP_MODERN_PORT");
        sftpKeyPath = requireSftpProperty("SFTP_KEY_PATH");
        sftpKnownHostsPath = requireSftpProperty("SFTP_KNOWN_HOSTS_PATH");
        sftpUploadDir = requireSftpProperty("SFTP_UPLOAD_DIR");
        // Deliberately NOT requireSftpProperty: both may be legitimately empty (ordinary
        // harness runs with no external legacy driver) — the two legacy @Test methods below
        // self-skip via Assume.assumeTrue rather than failing the whole suite.
        sftpLegacyPort = System.getProperty("SFTP_LEGACY_PORT", "");
        sftpLegacyUploadDir = System.getProperty("SFTP_LEGACY_UPLOAD_DIR", "");
        mirthLogPath = System.getProperty("MIRTH_LOG_PATH", "");

        seedAll();
    }

    private static boolean legacyLegActive() {
        return sftpLegacyPort != null && !sftpLegacyPort.isEmpty();
    }

    /**
     * Local replica of {@code SmokeTestBase}'s private {@code requireProperty} (HttpParamsTest
     * Pitfall 9 precedent) — deliberately NOT added to {@link SmokeTestBase#baseSetUp()}'s
     * required-property list, so other suites keep running unmodified without these.
     */
    private static String requireSftpProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required system property '" + name + "' not set. "
                    + "SftpParamsTest only runs against a live harness — invoke via "
                    + "smoke-tests/run-smoke-test.sh, which passes harness ports/paths as -D "
                    + "properties to `ant -f smoke-tests/build.xml test-run`.");
        }
        return value;
    }

    /**
     * Seeds all three fixtures' input files directly onto the host-side bind-mounted upload
     * directory (the same mechanism {@link NativePumpChannelsTest#pumpFile()} uses for the
     * local FILE-scheme channel) — the SFTP-scheme File Reader on each fixture channel then
     * picks the file up over the SFTP protocol on its own polling cycle (pollingFrequency
     * 3000ms, pollOnStart=true), proving the READ half of the round trip.
     */
    private static void seedAll() throws IOException {
        seed(sftpUploadDir, "input-modern.hl7");
        seed(sftpUploadDir, "input-keyauth.hl7");
        seed(sftpUploadDir, "input-knownhosts.hl7");

        // 25.1-03 (SC-3, IRT-1541): only the legacy-workaround leg needs a seed file — the
        // legacy-negative leg is EXPECTED to never successfully connect, so seeding its input
        // file would be pointless (and the legacy server's upload dir may not even be bind
        // mounted/writable from this JVM if the external driver didn't provide one).
        if (legacyLegActive() && sftpLegacyUploadDir != null && !sftpLegacyUploadDir.isEmpty()) {
            seed(sftpLegacyUploadDir, "input-legacy-workaround.hl7");
        }
    }

    private static void seed(String uploadDir, String fileName) throws IOException {
        Path seedFile = Paths.get(uploadDir, fileName);
        Files.write(seedFile, Hl7Messages.ORU_R01_LF.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    /**
     * Password auth round trip: the reader connects with {@code passwordAuth=true}
     * (jsch session.setUserInfo(SftpUserInfo) path), reads {@code input-modern.hl7}, the
     * transformer extracts patientName, and the writer deposits {@code output-modern.hl7}
     * back onto the same SFTP server — proving jsch 2.28.5's password-auth path still works
     * end-to-end for both directions (D-06).
     */
    @Test
    public void passwordAuthReadWrite() throws Exception {
        assertThreeLevels(MODERN_PASSWORD_ID, 1, () -> {
            Path out = pollForFile(Paths.get(sftpUploadDir, "output-modern.hl7"), 60);
            String content = readFile(out);
            assertTrue("SFTP modern-password destination content should contain the transformed patient token",
                    content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    /**
     * Key auth round trip: the reader connects with {@code keyAuth=true} and
     * {@code keyFile=${SFTP_KEY_PATH}} — jsch's {@code addIdentity} path (still functional
     * though {@code @Deprecated} in 2.28.5, per 25.1-PATTERNS.md) — reads
     * {@code input-keyauth.hl7} and the writer deposits {@code output-keyauth.hl7}. Proves
     * key-based auth still works end-to-end for both directions on jsch 2.28.5.
     */
    @Test
    public void keyAuthReadWrite() throws Exception {
        assertTrue("SFTP_KEY_PATH should reference an existing private key file",
                Files.exists(Paths.get(sftpKeyPath)));

        assertThreeLevels(KEY_AUTH_ID, 1, () -> {
            Path out = pollForFile(Paths.get(sftpUploadDir, "output-keyauth.hl7"), 60);
            String content = readFile(out);
            assertTrue("SFTP key-auth destination content should contain the transformed patient token",
                    content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    /**
     * Known-hosts round trip: the reader connects with {@code hostKeyChecking=yes} and
     * {@code knownHostsFile=${SFTP_KNOWN_HOSTS_PATH}} against the container's REAL host key
     * (captured at harness runtime via {@code ssh-keyscan}, never bypassed with {@code ask}/
     * {@code no}) — reads {@code input-knownhosts.hl7} and the writer deposits
     * {@code output-knownhosts.hl7}. A successful round trip here proves host-key
     * verification is actively exercised and passes (T-25.1-02a) — MITM spoofing protection
     * is live, not silently disabled.
     */
    @Test
    public void knownHostsVerification() throws Exception {
        assertTrue("SFTP_KNOWN_HOSTS_PATH should reference an existing known_hosts fixture",
                Files.exists(Paths.get(sftpKnownHostsPath)));

        assertThreeLevels(KNOWN_HOSTS_ID, 1, () -> {
            Path out = pollForFile(Paths.get(sftpUploadDir, "output-knownhosts.hl7"), 60);
            String content = readFile(out);
            assertTrue("SFTP known-hosts destination content should contain the transformed patient token",
                    content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    /**
     * D-07 negative leg (SC-3, IRT-1541; realigned 25.1-05, CR-01/SC-3 falsifiability
     * gap-closure): against the legacy-only server (regression-scripts/
     * docker-compose.test-irt1541.yml, offering ONLY the algorithm families jsch 2.28.5's
     * hardened defaults exclude), the File Reader's DEFAULT (empty) {@code
     * configurationSettings} must FAIL algorithm negotiation, and — since {@code
     * FileReceiver.onStart()} (server/src/.../file/FileReceiver.java) eagerly opens the
     * connection at DEPLOY time — that failure means the channel must NEVER reach STARTED.
     *
     * <p>Self-skips via {@link Assume#assumeTrue} when {@code SFTP_LEGACY_PORT} is unset — this
     * leg only ever runs under the external break-then-fix driver
     * ({@code regression-scripts/test-irt1541-jsch-sftp-upgrade.sh}), never a plain
     * {@code run-smoke-test.sh} invocation.
     *
     * <p><b>PRIMARY assertion (CR-01 fix):</b> a direct channel-never-STARTED check, with the
     * stable, driver-recognized failure substring {@code unexpectedly reached STARTED}. This
     * replaces the previous ordering, where the mirth.log signature assertion ran FIRST and a
     * genuine future hole in jsch's hardened defaults would fail on THAT message — never on the
     * driver-matched string — misclassifying a real security regression as INCONCLUSIVE instead
     * of SELF-TEST FAILED (regression-scripts/test-irt1541-jsch-sftp-upgrade.sh's exit-1
     * branch). The mirth.log {@code JSchAlgoNegoFailException} check below is now SECONDARY
     * corroboration only, run after the primary assertion has already established the channel
     * never started. The previous {@code assertEquals(0, sentCount)} check is removed — the
     * legacy-negative leg's input file is never seeded (see {@link #seedAll()}), so that
     * assertion was tautological (sent count can never be anything but 0) and could never
     * falsify anything.
     */
    @Test
    public void legacyDefaultFailsAlgoNego() throws Exception {
        Assume.assumeTrue("SFTP_LEGACY_PORT not set - skipping legacy algorithm-negotiation "
                + "negative leg (only runs under the external break-then-fix driver, "
                + "regression-scripts/test-irt1541-jsch-sftp-upgrade.sh)", legacyLegActive());
        assertTrue("MIRTH_LOG_PATH must be set when SFTP_LEGACY_PORT is active",
                mirthLogPath != null && !mirthLogPath.isEmpty());

        // PRIMARY (CR-01): the legacy-negative channel (00000030) must NEVER reach STARTED.
        // onStart() opens the connection synchronously at deploy time, so a bounded poll
        // window (no fixed sleep, Pitfall 7) is sufficient to observe whether the connection
        // ever unexpectedly succeeded.
        boolean everStarted = channelEverReachedStarted(LEGACY_NEGATIVE_ID, 10);
        assertFalse("legacy-negative channel 00000030 unexpectedly reached STARTED (onStart() "
                + "opened the connection, meaning the hardened defaults accepted the "
                + "legacy-only server -- a hole in the D-07 defaults)", everStarted);

        // SECONDARY corroboration: the log should ALSO record the specific
        // JSchAlgoNegoFailException / "Algorithm negotiation fail:" signature, not merely a
        // generic connect failure. The channel-start failure is synchronous so the signature
        // is typically already in mirth.log by the time this test runs; poll anyway in case of
        // scheduling variance.
        pollUntil("mirth.log contains a JSchAlgoNegoFailException signature for the "
                + "legacy-negative leg", 30, SftpParamsTest::mirthLogContainsAlgoNegoFailure);

        assertTrue("mirth.log should record the algorithm-negotiation failure SPECIFICALLY "
                + "(JSchAlgoNegoFailException / \"Algorithm negotiation fail:\"), not a "
                + "generic connect failure",
                mirthLogContainsAlgoNegoFailure());
    }

    /**
     * Polls (bounded, no fixed sleep — Pitfall 7) for up to {@code timeoutSeconds}, returning
     * {@code true} as soon as {@code channelId} is observed in STARTED state, or {@code false}
     * if the channel never reaches STARTED within the window. Unlike {@link #pollUntil}, this
     * does NOT throw when the condition never becomes true — for this negative leg, "never
     * started" is the EXPECTED, passing outcome.
     */
    private static boolean channelEverReachedStarted(String channelId, int timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        do {
            try {
                if (rest.isStarted(channelId)) {
                    return true;
                }
            } catch (IOException e) {
                // Treat a transient REST error as "not started yet" and keep polling.
            }
            Thread.sleep(500);
        } while (System.nanoTime() < deadline);
        return false;
    }

    /**
     * D-07 positive leg (SC-3, IRT-1541, D-05): against the SAME legacy-only server, the
     * documented per-channel {@code configurationSettings} override (populated with the FULL
     * comma-separated lists — legacy algorithm PLUS jsch's modern defaults, Assumption A2 /
     * T-25.1-03a) RESTORES connectivity — proving the escape hatch actually works, not just
     * that the hardened defaults reject legacy servers.
     */
    @Test
    public void legacyWorkaroundRestores() throws Exception {
        Assume.assumeTrue("SFTP_LEGACY_PORT not set - skipping legacy workaround positive leg "
                + "(only runs under the external break-then-fix driver, "
                + "regression-scripts/test-irt1541-jsch-sftp-upgrade.sh)", legacyLegActive());
        assertTrue("SFTP_LEGACY_UPLOAD_DIR must reference an existing, writable directory "
                + "when SFTP_LEGACY_PORT is active",
                sftpLegacyUploadDir != null && !sftpLegacyUploadDir.isEmpty()
                        && Files.isDirectory(Paths.get(sftpLegacyUploadDir)));

        assertThreeLevels(LEGACY_WORKAROUND_ID, 1, () -> {
            Path out = pollForFile(Paths.get(sftpLegacyUploadDir, "output-legacy-workaround.hl7"), 60);
            String content = readFile(out);
            assertTrue("SFTP legacy-workaround destination content should contain the "
                    + "transformed patient token", content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    /**
     * Reads the live mirth.log (fresh each call — the log grows over the run) and checks for
     * jsch's class-specific algorithm-negotiation-failure signature. Never throws on a missing
     * file (pollUntil retries); returns {@code false} instead so the poll loop keeps trying.
     */
    private static boolean mirthLogContainsAlgoNegoFailure() {
        try {
            if (mirthLogPath == null || mirthLogPath.isEmpty()) {
                return false;
            }
            Path logPath = Paths.get(mirthLogPath);
            if (!Files.exists(logPath)) {
                return false;
            }
            String logContent = new String(Files.readAllBytes(logPath), StandardCharsets.UTF_8);
            return logContent.contains("JSchAlgoNegoFailException")
                    || logContent.contains("Algorithm negotiation fail:");
        } catch (IOException e) {
            return false;
        }
    }
}
