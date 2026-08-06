/*
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 */
package com.mirth.connect.connectors.smtp;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.lang.reflect.Method;

import org.junit.Test;

import com.mirth.connect.server.util.ServerSMTPConnection;

/**
 * E3 (SC-4): {@code SmtpDispatcherProperties.toFormattedString()} must emit a {@code BCC:} line
 * once PR #177 lands. Authored now, but the skip decision is decoupled from the assertion via a
 * reflective presence check of the PR #177 marker -- the 7-arg
 * {@code ServerSMTPConnection.send(to, cc, bcc, from, subject, body, charset)} overload, which is
 * absent on the current branch. This keeps the skip honest (not assert-then-catch) so the test
 * self-activates into a real assertion the moment PR #177 is present, without ever reddening the
 * current-branch suite.
 */
public class SmtpDispatcherPropertiesToFormattedStringTest {

    /**
     * @return true once PR #177 has landed the 7-arg
     *         {@code ServerSMTPConnection.send(to, cc, bcc, from, subject, body, charset)}
     *         overload -- the stable, independent PR-#177 marker (SmtpDispatcherProperties.java's
     *         {@code toFormattedString()} BCC line lands atomically with it).
     */
    private static boolean pr177Present() {
        for (Method method : ServerSMTPConnection.class.getDeclaredMethods()) {
            if ("send".equals(method.getName()) && method.getParameterTypes().length == 7) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void toFormattedString_emitsBccLine() {
        assumeTrue("PR #177 not present -- BCC line not yet emitted by toFormattedString()",
                pr177Present());

        SmtpDispatcherProperties properties = new SmtpDispatcherProperties();
        properties.setBcc("bcc@example.com");

        assertTrue("toFormattedString() should contain a BCC: line once PR #177 lands",
                properties.toFormattedString().contains("BCC:"));
    }
}
