/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 model POJO coverage — DebugUsage constructor/getter/setter/equals.
 */

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DebugUsageTest {

    // ------------------------------------------------------------------
    // Default constructor: integer fields default to 0, serverId to null
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_integerFieldsAreZero() {
        DebugUsage d = new DebugUsage();
        assertEquals(Integer.valueOf(0), d.getId());
        assertNull(d.getServerId());
        assertEquals(Integer.valueOf(0), d.getDuppCount());
        assertEquals(Integer.valueOf(0), d.getAttachBatchCount());
        assertEquals(Integer.valueOf(0), d.getSourceConnectorCount());
        assertEquals(Integer.valueOf(0), d.getSourceFilterTransCount());
        assertEquals(Integer.valueOf(0), d.getDestinationFilterTransCount());
        assertEquals(Integer.valueOf(0), d.getDestinationConnectorCount());
        assertEquals(Integer.valueOf(0), d.getResponseCount());
        assertEquals(Integer.valueOf(0), d.getInvocationCount());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setId_getId_roundTrip() {
        DebugUsage d = new DebugUsage();
        d.setId(42);
        assertEquals(Integer.valueOf(42), d.getId());
    }

    @Test
    public void setServerId_getServerId_roundTrip() {
        DebugUsage d = new DebugUsage();
        d.setServerId("server-uuid-001");
        assertEquals("server-uuid-001", d.getServerId());
    }

    @Test
    public void setDuppCount_getDuppCount_roundTrip() {
        DebugUsage d = new DebugUsage();
        d.setDuppCount(5);
        assertEquals(Integer.valueOf(5), d.getDuppCount());
    }

    @Test
    public void setAttachBatchCount_getAttachBatchCount_roundTrip() {
        DebugUsage d = new DebugUsage();
        d.setAttachBatchCount(3);
        assertEquals(Integer.valueOf(3), d.getAttachBatchCount());
    }

    @Test
    public void setSourceConnectorCount_getSourceConnectorCount_roundTrip() {
        DebugUsage d = new DebugUsage();
        d.setSourceConnectorCount(10);
        assertEquals(Integer.valueOf(10), d.getSourceConnectorCount());
    }

    @Test
    public void setSourceFilterTransCount_roundTrip() {
        DebugUsage d = new DebugUsage();
        d.setSourceFilterTransCount(7);
        assertEquals(Integer.valueOf(7), d.getSourceFilterTransCount());
    }

    @Test
    public void setDestinationFilterTransCount_roundTrip() {
        DebugUsage d = new DebugUsage();
        d.setDestinationFilterTransCount(4);
        assertEquals(Integer.valueOf(4), d.getDestinationFilterTransCount());
    }

    @Test
    public void setDestinationConnectorCount_roundTrip() {
        DebugUsage d = new DebugUsage();
        d.setDestinationConnectorCount(8);
        assertEquals(Integer.valueOf(8), d.getDestinationConnectorCount());
    }

    @Test
    public void setResponseCount_getResponseCount_roundTrip() {
        DebugUsage d = new DebugUsage();
        d.setResponseCount(20);
        assertEquals(Integer.valueOf(20), d.getResponseCount());
    }

    @Test
    public void setInvocationCount_getInvocationCount_roundTrip() {
        DebugUsage d = new DebugUsage();
        d.setInvocationCount(100);
        assertEquals(Integer.valueOf(100), d.getInvocationCount());
    }

    // ------------------------------------------------------------------
    // equals: two instances with same field values are equal
    // ------------------------------------------------------------------

    @Test
    public void equals_sameFieldValues_returnsTrue() {
        DebugUsage d1 = new DebugUsage();
        d1.setId(1);
        d1.setServerId("srv-1");
        d1.setDuppCount(2);
        d1.setInvocationCount(10);

        DebugUsage d2 = new DebugUsage();
        d2.setId(1);
        d2.setServerId("srv-1");
        d2.setDuppCount(2);
        d2.setInvocationCount(10);

        assertTrue(d1.equals(d2));
    }

    @Test
    public void equals_differentValues_returnsFalse() {
        DebugUsage d1 = new DebugUsage();
        d1.setId(1);

        DebugUsage d2 = new DebugUsage();
        d2.setId(2);

        assertFalse(d1.equals(d2));
    }

    @Test
    public void equals_sameInstance_returnsTrue() {
        DebugUsage d = new DebugUsage();
        d.setServerId("test");
        assertTrue(d.equals(d));
    }

    // ------------------------------------------------------------------
    // toString: non-null, non-empty string
    // ------------------------------------------------------------------

    @Test
    public void toString_returnsNonEmpty() {
        DebugUsage d = new DebugUsage();
        d.setServerId("srv-2");
        d.setDuppCount(3);
        String str = d.toString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
    }

    // ------------------------------------------------------------------
    // Multiple field mutations: last-write-wins
    // ------------------------------------------------------------------

    @Test
    public void multipleSetterCalls_lastValueWins() {
        DebugUsage d = new DebugUsage();
        d.setDuppCount(1);
        d.setDuppCount(99);
        assertEquals(Integer.valueOf(99), d.getDuppCount());
    }
}
