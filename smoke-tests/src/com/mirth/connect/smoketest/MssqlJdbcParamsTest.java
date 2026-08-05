package com.mirth.connect.smoketest;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assume;
import org.junit.Test;

/**
 * Live encrypted mssql-jdbc verification for the JDBC connector's 12.10.2.jre11 driver
 * (phase 25-cve-mssql-jdbc-upgrade, plan 25-01, CVE-04 / ROADMAP SC-3). Asserts a real
 * read AND write round trip -- through the Database Reader AND Database Writer -- against
 * a live containerized SQL Server (regression-scripts/docker-compose.test-cve04.yml),
 * proving the new driver negotiates TLS (encrypt=true;trustServerCertificate=true) and
 * moves data end-to-end on the JDK 17 floor.
 *
 * <p>Rides the same single harness boot as {@link SftpParamsTest}/{@link HttpParamsTest}
 * (JUnit's {@code batchtest} in {@code build.xml} auto-discovers this class). Only ever
 * runs under the external break-then-fix driver
 * ({@code regression-scripts/test-cve04-mssql-jdbc-upgrade.sh}), which pre-exports
 * MSSQL_HOST/MSSQL_PORT/MSSQL_DB/MSSQL_USER/MSSQL_PASSWORD before invoking
 * {@code run-smoke-test.sh} -- a plain harness run self-skips via {@link Assume#assumeTrue}
 * (25.1-07 discipline: a self-skip must never be classified OK/PASS by the DRIVER that
 * consumes this JUnit report; that classification lives in the driver script, not here).
 *
 * <p>Unlike the File-connector SFTP legs ({@link SftpParamsTest}, which poll for an output
 * FILE), the JDBC Database Writer's artifact is a row in the SQL Server {@code dest_records}
 * table -- so the L2 artifact check here opens its OWN JDBC connection (same driver, same
 * encrypted URL the channel itself uses) directly from this JVM, mirroring
 * {@code WebDavRoundTripTest}'s driver-JVM-direct-connection convention (22-05) rather than
 * {@link SmokeTestBase#pollForFile}.
 */
public class MssqlJdbcParamsTest extends SmokeTestBase {

    private static final String ENCRYPTED_ID = "00000034-0000-0000-0000-000000000034";
    private static final String SELFSIGNED_NEGATIVE_ID = "00000035-0000-0000-0000-000000000035";
    private static final String SELFSIGNED_WORKAROUND_ID = "00000036-0000-0000-0000-000000000036";
    /** Must match {@code <name>} in jdbc-mssql-selfsigned-negative-test.xml (WR-02: channel-scopes
     *  the mirth.log cert-validation-failure evidence so no OTHER channel's cert failure can
     *  satisfy this channel's falsifiability proof). */
    private static final String SELFSIGNED_NEGATIVE_CHANNEL_NAME = "JDBC MSSQL Self-Signed Negative Test";

    /** Empty when no external break-then-fix driver is running (ordinary harness run). */
    private static String mssqlHost;
    private static String mssqlPort;
    private static String mssqlDb;
    private static String mssqlUser;
    private static String mssqlPassword;
    /** 25-04 (D-09): empty unless run-smoke-test.sh forwarded a MIRTH_LOG_PATH -D property. */
    private static String mirthLogPath;

    private static boolean mssqlLegActive() {
        return mssqlPort != null && !mssqlPort.isEmpty();
    }

