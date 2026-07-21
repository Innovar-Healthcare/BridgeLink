/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.logging;

import static org.junit.Assert.assertEquals;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.Test;

/**
 * D-06 regression test for {@link MirthLog4jFilter}, the single BridgeLink touchpoint directly
 * exercising log4j-core's parameterized-logging message-formatting API changed by the 2.25.4
 * changelog item #1 fix ("Don't issue warnings if extra argument in parameterized logging is
 * null", log4j #3975 / PR #4014).
 *
 * Prior to this test, {@code MirthLog4jFilter} had 0% JaCoCo coverage (confirmed in
 * 11-IMPACT-ANALYSIS.md).
 */
public class MirthLog4jFilterTest {

    /**
     * MirthLog4jFilter.decide(...) only DENYs when the logger name equals LogOutputStream's
     * LOGGER_NAME constant, which is the literal string "Server" (not a class-derived logger
     * name). This logger MUST be obtained by that literal name to exercise the DENY branch.
     */
    private static final String SERVER_LOGGER_NAME = "Server";

    private final Logger serverLogger = (Logger) LoggerContext.getContext(false).getLogger(SERVER_LOGGER_NAME);
    private final Logger otherLogger = (Logger) LoggerContext.getContext(false).getLogger(MirthLog4jFilterTest.class);
    private final MirthLog4jFilter filter = new MirthLog4jFilter();

    @Test
    public void testFilterDeniesKnownSaxParserWarningOnServerLogger() {
        Result result = filter.filter(serverLogger, Level.ERROR, null,
                "Feature 'http://javax.xml.XMLConstants/feature/secure-processing' is not recognized.");
        assertEquals(Result.DENY, result);
    }

    @Test
    public void testFilterDoesNotDenySameMessageOnNonServerLogger() {
        // Proves the LOGGER_NAME.equals(...) gate: the same known SAXParser-warning message on a
        // logger that is NOT named "Server" must NOT be denied. Ordinary server-wide logging on
        // any other logger is never suppressed by this filter.
        Result result = filter.filter(otherLogger, Level.ERROR, null,
                "Feature 'http://javax.xml.XMLConstants/feature/secure-processing' is not recognized.");
        assertEquals(Result.NEUTRAL, result);
    }

    @Test
    public void testFilterDoesNotDenyBelowErrorLevelOnServerLogger() {
        // Proves the level gate: only Level.ERROR is eligible for DENY, even on the "Server"
        // logger with a message that would otherwise match.
        Result result = filter.filter(serverLogger, Level.WARN, null,
                "Feature 'http://javax.xml.XMLConstants/feature/secure-processing' is not recognized.");
        assertEquals(Result.NEUTRAL, result);
    }

    @Test
    public void testFilterHandlesNullVarargsElementWithoutThrowing() {
        // Regression guard for the 2.25.4 fix: "Don't issue warnings if extra argument in
        // parameterized logging is null" (log4j #3975 / PR #4014). MessageFactory.newMessage(msg,
        // params) must tolerate a null element in the varargs array without throwing and without
        // triggering a spurious StatusLogger warning.
        Result result = filter.filter(serverLogger, Level.INFO, null, "message with {} arg", new Object[] { null });
        assertEquals(Result.NEUTRAL, result);
    }

    @Test
    public void testFilterDoesNotDenyUnrelatedErrorOnServerLogger() {
        Result result = filter.filter(serverLogger, Level.ERROR, null, "Some unrelated server error message.");
        assertEquals(Result.NEUTRAL, result);
    }
}
