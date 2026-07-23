package com.mirth.connect.smoketest.stubs;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

/**
 * D-07 JDBC destination stub skeleton (RED phase — see StubSelfTest Test 3). Real
 * implementation lands in the GREEN commit.
 */
public class JdbcSink {

    public JdbcSink(File dbFile) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public String getJdbcUrl() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void createSchema() throws SQLException {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void insertRow(String patientName, String rawMessage) throws SQLException {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public List<String> readPatientNames() throws SQLException {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
