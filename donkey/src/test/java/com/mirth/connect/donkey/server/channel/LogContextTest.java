/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.apache.logging.log4j.ThreadContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LogContextTest {

    @Before
    @After
    public void clear() {
        // These tests assert the enriched (enabled) behavior. The server can flip this off at
        // runtime via the log.channelcontext.enabled property, so set it explicitly here.
        LogContext.setEnabled(true);
        ThreadContext.clearAll();
    }

    @Test
    public void channelScope_setsAndRestores() {
        assertNull(ThreadContext.get(LogContext.CHANNEL_ID));
        try (LogContext.Scope s = LogContext.channel("abc", "Channel A")) {
            assertEquals("abc", ThreadContext.get(LogContext.CHANNEL_ID));
            assertEquals("Channel A", ThreadContext.get(LogContext.CHANNEL_NAME));
        }
        assertNull(ThreadContext.get(LogContext.CHANNEL_ID));
        assertNull(ThreadContext.get(LogContext.CHANNEL_NAME));
    }

    @Test
    public void nestedScopes_innerRestoresPrior() {
        try (LogContext.Scope outer = LogContext.channel("outer-id", "Outer")) {
            assertEquals("outer-id", ThreadContext.get(LogContext.CHANNEL_ID));
            try (LogContext.Scope inner = LogContext.channel("inner-id", "Inner")) {
                assertEquals("inner-id", ThreadContext.get(LogContext.CHANNEL_ID));
                assertEquals("Inner", ThreadContext.get(LogContext.CHANNEL_NAME));
            }
            assertEquals("outer-id", ThreadContext.get(LogContext.CHANNEL_ID));
            assertEquals("Outer", ThreadContext.get(LogContext.CHANNEL_NAME));
        }
        assertNull(ThreadContext.get(LogContext.CHANNEL_ID));
    }

    @Test
    public void connectorScope_addsMetaDataId() {
        try (LogContext.Scope c = LogContext.channel("c1", "C1");
             LogContext.Scope k = LogContext.connector("dest1", 7)) {
            assertEquals("c1", ThreadContext.get(LogContext.CHANNEL_ID));
            assertEquals("dest1", ThreadContext.get(LogContext.CONNECTOR));
            assertEquals("7", ThreadContext.get(LogContext.META_DATA_ID));
        }
        assertNull(ThreadContext.get(LogContext.CONNECTOR));
        assertNull(ThreadContext.get(LogContext.META_DATA_ID));
    }

    @Test
    public void messageScope_setsMessageId() {
        try (LogContext.Scope m = LogContext.message(42L)) {
            assertEquals("42", ThreadContext.get(LogContext.MESSAGE_ID));
        }
        assertNull(ThreadContext.get(LogContext.MESSAGE_ID));
    }

    @Test
    public void nullValue_doesNotPollute() {
        try (LogContext.Scope s = LogContext.channel("c", null)) {
            assertEquals("c", ThreadContext.get(LogContext.CHANNEL_ID));
            assertNull(ThreadContext.get(LogContext.CHANNEL_NAME));
        }
    }

    /**
     * Pre-existing MDC value (set outside any Scope, e.g. by a non-LogContext caller)
     * must be restored when an inner Scope that overwrites it closes.
     */
    @Test
    public void overwritingExistingKey_restoresPriorValue() {
        ThreadContext.put(LogContext.CHANNEL_ID, "preset");
        try {
            try (LogContext.Scope s = LogContext.channel("inner", "InnerName")) {
                assertEquals("inner", ThreadContext.get(LogContext.CHANNEL_ID));
            }
            assertEquals("preset", ThreadContext.get(LogContext.CHANNEL_ID));
        } finally {
            ThreadContext.remove(LogContext.CHANNEL_ID);
        }
    }

    /** Closing a Scope twice must be safe — the second close is a no-op. */
    @Test
    public void close_isIdempotent() {
        LogContext.Scope s = LogContext.channel("c", "C");
        assertEquals("c", ThreadContext.get(LogContext.CHANNEL_ID));
        s.close();
        assertNull(ThreadContext.get(LogContext.CHANNEL_ID));
        // Second close must not re-remove or throw
        s.close();
        assertNull(ThreadContext.get(LogContext.CHANNEL_ID));
    }

    /** clear() empties the MDC map. */
    @Test
    public void clear_emptiesAllKeys() {
        ThreadContext.put(LogContext.CHANNEL_ID, "c");
        ThreadContext.put(LogContext.MESSAGE_ID, "1");
        LogContext.clear();
        assertNull(ThreadContext.get(LogContext.CHANNEL_ID));
        assertNull(ThreadContext.get(LogContext.MESSAGE_ID));
    }

    /** ChannelTask-style scope: connector with a null name but a metaDataId. */
    @Test
    public void connectorScope_withNullName_setsOnlyMetaDataId() {
        try (LogContext.Scope k = LogContext.connector(null, 3)) {
            assertNull(ThreadContext.get(LogContext.CONNECTOR));
            assertEquals("3", ThreadContext.get(LogContext.META_DATA_ID));
        }
        assertNull(ThreadContext.get(LogContext.META_DATA_ID));
    }

    /** Two stacked scopes setting disjoint keys: outer keys must survive inner close. */
    @Test
    public void disjointStackedScopes_restoreIndependently() {
        try (LogContext.Scope outer = LogContext.channel("c-outer", "Outer")) {
            try (LogContext.Scope inner = LogContext.connector("destA", 5)) {
                assertEquals("c-outer", ThreadContext.get(LogContext.CHANNEL_ID));
                assertEquals("destA", ThreadContext.get(LogContext.CONNECTOR));
                assertEquals("5", ThreadContext.get(LogContext.META_DATA_ID));
            }
            // Inner closed: connector keys gone, channel keys still set.
            assertEquals("c-outer", ThreadContext.get(LogContext.CHANNEL_ID));
            assertNull(ThreadContext.get(LogContext.CONNECTOR));
            assertNull(ThreadContext.get(LogContext.META_DATA_ID));
        }
        assertNull(ThreadContext.get(LogContext.CHANNEL_ID));
    }

    /**
     * When disabled (the production default, set by the server from log.channelcontext.enabled),
     * every factory must be a no-op so no MDC keys are written and log lines stay in the legacy
     * format. The returned no-op scope must also close cleanly.
     */
    @Test
    public void disabled_writesNoContext() {
        LogContext.setEnabled(false);
        try {
            try (LogContext.Scope c = LogContext.channel("abc", "Channel A");
                 LogContext.Scope k = LogContext.connector("dest1", 7);
                 LogContext.Scope m = LogContext.message(42L);
                 LogContext.Scope s = LogContext.script("FILTER", 101)) {
                assertNull(ThreadContext.get(LogContext.CHANNEL_ID));
                assertNull(ThreadContext.get(LogContext.CHANNEL_NAME));
                assertNull(ThreadContext.get(LogContext.CONNECTOR));
                assertNull(ThreadContext.get(LogContext.META_DATA_ID));
                assertNull(ThreadContext.get(LogContext.MESSAGE_ID));
                assertNull(ThreadContext.get(LogContext.SCRIPT_PHASE));
                assertNull(ThreadContext.get(LogContext.SCRIPT_LINE));
            }
            assertNull(ThreadContext.get(LogContext.CHANNEL_ID));
        } finally {
            LogContext.setEnabled(true);
        }
    }
}
