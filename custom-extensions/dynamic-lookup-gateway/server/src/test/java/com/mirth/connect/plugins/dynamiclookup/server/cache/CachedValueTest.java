/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1056: JUnit coverage improvement — CachedValue model.
 */

package com.mirth.connect.plugins.dynamiclookup.server.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

/**
 * Unit tests for {@link CachedValue}.
 *
 * Tests constructor, getters, and toString.
 */
public class CachedValueTest {

    @Test
    public void constructor_storesValueAndTimestamp() {
        Date now = new Date();
        CachedValue cv = new CachedValue("hello", now);
        assertEquals("hello", cv.getValue());
        assertEquals(now, cv.getUpdatedAt());
    }

    @Test
    public void constructor_allowsNullValue() {
        CachedValue cv = new CachedValue(null, new Date());
        assertNull(cv.getValue());
    }

    @Test
    public void constructor_allowsNullDate() {
        CachedValue cv = new CachedValue("some-value", null);
        assertNull(cv.getUpdatedAt());
        assertEquals("some-value", cv.getValue());
    }

    @Test
    public void toString_containsValueAndDate() {
        Date now = new Date();
        CachedValue cv = new CachedValue("test-val", now);
        String s = cv.toString();
        assertNotNull(s);
        assertTrue(s.contains("test-val"));
    }

    @Test
    public void toString_nullValueDoesNotThrow() {
        CachedValue cv = new CachedValue(null, null);
        String s = cv.toString();
        assertNotNull(s);
    }

    @Test
    public void getValue_returnsExactString() {
        String val = "lookup-result-XYZ";
        CachedValue cv = new CachedValue(val, null);
        assertEquals(val, cv.getValue());
    }

    @Test
    public void getUpdatedAt_returnsSameReferenceAsConstructed() {
        Date ts = new Date(1700000000000L);
        CachedValue cv = new CachedValue("v", ts);
        assertEquals(ts, cv.getUpdatedAt());
        assertEquals(1700000000000L, cv.getUpdatedAt().getTime());
    }
}