    /**
     * Local replica of {@code SmokeTestBase}'s private {@code requireProperty}
     * (SftpParamsTest's {@code requireSftpProperty} precedent) -- deliberately NOT added to
     * {@link SmokeTestBase#baseSetUp()}'s required-property list, so other suites keep
     * running unmodified when no external mssql driver is active. Called only AFTER
     * {@link Assume#assumeTrue} has confirmed the leg is active, so a thrown
     * {@link IllegalStateException} here means a genuine property-forwarding bug (MSSQL_PORT
     * set but a sibling MSSQL_* property missing), not an ordinary self-skip.
     */
    private static String requireMssqlProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required system property '" + name + "' not set. "
                    + "MssqlJdbcParamsTest only runs against a live SQL Server -- invoke via "
                    + "regression-scripts/test-cve04-mssql-jdbc-upgrade.sh, which pre-exports "
                    + "MSSQL_HOST/MSSQL_PORT/MSSQL_DB/MSSQL_USER/MSSQL_PASSWORD before invoking "
                    + "smoke-tests/run-smoke-test.sh.");
        }
        return value;
    }

    /**
     * Encrypted read+write round trip (D-08/SC-3): seeds ONE fresh unprocessed {@code
     * src_records} row (patient_name = {@link Hl7Messages#EXPECTED_PATIENT}) directly via
     * JDBC, immediately before polling -- the Database Reader's 3s poll cycle then picks it
     * up, the transformer extracts patientName from the row's XML representation, and the
     * Database Writer deposits the transformed row into {@code dest_records} -- via
     * mssql-jdbc-12.10.2.jre11 with encrypt=true;trustServerCertificate=true. A successful
     * round trip here proves the new driver actually negotiates TLS and moves data, not
     * merely that it opens a socket.
     *
     * <p><b>Rule 1 fix (discovered live):</b> the seed row must NOT be inserted at channel
     * deploy time (the fixture's {@code deployScript} is DDL-only, see
     * {@code jdbc-mssql-encrypted-test.xml}) -- {@code pollOnStart=true} means the Reader
     * would otherwise consume it within ~3s of deploy, well BEFORE
     * {@code run-smoke-test.sh}'s later "Clearing channel statistics" REST call zeroes the
     * sent/received counters for ALL 32 fixtures (that call runs once, right before this
     * JUnit driver forks) -- erasing the evidence before this test ever gets to poll for it.
     * Seeding here, mirroring {@link SftpParamsTest#seedAll()}'s seed-right-before-poll
     * convention, guarantees the row is still unprocessed when the poll window opens.
     *
     * <p>Self-skips via {@link Assume#assumeTrue} when {@code MSSQL_PORT} is unset -- this
     * leg only ever runs under the external break-then-fix driver
     * ({@code regression-scripts/test-cve04-mssql-jdbc-upgrade.sh}), never a plain
     * {@code run-smoke-test.sh} invocation.
     */
    @Test
    public void encryptedReadWrite() throws Exception {
        mssqlPort = System.getProperty("MSSQL_PORT", "");
        Assume.assumeTrue("MSSQL_PORT not set - skipping encrypted mssql-jdbc round-trip leg "
                + "(only runs under the external break-then-fix driver, "
                + "regression-scripts/test-cve04-mssql-jdbc-upgrade.sh)", mssqlLegActive());

        mssqlHost = requireMssqlProperty("MSSQL_HOST");
        mssqlDb = requireMssqlProperty("MSSQL_DB");
        mssqlUser = requireMssqlProperty("MSSQL_USER");
        mssqlPassword = requireMssqlProperty("MSSQL_PASSWORD");

        seedSrcRecord();

        assertThreeLevels(ENCRYPTED_ID, 1, () -> {
            String patientName = pollForDestPatientName(60);
            assertTrue("dest_records destination content should contain the transformed "
                    + "patient token (expected '" + Hl7Messages.EXPECTED_PATIENT + "', got '"
                    + patientName + "')", patientName.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    /**
     * D-09 negative leg (SC-3, CVE-04; phase 25-cve-mssql-jdbc-upgrade, plan 25-04): against
     * the SAME self-signed SQL Server container as {@link #encryptedReadWrite()}
     * (regression-scripts/docker-compose.test-cve04.yml), the DEFAULT connection (encrypt=true
     * implicit, no {@code trustServerCertificate}) must FAIL certificate validation on every
     * poll attempt -- proving mssql-jdbc's secure-by-default posture actually rejects an
     * untrusted self-signed cert (D-06). Unlike the File-connector SFTP negative leg
     * ({@link SftpParamsTest#legacyDefaultFailsAlgoNego()}, whose {@code FileReceiver.onStart()}
     * opens the connection synchronously so the channel never reaches STARTED), the JDBC
     * negative fixture ({@code jdbc-mssql-selfsigned-negative-test.xml}) sets
     * {@code keepConnectionOpen=false} -- {@code onStart()} never blocks, the channel always
     * reaches STARTED, and each failed poll cycle (pollingFrequency 3000ms) dispatches ONE
     * {@code ErrorEvent}, accruing a nonzero ERROR count over the poll window.
     *
     * <p><b>PRIMARY assertion:</b> a nonzero SENT count would mean the self-signed cert was
     * unexpectedly TRUSTED (a hole in the secure-by-default proof) -- the stable,
     * driver-recognized failure substring below ("unexpectedly reached a SENT count &gt; 0")
     * mirrors {@code SftpParamsTest}'s "unexpectedly reached STARTED" precedent, so
     * {@code test-cve04-mssql-jdbc-upgrade.sh} can distinguish this specific hole from a
     * generic infra failure (SELF-TEST FAILED, exit 1, vs. INCONCLUSIVE, exit 2).
     *
     * <p><b>SECONDARY (actually primary evidence) assertion:</b> mirth.log must contain the
     * class-specific mssql-jdbc cert-validation failure signature ({@code SQLServerException}:
     * "could not establish a secure connection" / PKIX / "validate the server" name) -- not
     * merely "any failure counts" (break-dependency.sh Pitfall 10a/10b discipline). <b>Rule 1
     * fix (discovered live):</b> {@code DatabaseReceiver.poll()}'s catch block dispatches an
     * {@code ErrorEvent} on the internal event bus when the connection itself fails -- it does
     * NOT create/persist an ERROR-status message, since no message ever entered the channel
     * pipeline (the failure happens before {@code processResultSet}/{@code dispatchRawMessage}
     * is ever reached). The REST {@code /channels/{id}/statistics} "error" field only counts
     * ERROR-status MESSAGES, so it never becomes nonzero for this specific connection-level
     * failure -- polling on it (as an earlier version of this method did) times out forever.
     * The mirth.log signature is therefore the actual, load-bearing evidence that the channel
     * accrued the expected failure, not merely corroboration; the REST "error" statistic is not
     * a meaningful signal here and is intentionally not asserted.
     *
     * <p>Self-skips via {@link Assume#assumeTrue} when {@code MSSQL_PORT} is unset -- this leg
     * only ever runs under the external break-then-fix driver
     * ({@code regression-scripts/test-cve04-mssql-jdbc-upgrade.sh}), never a plain
     * {@code run-smoke-test.sh} invocation.
     *
     * <p><b>--simulate-regression (fault-injection self-proof):</b> when
     * {@code MSSQL_SIMULATE_HOLE=1} is forwarded (test-cve04-mssql-jdbc-upgrade.sh
     * --simulate-regression swaps in {@code jdbc-mssql-selfsigned-negative-hole-test.xml} for
     * channel 00000035, which uses the SAME id but the RESTORING
     * {@code trustServerCertificate=true} URL), this method seeds ONE row into the hole
     * fixture's OWN {@code selfsigned_hole_src_records} table (never the real negative/
     * workaround fixtures' tables) so the hole channel actually reaches a nonzero SENT count --
     * proving the PRIMARY assertion's exit-1 branch is reachable, not unreachable dead code.
     */
    @Test
    public void selfSignedDefaultFailsCertValidation() throws Exception {
        mssqlPort = System.getProperty("MSSQL_PORT", "");
        Assume.assumeTrue("MSSQL_PORT not set - skipping self-signed default negative leg "
                + "(only runs under the external break-then-fix driver, "
                + "regression-scripts/test-cve04-mssql-jdbc-upgrade.sh)", mssqlLegActive());

        mirthLogPath = System.getProperty("MIRTH_LOG_PATH", "");
        assertTrue("MIRTH_LOG_PATH must be set when MSSQL_PORT is active",
                mirthLogPath != null && !mirthLogPath.isEmpty());

        if ("1".equals(System.getProperty("MSSQL_SIMULATE_HOLE", ""))) {
            mssqlHost = requireMssqlProperty("MSSQL_HOST");
            mssqlDb = requireMssqlProperty("MSSQL_DB");
            mssqlUser = requireMssqlProperty("MSSQL_USER");
            mssqlPassword = requireMssqlProperty("MSSQL_PASSWORD");
            seedSelfSignedHoleRecord();
        }

        // Poll until EITHER mirth.log records the cert-validation failure signature (expected
        // outcome) OR the channel unexpectedly accrues a SENT count (the hole condition) --
        // whichever happens first determines which assertion below fires.
        pollUntil("mirth.log contains the mssql-jdbc cert-validation failure signature, or "
                + "channel " + SELFSIGNED_NEGATIVE_ID + " unexpectedly accrues a SENT count", 30, () -> {
            try {
                return mirthLogContainsCertValidationFailure()
                        || rest.getSentCount(SELFSIGNED_NEGATIVE_ID) > 0;
            } catch (Exception e) {
                return false;
            }
        });

        long sentCount = rest.getSentCount(SELFSIGNED_NEGATIVE_ID);
        assertTrue("Channel " + SELFSIGNED_NEGATIVE_ID + " unexpectedly reached a SENT count > 0 "
                + "(the default encrypt=true connection to the self-signed server was accepted "
                + "without certificate validation -- a hole in the D-09 secure-by-default proof)",
                sentCount == 0);

        assertTrue("mirth.log should contain the mssql-jdbc cert-validation failure signature "
                + "(could not establish a secure connection / PKIX / validate the server name)",
                mirthLogContainsCertValidationFailure());
    }

    /**
     * D-09 positive leg (SC-3, CVE-04; phase 25-cve-mssql-jdbc-upgrade, plan 25-04): against the
     * SAME self-signed SQL Server container, the documented per-connection escape hatch
     * ({@code ;trustServerCertificate=true}, D-07) RESTORES connectivity -- proving the
     * workaround actually works, not just that the secure default rejects self-signed servers.
     * Uses its own {@code selfsigned_src_records}/{@code selfsigned_dest_records} tables
     * (created idempotently by {@code jdbc-mssql-selfsigned-workaround-test.xml}'s
     * deployScript) so this leg never races {@link #encryptedReadWrite()}'s
     * {@code src_records}/{@code dest_records} tables.
     */
    @Test
    public void selfSignedWorkaroundRestores() throws Exception {
        mssqlPort = System.getProperty("MSSQL_PORT", "");
        Assume.assumeTrue("MSSQL_PORT not set - skipping self-signed workaround leg "
                + "(only runs under the external break-then-fix driver, "
                + "regression-scripts/test-cve04-mssql-jdbc-upgrade.sh)", mssqlLegActive());

        mssqlHost = requireMssqlProperty("MSSQL_HOST");
        mssqlDb = requireMssqlProperty("MSSQL_DB");
        mssqlUser = requireMssqlProperty("MSSQL_USER");
        mssqlPassword = requireMssqlProperty("MSSQL_PASSWORD");

        seedSelfSignedWorkaroundRecord();

        assertThreeLevels(SELFSIGNED_WORKAROUND_ID, 1, () -> {
            String patientName = pollForSelfSignedWorkaroundDestPatientName(60);
            assertTrue("selfsigned_dest_records destination content should contain the "
                    + "transformed patient token (expected '" + Hl7Messages.EXPECTED_PATIENT
                    + "', got '" + patientName + "')",
                    patientName.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    /**
     * Inserts one fresh unprocessed {@code src_records} row directly via JDBC (same
     * encrypted URL the channel itself uses), immediately before {@link #assertThreeLevels}
     * starts polling -- see the Rule 1 fix note on {@link #encryptedReadWrite()} for why
     * this cannot live in the channel's {@code deployScript}.
     */
    private void seedSrcRecord() throws SQLException {
        String url = "jdbc:sqlserver://" + mssqlHost + ":" + mssqlPort + ";databaseName="
                + mssqlDb + ";encrypt=true;trustServerCertificate=true";
        try (Connection conn = DriverManager.getConnection(url, mssqlUser, mssqlPassword);
                Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO src_records (patient_name, payload, processed) VALUES ('"
                    + Hl7Messages.EXPECTED_PATIENT + "', 'cve-04-encrypted-roundtrip-seed', 0)");
        }
    }

    /**
     * Polls (no fixed sleep, {@link SmokeTestBase#pollUntil} -- Pitfall 7) for up to {@code
     * timeoutSeconds} for a row to appear in {@code dest_records}, opening its OWN encrypted
     * JDBC connection each poll attempt (same URL shape the channel itself uses) rather than
     * reusing a long-lived connection across the whole poll window -- keeps this test-only
     * connection lifecycle independent of the channel's pooled connection lifecycle.
     */
    private static String pollForDestPatientName(int timeoutSeconds) throws InterruptedException {
        AtomicReference<String> found = new AtomicReference<>("");
        pollUntil("dest_records row via mssql-jdbc (encrypted)", timeoutSeconds, () -> {
            String url = "jdbc:sqlserver://" + mssqlHost + ":" + mssqlPort + ";databaseName="
                    + mssqlDb + ";encrypt=true;trustServerCertificate=true";
            try (Connection conn = DriverManager.getConnection(url, mssqlUser, mssqlPassword);
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT TOP 1 patient_name FROM dest_records ORDER BY id DESC")) {
                if (rs.next()) {
                    found.set(rs.getString("patient_name"));
                    return true;
                }
                return false;
            } catch (SQLException e) {
                // Transient connection failure while the server is still starting up its
                // encrypted listener -- treat as "not yet" and keep polling.
                return false;
            }
        });
        return found.get();
    }

    /**
     * Inserts one fresh unprocessed {@code selfsigned_src_records} row directly via JDBC (the
     * documented {@code ;trustServerCertificate=true} workaround URL), immediately before
     * {@link #assertThreeLevels} starts polling -- mirrors {@link #seedSrcRecord()}'s
     * seed-right-before-poll convention, on the self-signed leg's own tables.
     */
    private void seedSelfSignedWorkaroundRecord() throws SQLException {
        String url = "jdbc:sqlserver://" + mssqlHost + ":" + mssqlPort + ";databaseName="
                + mssqlDb + ";trustServerCertificate=true";
        try (Connection conn = DriverManager.getConnection(url, mssqlUser, mssqlPassword);
                Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO selfsigned_src_records (patient_name, payload, processed) "
                    + "VALUES ('" + Hl7Messages.EXPECTED_PATIENT + "', "
                    + "'cve-04-selfsigned-workaround-roundtrip-seed', 0)");
        }
    }

    /**
     * Polls (no fixed sleep, {@link SmokeTestBase#pollUntil} -- Pitfall 7) for up to {@code
     * timeoutSeconds} for a row to appear in {@code selfsigned_dest_records}, opening its OWN
     * trustServerCertificate=true JDBC connection each poll attempt -- mirrors
     * {@link #pollForDestPatientName(int)} on the self-signed leg's own tables.
     */
    private static String pollForSelfSignedWorkaroundDestPatientName(int timeoutSeconds) throws InterruptedException {
        AtomicReference<String> found = new AtomicReference<>("");
        pollUntil("selfsigned_dest_records row via mssql-jdbc (workaround)", timeoutSeconds, () -> {
            String url = "jdbc:sqlserver://" + mssqlHost + ":" + mssqlPort + ";databaseName="
                    + mssqlDb + ";trustServerCertificate=true";
            try (Connection conn = DriverManager.getConnection(url, mssqlUser, mssqlPassword);
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT TOP 1 patient_name FROM selfsigned_dest_records ORDER BY id DESC")) {
                if (rs.next()) {
                    found.set(rs.getString("patient_name"));
                    return true;
                }
                return false;
            } catch (SQLException e) {
                // Transient connection failure while the server is still starting up --
                // treat as "not yet" and keep polling.
                return false;
            }
        });
        return found.get();
    }

    /**
     * --simulate-regression fault-injection seed (test-cve04-mssql-jdbc-upgrade.sh): inserts
     * one fresh unprocessed row into the hole fixture's OWN {@code selfsigned_hole_src_records}
     * table (never the real negative/workaround fixtures' tables), via the SAME
     * {@code trustServerCertificate=true} connection the hole fixture itself uses -- proving
     * the hole channel (00000035, swapped to the restoring URL) actually reaches a nonzero SENT
     * count when {@code MSSQL_SIMULATE_HOLE=1} is active. Only ever called when that property is
     * set (see {@link #selfSignedDefaultFailsCertValidation()}); never runs in a normal leg.
     */
    private void seedSelfSignedHoleRecord() throws SQLException {
        String url = "jdbc:sqlserver://" + mssqlHost + ":" + mssqlPort + ";databaseName="
                + mssqlDb + ";trustServerCertificate=true";
        try (Connection conn = DriverManager.getConnection(url, mssqlUser, mssqlPassword);
                Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO selfsigned_hole_src_records (patient_name, payload, "
                    + "processed) VALUES ('" + Hl7Messages.EXPECTED_PATIENT + "', "
                    + "'cve-04-simulate-regression-hole-seed', 0)");
        }
    }

    /**
     * Reads the live mirth.log (fresh each call -- the log grows over the run) and checks for
     * mssql-jdbc's class-specific certificate-validation-failure signature. Never throws on a
     * missing file (pollUntil retries); returns {@code false} instead so the poll loop keeps
     * trying. Mirrors {@code SftpParamsTest#mirthLogContainsAlgoNegoFailure()}.
     *
     * <p>WR-02: scoped to lines that also name {@link #SELFSIGNED_NEGATIVE_CHANNEL_NAME} (not a
     * bare substring match anywhere in the shared log) so a different mssql fixture's own
     * PKIX/cert-validation failure can never satisfy channel 35's falsifiability proof. This is
     * safe on a single physical line because DatabaseReceiver logs
     * {@code Error in channel "<name>": <e.getMessage()>} as one log record whose message
     * (DatabaseReceiverException(Throwable) wraps the driver exception's own {@code toString()})
     * carries both the channel name and the cert-validation text together.
     */
    private static boolean mirthLogContainsCertValidationFailure() {
        try {
            if (mirthLogPath == null || mirthLogPath.isEmpty()) {
                return false;
            }
            Path logPath = Paths.get(mirthLogPath);
            if (!Files.exists(logPath)) {
                return false;
            }
            String logContent = new String(Files.readAllBytes(logPath), StandardCharsets.UTF_8);
            for (String line : logContent.split("\\r?\\n", -1)) {
                if (!line.contains(SELFSIGNED_NEGATIVE_CHANNEL_NAME)) {
                    continue;
                }
                if (line.contains("could not establish a secure connection")
                        || line.contains("PKIX")
                        || line.contains("Failed to validate the server name")) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
