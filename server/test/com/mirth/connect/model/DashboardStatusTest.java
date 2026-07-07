/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 model POJO coverage — DashboardStatus getter/setter/getKey/toString.
 */

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.donkey.model.message.Status;

import org.junit.Test;

public class DashboardStatusTest {

    // ------------------------------------------------------------------
    // Default constructor: defaults
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_queuedIsZero() {
        DashboardStatus ds = new DashboardStatus();
        assertEquals(Long.valueOf(0L), ds.getQueued());
    }

    @Test
    public void defaultConstructor_childStatusesEmpty() {
        DashboardStatus ds = new DashboardStatus();
        assertNotNull(ds.getChildStatuses());
        assertTrue(ds.getChildStatuses().isEmpty());
    }

    @Test
    public void defaultConstructor_waitForPreviousFalse() {
        DashboardStatus ds = new DashboardStatus();
        assertFalse(ds.isWaitForPrevious());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setChannelId_roundTrip() {
        DashboardStatus ds = new DashboardStatus();
        ds.setChannelId("channel-abc");
        assertEquals("channel-abc", ds.getChannelId());
    }

    @Test
    public void setName_roundTrip() {
        DashboardStatus ds = new DashboardStatus();
        ds.setName("My Channel");
        assertEquals("My Channel", ds.getName());
    }

    @Test
    public void setState_deployed() {
        DashboardStatus ds = new DashboardStatus();
        ds.setState(DeployedState.STARTED);
        assertEquals(DeployedState.STARTED, ds.getState());
    }

    @Test
    public void setState_stopped() {
        DashboardStatus ds = new DashboardStatus();
        ds.setState(DeployedState.STOPPED);
        assertEquals(DeployedState.STOPPED, ds.getState());
    }

    @Test
    public void setDeployedDate_roundTrip() {
        DashboardStatus ds = new DashboardStatus();
        Calendar cal = Calendar.getInstance();
        ds.setDeployedDate(cal);
        assertEquals(cal, ds.getDeployedDate());
    }

    @Test
    public void setDeployedRevisionDelta_roundTrip() {
        DashboardStatus ds = new DashboardStatus();
        ds.setDeployedRevisionDelta(3);
        assertEquals(Integer.valueOf(3), ds.getDeployedRevisionDelta());
    }

    @Test
    public void setCodeTemplatesChanged_true() {
        DashboardStatus ds = new DashboardStatus();
        ds.setCodeTemplatesChanged(Boolean.TRUE);
        assertEquals(Boolean.TRUE, ds.getCodeTemplatesChanged());
    }

    @Test
    public void setStatistics_roundTrip() {
        DashboardStatus ds = new DashboardStatus();
        Map<Status, Long> stats = new HashMap<Status, Long>();
        stats.put(Status.SENT, 100L);
        stats.put(Status.ERROR, 5L);
        ds.setStatistics(stats);
        assertEquals(Long.valueOf(100L), ds.getStatistics().get(Status.SENT));
    }

    @Test
    public void setLifetimeStatistics_roundTrip() {
        DashboardStatus ds = new DashboardStatus();
        Map<Status, Long> stats = new HashMap<Status, Long>();
        stats.put(Status.SENT, 1000L);
        ds.setLifetimeStatistics(stats);
        assertEquals(Long.valueOf(1000L), ds.getLifetimeStatistics().get(Status.SENT));
    }

    @Test
    public void setMetaDataId_roundTrip() {
        DashboardStatus ds = new DashboardStatus();
        ds.setMetaDataId(0);
        assertEquals(Integer.valueOf(0), ds.getMetaDataId());
    }

    @Test
    public void setQueueEnabled_true() {
        DashboardStatus ds = new DashboardStatus();
        ds.setQueueEnabled(true);
        assertTrue(ds.isQueueEnabled());
    }

    @Test
    public void setQueueEnabled_false() {
        DashboardStatus ds = new DashboardStatus();
        ds.setQueueEnabled(false);
        assertFalse(ds.isQueueEnabled());
    }

    @Test
    public void setQueued_roundTrip() {
        DashboardStatus ds = new DashboardStatus();
        ds.setQueued(42L);
        assertEquals(Long.valueOf(42L), ds.getQueued());
    }

    @Test
    public void setWaitForPrevious_true() {
        DashboardStatus ds = new DashboardStatus();
        ds.setWaitForPrevious(true);
        assertTrue(ds.isWaitForPrevious());
    }

    @Test
    public void setStatusType_channel() {
        DashboardStatus ds = new DashboardStatus();
        ds.setStatusType(DashboardStatus.StatusType.CHANNEL);
        assertEquals(DashboardStatus.StatusType.CHANNEL, ds.getStatusType());
    }

    @Test
    public void setStatusType_destinationConnector() {
        DashboardStatus ds = new DashboardStatus();
        ds.setStatusType(DashboardStatus.StatusType.DESTINATION_CONNECTOR);
        assertEquals(DashboardStatus.StatusType.DESTINATION_CONNECTOR, ds.getStatusType());
    }

    // ------------------------------------------------------------------
    // getKey: returns channelId-metaDataId-statusType
    // ------------------------------------------------------------------

    @Test
    public void getKey_returnsExpectedFormat() {
        DashboardStatus ds = new DashboardStatus();
        ds.setChannelId("chan-001");
        ds.setMetaDataId(0);
        ds.setStatusType(DashboardStatus.StatusType.CHANNEL);
        String key = ds.getKey();
        assertNotNull(key);
        assertTrue(key.contains("chan-001"));
        assertTrue(key.contains("0"));
        assertTrue(key.contains("CHANNEL"));
    }

    // ------------------------------------------------------------------
    // toString: non-null
    // ------------------------------------------------------------------

    @Test
    public void toString_nonNull() {
        DashboardStatus ds = new DashboardStatus();
        ds.setChannelId("test-chan");
        ds.setName("Test Channel");
        String str = ds.toString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
    }

    // ------------------------------------------------------------------
    // StatusType enum values
    // ------------------------------------------------------------------

    @Test
    public void statusType_chainValue() {
        assertEquals(DashboardStatus.StatusType.CHAIN, DashboardStatus.StatusType.valueOf("CHAIN"));
    }

    @Test
    public void statusType_sourceConnectorValue() {
        assertEquals(DashboardStatus.StatusType.SOURCE_CONNECTOR, DashboardStatus.StatusType.valueOf("SOURCE_CONNECTOR"));
    }

    @Test
    public void statusType_values_has4Types() {
        assertEquals(4, DashboardStatus.StatusType.values().length);
    }
}
