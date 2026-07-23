package com.mirth.connect.smoketest.stubs;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * D-07 JDBC destination stub: a SQLite file database (18-RESEARCH.md Pattern 4, Open
 * Question 1 RESOLVED). SQLite is used instead of embedded Derby because a single-JVM
 * {@code db.lck} lock makes cross-connection reads impossible with Derby (Pitfall 4) — this
 * class exists specifically to prove the cross-connection read-after-write pattern the
 * harness needs: the deployed server's Database Writer connector (running inside a
 * different JVM in the real harness, driver {@code org.sqlite.JDBC}, URL
 * {@code jdbc:sqlite:${SQLITE_PATH}} per smoke-tests/channels/jdbc-test.xml) writes a row,
 * and the harness re-opens the file with a NEW connection after SENT status to read it back.
 *
 * <p><b>T-18-11 note:</b> unlike the other three D-07 stubs, JdbcSink has no network
 * listener to bind — it is a local SQLite file database accessed only via {@code jdbc:sqlite:}
 * file I/O, never over 127.0.0.1 or any other network interface, so there is no port for a
 * "CI runner network → stub listener" trust boundary to apply to.
 */
public class JdbcSink {

    private final String jdbcUrl;

    public JdbcSink(File dbFile) {
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /** Creates the table a channel's Database Writer INSERTs into. */
    public void createSchema() throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS smoke_messages ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "patient_name TEXT, "
                    + "raw_message TEXT, "
                    + "received_at TEXT)");
        }
    }

    /**
     * Test-only direct insert helper — StubSelfTest uses this to simulate the Database
     * Writer without a live channel; 18-07's channel-level assertions instead read rows
     * actually written by the deployed jdbc-test.xml channel.
     */
    public void insertRow(String patientName, String rawMessage) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO smoke_messages (patient_name, raw_message, received_at) VALUES (?, ?, datetime('now'))")) {
            ps.setString(1, patientName);
            ps.setString(2, rawMessage);
            ps.executeUpdate();
        }
    }

    /**
     * Opens a NEW connection (simulating the harness re-opening the file after the writer
     * committed) and reads all rows back — the cross-connection read-after-write proof.
     */
    public List<String> readPatientNames() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT patient_name FROM smoke_messages ORDER BY id")) {
            while (rs.next()) {
                names.add(rs.getString("patient_name"));
            }
        }
        return names;
    }
}
