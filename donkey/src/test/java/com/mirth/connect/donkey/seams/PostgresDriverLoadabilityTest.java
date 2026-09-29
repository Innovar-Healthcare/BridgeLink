/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.seams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;

import org.junit.Test;

/**
 * IRT-1489-26.8 (D-03/D-04, SC-1): unconditional per-push loadability gate for the postgres JDBC
 * driver, separate from {@link JdbcDriverSeamTest} (a derby-default CRUD characterization,
 * {@code -Ddb}-gated for pg). This class is NOT gated by any system property and runs on every
 * leg of {@code test-run-and-aggregate} (JDK 17/21/25), closing the 26.6-VALIDATION.md SC-3 gap
 * (the Java-17 loadability check was a shell gate outside {@code ant test-run}).
 * <p>
 * Every assertion below is a plain {@code assertNotNull}/{@code assertTrue}/{@code assertEquals}
 * that produces a JUnit {@code <failure>}, never a {@code <skipped>}. No {@code org.junit.Assume}
 * is used anywhere in this class: an {@code Assume} self-skip would record a false-positive green
 * on an absent or wrong driver, which is precisely the 25.1 {@code <skipped>}-as-PASS landmine
 * this test exists to guard against (D-08).
 */
public class PostgresDriverLoadabilityTest {

    private static final String DRIVER_RESOURCE = "org/postgresql/Driver.class";
    private static final String DRIVER_CLASS = "org.postgresql.Driver";

    // Feature version = class-file major - 44, so 61 is Java 17. This mirrors
    // smoke-tests/check-jar-java17.sh's MAX_CLASS_MAJOR default.
    private static final int JAVA17_MAX_MAJOR = 61;

    /**
     * D-04(a): the org/postgresql/Driver.class resource must be present on the donkey test
     * classpath. FAILS closed (never skips) if the postgres driver jar is absent.
     */
    @Test
    public void driverClassPresentOnClasspath() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(DRIVER_RESOURCE)) {
            assertNotNull("FAIL closed: " + DRIVER_RESOURCE + " is absent from the donkey test "
                    + "classpath (postgres driver jar missing?)", in);
        }
    }

    /**
     * D-04(b): reads the BASE org/postgresql/Driver.class resource (not a META-INF/versions/N
     * multi-release tier; this jar's only MR tier, versions/11, contains only LazyCleanerImpl and
     * never a versioned Driver.class, so the base entry is also the resolved class) and asserts
     * its class-file major version, the big-endian u2 at byte offset 6, is <= 61 (Java-17
     * loadable). Ported from smoke-tests/check-jar-java17.sh's byte-read logic.
     */
    @Test
    public void driverClassFileIsJava17Loadable() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(DRIVER_RESOURCE)) {
            assertNotNull(DRIVER_RESOURCE + " absent from classpath", in);

            byte[] header = in.readNBytes(8);
            assertEquals("class file must contain a full 8-byte header", 8, header.length);
            assertTrue("class file must start with magic 0xCAFEBABE",
                    (header[0] & 0xFF) == 0xCA && (header[1] & 0xFF) == 0xFE
                            && (header[2] & 0xFF) == 0xBA && (header[3] & 0xFF) == 0xBE);

            int major = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
            assertTrue("org.postgresql.Driver must be Java-17-loadable (class-file major <= "
                    + JAVA17_MAX_MAJOR + "), was " + major, major <= JAVA17_MAX_MAJOR);
        }
    }

    /**
     * D-04(c)/(d): the class actually resolves via Class.forName, and the resolved driver reports
     * a 42.7.x-or-newer version. getMajorVersion()/getMinorVersion() are java.sql.Driver metadata
     * methods that open no connection and read no credentials; a silent downgrade below the
     * CVE-fixed 42.7 floor turns this test red. A future 43.x driver line would deliberately fail
     * the major==42 check and must be updated by hand, since that failure is the intended forcing
     * function for a human to look at the version-floor assertion again.
     */
    @Test
    public void driverResolvesAndMeetsVersionFloor() throws Exception {
        Class<?> driverClass = Class.forName(DRIVER_CLASS);
        java.sql.Driver driver = (java.sql.Driver) driverClass.getDeclaredConstructor().newInstance();

        assertEquals("postgres driver major line must be 42", 42, driver.getMajorVersion());
        assertTrue("postgres driver minor must be >= 7 (CVE-fixed 42.7.x floor); a silent "
                + "downgrade below 42.7 must turn this test red, was " + driver.getMajorVersion()
                + "." + driver.getMinorVersion(), driver.getMinorVersion() >= 7);
    }
}
