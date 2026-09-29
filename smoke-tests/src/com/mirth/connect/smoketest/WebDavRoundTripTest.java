package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.connectors.file.FileSystemConnectionOptions;
import com.mirth.connect.connectors.file.filesystems.WebDavConnection;
import com.mirth.connect.smoketest.stubs.WebDavServerStub;

/**
 * D-07 HARD GATE (CVE-06, 22-05): File-connector {@code webdav://} read+write round-trip
 * against a real embedded WebDAV server ({@link WebDavServerStub}), closing the zero-coverage
 * gap the Sardine rewrite (D-06) would otherwise leave silent.
 *
 * <p><b>Rule 1 fix -- stub lifecycle is NOT owned by this test class</b> (unlike every other
 * embedded-server stub in this harness): {@code FileReceiver.onStart()} eagerly opens and
 * validates its connection at CHANNEL DEPLOY time, which happens well before
 * {@code run-smoke-test.sh}'s JUnit driver phase. Starting {@link WebDavServerStub} from this
 * class's own {@code @BeforeClass} (as {@code DicomScpStub}/{@code SoapStub} do for their
 * connectors, which only connect out lazily at send-time) would leave
 * {@code file-webdav-test.xml} deploying against nothing listening yet. {@code
 * run-smoke-test.sh} therefore launches {@link WebDavServerStub}'s {@code main()} as an
 * independent OS process BEFORE the channel import/deploy stage (mirroring the SFTP leg's
 * independent atmoz/sftp Docker container) and stops it during teardown; this class only reads
 * the already-running server's DAV root directory ({@code WEBDAV_ROOT_DIR}) to seed/poll files.
 *
 * <p>Mirrors {@code SftpParamsTest}'s shape otherwise: own {@code @BeforeClass} + a local
 * {@code requireWebdavProperty} replica (deliberately NOT added to
 * {@link SmokeTestBase#baseSetUp()}'s shared required-property list, so other suites keep
 * running unmodified without {@code WEBDAV_PORT} set), {@link #assertThreeLevels} for the L1/L2
 * gate, {@link #pollForFile}/{@link #pollUntil} (no fixed sleeps).
 *
 * <p><b>D-07 behavioral-risk coverage:</b>
 * <ul>
 *   <li><b>Basic auth</b> -- {@link WebDavServerStub} requires HTTP Basic Auth on every request
 *       (never anonymous); a successful round trip proves the credential path works both ways
 *       (see {@link WebDavConnection}'s Rule 1 preemptive-auth fix for the PUT-body-replay
 *       hazard this specifically avoids).</li>
 *   <li><b>PROPFIND listing</b> -- the File Reader's directory scan drives a real
 *       {@code sardine.list()}/PROPFIND against the stub to find {@code input-webdav.hl7}.</li>
 *   <li><b>Path encoding</b> -- {@code WebDavConnection.getFullPath}'s slash-normalization
 *       (@code ("/"+dir+"/"+file).replaceAll("//","/")}) is exercised organically on every
 *       request this test drives (the parsed connector directory always carries a leading
 *       slash that collides with the format string's own leading slash).</li>
 *   <li><b>{@code webdav://} vs {@code webdavs://}</b> -- {@link #webdavReadWriteRoundTrip()}
 *       exercises the plaintext leg through a REAL deployed channel; {@link
 *       #webdavsSecureSchemeMapping()} exercises the TLS leg via a DIRECT {@link
 *       WebDavConnection} instantiation against the SAME external stub process's HTTPS
 *       listener (avoiding a global TLS-truststore override on the live deployed Mirth server
 *       process -- scoped instead to this forked driver JVM only, see
 *       {@code smoke-tests/build.xml}'s {@code javax.net.ssl.trustStore} wiring). Both exercise
 *       the identical production {@code WebDavConnection}+Sardine code path.</li>
 * </ul>
 */
public class WebDavRoundTripTest extends SmokeTestBase {

    private static final String WEBDAV_CHANNEL_ID = "00000033-0000-0000-0000-000000000033";
    private static final String WEBDAV_USERNAME = "webdavuser";
    private static final String WEBDAV_PASSWORD = "webdavpass";

    private static String webdavTlsPort;
    private static Path uploadDir;

