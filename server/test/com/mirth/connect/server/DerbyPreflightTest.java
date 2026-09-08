package com.mirth.connect.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for the Derby/Java-21 startup preflight (IRT-1488 / JAVA-04).
 *
 * Tests the package-private, static, pure derbyPreflightBlocks(String, int)
 * truth table in Mirth, and the verbatim DERBY_JAVA_ERROR_MSG constant text.
 *
 * derbyPreflightBlocks is static and pure (no controller/field access), so
 * unlike RootCheckTest this suite needs zero Guice/Mockito setup.
 */
public class DerbyPreflightTest {

    @Test
    public void testBlocksWhenDerbyAndJava17() {
        assertTrue(Mirth.derbyPreflightBlocks("derby", 17));
    }

    @Test
    public void testAllowsWhenDerbyAndJava21() {
        assertFalse(Mirth.derbyPreflightBlocks("derby", 21));
    }

    @Test
    public void testAllowsWhenDerbyAndJava25() {
        assertFalse(Mirth.derbyPreflightBlocks("derby", 25));
    }

    @Test
    public void testAllowsWhenMysqlAndJava17() {
        assertFalse(Mirth.derbyPreflightBlocks("mysql", 17));
    }

    @Test
    public void testAllowsWhenPostgresqlAndJava17() {
        assertFalse(Mirth.derbyPreflightBlocks("postgresql", 17));
    }

    @Test
    public void testAllowsWhenDatabaseTypeNullAndJava17() {
        assertFalse(Mirth.derbyPreflightBlocks(null, 17));
    }

    @Test
    public void testBlocksWhenDerbyMixedCaseAndJava17() {
        // Case-insensitive match on database type
        assertTrue(Mirth.derbyPreflightBlocks("Derby", 17));
    }

    @Test
    public void testBlocksWhenDerbyUpperCaseAndJava17() {
        assertTrue(Mirth.derbyPreflightBlocks("DERBY", 17));
    }

    @Test
    public void testErrorMessageIsVerbatimIrt1488Text() {
        assertEquals("embedded Derby requires Java 21+ as of 26.9; upgrade Java or switch to an external database",
            Mirth.DERBY_JAVA_ERROR_MSG);
    }

}
