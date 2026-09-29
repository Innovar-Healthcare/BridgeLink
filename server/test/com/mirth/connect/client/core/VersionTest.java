/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.client.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Proves the v26_6_1 rung (IRT-2329): a persisted SCHEMA_INFO.VERSION of "26.6.1" (written by a
 * deployed release/26.6.1) now resolves to a real enum constant sitting between v26_6_0 and
 * v26_9_0, so the migration ladder walks 26.6.0 -> 26.6.1 -> 26.9.0 instead of restarting at V0.
 */
public class VersionTest {

    @Test
    public void fromString26_6_1NextVersionIs26_9_0() {
        // Criterion 1: fromString("26.6.1").getNextVersion() resolves to v26_9_0.
        assertEquals(Version.v26_9_0, Version.fromString("26.6.1").getNextVersion());
    }

    @Test
    public void fromString26_6_0NextVersionIs26_6_1() {
        // Criterion 3a: the 26.6.0 rung now steps to the new 26.6.1 rung, not straight to 26.9.0.
        assertEquals(Version.v26_6_1, Version.fromString("26.6.0").getNextVersion());
    }

    @Test
    public void v26_6_1NextVersionIs26_9_0() {
        // Criterion 3b: the new rung itself steps forward to v26_9_0.
        assertEquals(Version.v26_9_0, Version.v26_6_1.getNextVersion());
    }

    @Test
    public void getLatestStillReturns26_9_0() {
        // The mid-list insertion must not move the terminal constant.
        assertEquals(Version.v26_9_0, Version.getLatest());
    }

    @Test
    public void fromString26_6_1ResolvesToTheNewConstant() {
        Version version = Version.fromString("26.6.1");

        assertNotNull("fromString(\"26.6.1\") must resolve to a real constant, not null", version);
        assertEquals("26.6.1", version.getSchemaVersion());
    }
}
