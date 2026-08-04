package com.mirth.connect.smoketest;

import static org.junit.Assert.assertTrue;

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

    /** Empty when no external break-then-fix driver is running (ordinary harness run). */
    private static String mssqlHost;
    private static String mssqlPort;
    private static String mssqlDb;
    private static String mssqlUser;
    private static String mssqlPassword;

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
}
