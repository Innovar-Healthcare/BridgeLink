package com.mirth.connect.smoketest;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

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

    private static String sftpModernPort;
    private static String sftpKeyPath;
    private static String sftpKnownHostsPath;
    private static String sftpUploadDir;

    @BeforeClass
    public static void sftpParamsSetUp() throws Exception {
        sftpModernPort = requireSftpProperty("SFTP_MODERN_PORT");
        sftpKeyPath = requireSftpProperty("SFTP_KEY_PATH");
        sftpKnownHostsPath = requireSftpProperty("SFTP_KNOWN_HOSTS_PATH");
        sftpUploadDir = requireSftpProperty("SFTP_UPLOAD_DIR");

        seedAll();
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
        seed("input-modern.hl7");
        seed("input-keyauth.hl7");
        seed("input-knownhosts.hl7");
    }

    private static void seed(String fileName) throws IOException {
        Path seedFile = Paths.get(sftpUploadDir, fileName);
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
}
