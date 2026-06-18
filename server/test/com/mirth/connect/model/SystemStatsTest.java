/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 model POJO coverage — SystemStats getter/setter round-trips.
 */

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Calendar;

import org.junit.Test;

public class SystemStatsTest {

    @Test
    public void defaultConstructor_allFieldsDefault() {
        SystemStats s = new SystemStats();
        assertNull(s.getTimestamp());
        assertEquals(0.0, s.getCpuUsagePct(), 0.001);
        assertEquals(0L, s.getAllocatedMemoryBytes());
        assertEquals(0L, s.getFreeMemoryBytes());
        assertEquals(0L, s.getMaxMemoryBytes());
        assertEquals(0L, s.getDiskFreeBytes());
        assertEquals(0L, s.getDiskTotalBytes());
    }

    @Test
    public void setTimestamp_getTimestamp_roundTrip() {
        SystemStats s = new SystemStats();
        Calendar cal = Calendar.getInstance();
        s.setTimestamp(cal);
        assertNotNull(s.getTimestamp());
        assertEquals(cal, s.getTimestamp());
    }

    @Test
    public void setCpuUsagePct_roundTrip() {
        SystemStats s = new SystemStats();
        s.setCpuUsagePct(45.5);
        assertEquals(45.5, s.getCpuUsagePct(), 0.001);
    }

    @Test
    public void setAllocatedMemoryBytes_roundTrip() {
        SystemStats s = new SystemStats();
        s.setAllocatedMemoryBytes(1073741824L);
        assertEquals(1073741824L, s.getAllocatedMemoryBytes());
    }

    @Test
    public void setFreeMemoryBytes_roundTrip() {
        SystemStats s = new SystemStats();
        s.setFreeMemoryBytes(536870912L);
        assertEquals(536870912L, s.getFreeMemoryBytes());
    }

    @Test
    public void setMaxMemoryBytes_roundTrip() {
        SystemStats s = new SystemStats();
        s.setMaxMemoryBytes(2147483648L);
        assertEquals(2147483648L, s.getMaxMemoryBytes());
    }

    @Test
    public void setDiskFreeBytes_roundTrip() {
        SystemStats s = new SystemStats();
        s.setDiskFreeBytes(10737418240L);
        assertEquals(10737418240L, s.getDiskFreeBytes());
    }

    @Test
    public void setDiskTotalBytes_roundTrip() {
        SystemStats s = new SystemStats();
        s.setDiskTotalBytes(107374182400L);
        assertEquals(107374182400L, s.getDiskTotalBytes());
    }

    @Test
    public void setZeroValues_roundTrip() {
        SystemStats s = new SystemStats();
        s.setCpuUsagePct(0.0);
        s.setAllocatedMemoryBytes(0L);
        s.setFreeMemoryBytes(0L);
        s.setMaxMemoryBytes(0L);
        s.setDiskFreeBytes(0L);
        s.setDiskTotalBytes(0L);
        assertEquals(0.0, s.getCpuUsagePct(), 0.001);
        assertEquals(0L, s.getAllocatedMemoryBytes());
        assertEquals(0L, s.getDiskTotalBytes());
    }
}
