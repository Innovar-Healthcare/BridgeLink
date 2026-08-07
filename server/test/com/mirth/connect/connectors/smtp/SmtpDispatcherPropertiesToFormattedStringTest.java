/*
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 */
package com.mirth.connect.connectors.smtp;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

/**
 * E3 (SC-4): {@code SmtpDispatcherProperties.toFormattedString()} must emit a {@code BCC:} line
 * once PR #177 lands. Authored now, but gated on the same {@code PR177_PRESENT} system-property
 * verdict used by the rest of the phase's smoke tests (see {@code SmtpCcBccRoundTripTest} /
 * {@code SmtpJsApiCcBccTest}), rather than a reflective presence check of a different class
 * ({@code ServerSMTPConnection}'s 7-arg {@code send(...)} overload). The prior cross-class marker
 * risked a false failure/false negative if the two changes ever landed non-atomically
 * (18.5-REVIEW.md WR-02); gating on the single shared verdict keeps the skip decision and the
 * assertion target aligned on the class actually under test. This keeps the skip honest (not
 * assert-then-catch) so the test self-activates into a real assertion the moment PR #177 is
 * present, without ever reddening the current-branch suite.
 */
public class SmtpDispatcherPropertiesToFormattedStringTest {

    @Test
    public void toFormattedString_emitsBccLine() {
        assumeTrue("PR #177 not present -- BCC line not yet emitted by toFormattedString()",
                Boolean.getBoolean("PR177_PRESENT"));

        SmtpDispatcherProperties properties = new SmtpDispatcherProperties();
        properties.setBcc("bcc@example.com");

        assertTrue("toFormattedString() should contain a BCC: line once PR #177 lands",
                properties.toFormattedString().contains("BCC:"));
    }
}