    @BeforeClass
    public static void webdavSetUp() throws Exception {
        requireWebdavProperty("WEBDAV_PORT");
        webdavTlsPort = requireWebdavProperty("WEBDAV_TLS_PORT");
        requireWebdavProperty("WEBDAV_TLS_KEYSTORE");
        requireWebdavProperty("WEBDAV_TLS_KEYSTORE_PW");
        String webdavRootDir = requireWebdavProperty("WEBDAV_ROOT_DIR");

        // File Reader/Writer point at "/upload" (file-webdav-test.xml's <host> path segment,
        // mirroring the SFTP/FTP fixtures' "127.0.0.1:PORT/upload" convention). The external
        // WebDavServerStub process (launched by run-smoke-test.sh, already running) created
        // WEBDAV_ROOT_DIR/upload before the channel deploy stage.
        uploadDir = Paths.get(webdavRootDir, "upload");

        seed(uploadDir.resolve("input-webdav.hl7"));
    }

    /**
     * Local replica of {@code SmokeTestBase}'s private {@code requireProperty}/{@code
     * SftpParamsTest}'s {@code requireSftpProperty} -- deliberately NOT added to
     * {@link SmokeTestBase#baseSetUp()}'s shared required-property list, so other suites keep
     * running unmodified without these WebDAV-specific properties.
     */
    private static String requireWebdavProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required system property '" + name + "' not set. "
                    + "WebDavRoundTripTest only runs against a live harness -- invoke via "
                    + "smoke-tests/run-smoke-test.sh, which passes harness ports/paths as -D "
                    + "properties to `ant -f smoke-tests/build.xml test-run`.");
        }
        return value;
    }

    private static void seed(Path seedFile) throws IOException {
        Files.write(seedFile, Hl7Messages.ORU_R01_LF.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    /**
     * Plaintext {@code webdav://} read+write round trip through a REAL deployed File Reader +
     * File Writer channel (file-webdav-test.xml): the reader picks up {@code input-webdav.hl7}
     * over WebDAV (READ, exercising PROPFIND listing + basic auth), the transformer extracts
     * patientName, and the writer PUTs {@code output-webdav.hl7} back to the same DAV root
     * (WRITE) -- proving both directions against Sardine end-to-end.
     */
    @Test
    public void webdavReadWriteRoundTrip() throws Exception {
        assertThreeLevels(WEBDAV_CHANNEL_ID, 1, () -> {
            Path out = pollForFile(uploadDir.resolve("output-webdav.hl7"), 60);
            String content = readFile(out);
            assertTrue("WebDAV destination content should contain the transformed patient token", content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    /**
     * TLS {@code webdavs://} scheme-mapping proof: directly instantiates the production {@link
     * WebDavConnection} (secure=true) against {@link WebDavServerStub}'s HTTPS listener --
     * confirming the {@code secure} flag actually switches Sardine onto an
     * {@code https://}-prefixed base URL and a real TLS handshake succeeds end-to-end (write,
     * exists, read-back), not merely that the flag is stored. Run directly (not through a
     * deployed channel) to keep the self-signed certificate's trust scoped to this forked
     * driver JVM (see {@code smoke-tests/build.xml}'s {@code javax.net.ssl.trustStore} wiring)
     * rather than overriding the live deployed Mirth server's JVM-wide TLS trust store.
     */
    @Test
    public void webdavsSecureSchemeMapping() throws Exception {
        FileSystemConnectionOptions options = new FileSystemConnectionOptions(false, WEBDAV_USERNAME, WEBDAV_PASSWORD, null);
        WebDavConnection connection = new WebDavConnection("127.0.0.1", Integer.parseInt(webdavTlsPort), true, options);

        try {
            String fileName = "webdavs-direct.hl7";
            byte[] content = Hl7Messages.ORU_R01_LF.getBytes(StandardCharsets.UTF_8);

            try (InputStream is = new ByteArrayInputStream(content)) {
                connection.writeFile(fileName, "/", false, is, content.length, null);
            }

            assertTrue("webdavs:// write should be visible via a direct exists() check", connection.exists(fileName, "/"));

            String readBack;
            try (InputStream in = connection.readFile(fileName, "/", null)) {
                readBack = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } finally {
                connection.closeReadFile();
            }

            assertEquals("webdavs:// read-back content should be byte-identical to what was written", Hl7Messages.ORU_R01_LF, readBack);
        } finally {
            connection.destroy();
        }
    }
}
