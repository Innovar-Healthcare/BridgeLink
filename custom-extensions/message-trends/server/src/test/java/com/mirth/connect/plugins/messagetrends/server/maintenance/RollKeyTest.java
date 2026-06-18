/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1056: JUnit coverage improvement — RollKey.
 */

package com.mirth.connect.plugins.messagetrends.server.maintenance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

/**
 * Unit tests for {@link RollKey}.
 *
 * Tests cover constructor, getters, equals, hashCode, toString.
 */
public class RollKeyTest {

    private static final String SERVER_ID   = "server-1";
    private static final String CHANNEL_ID  = "channel-abc";
    private static final String CONNECTOR_ID = "connector-001";
    private static final Date   BUCKET_TS   = new Date(1700000000000L);
    private static final int    BUCKET_SIZE = 60;

    // ------------------------------------------------------------------
    // Constructor and getters
    // ------------------------------------------------------------------

    @Test
    public void constructor_storesAllFields() {
        RollKey k = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        assertEquals(SERVER_ID, k.getServerId());
        assertEquals(CHANNEL_ID, k.getChannelId());
        assertEquals(CONNECTOR_ID, k.getConnectorId());
        assertEquals(BUCKET_TS, k.getBucketTs());
        assertEquals(BUCKET_SIZE, k.getBucketSizeMinutes());
    }

    @Test
    public void constructor_allowsNullConnectorId() {
        RollKey k = new RollKey(SERVER_ID, CHANNEL_ID, null, BUCKET_TS, BUCKET_SIZE);
        assertTrue(k.getConnectorId() == null);
    }

    // ------------------------------------------------------------------
    // equals
    // ------------------------------------------------------------------

    @Test
    public void equals_sameFieldValues_returnsTrue() {
        RollKey k1 = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        RollKey k2 = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        assertTrue(k1.equals(k2));
    }

    @Test
    public void equals_sameInstance_returnsTrue() {
        RollKey k = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        assertTrue(k.equals(k));
    }

    @Test
    public void equals_differentServerId_returnsFalse() {
        RollKey k1 = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        RollKey k2 = new RollKey("other-server", CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        assertFalse(k1.equals(k2));
    }

    @Test
    public void equals_differentChannelId_returnsFalse() {
        RollKey k1 = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        RollKey k2 = new RollKey(SERVER_ID, "other-channel", CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        assertFalse(k1.equals(k2));
    }

    @Test
    public void equals_differentConnectorId_returnsFalse() {
        RollKey k1 = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        RollKey k2 = new RollKey(SERVER_ID, CHANNEL_ID, "other-connector", BUCKET_TS, BUCKET_SIZE);
        assertFalse(k1.equals(k2));
    }

    @Test
    public void equals_differentBucketTs_returnsFalse() {
        RollKey k1 = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        RollKey k2 = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, new Date(0L), BUCKET_SIZE);
        assertFalse(k1.equals(k2));
    }

    @Test
    public void equals_differentBucketSizeMinutes_returnsFalse() {
        RollKey k1 = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, 60);
        RollKey k2 = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, 15);
        assertFalse(k1.equals(k2));
    }

    @Test
    public void equals_null_returnsFalse() {
        RollKey k = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        assertFalse(k.equals(null));
    }

    @Test
    public void equals_differentType_returnsFalse() {
        RollKey k = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        assertFalse(k.equals("string"));
    }

    @Test
    public void equals_bothNullConnectorId_returnsTrue() {
        RollKey k1 = new RollKey(SERVER_ID, CHANNEL_ID, null, BUCKET_TS, BUCKET_SIZE);
        RollKey k2 = new RollKey(SERVER_ID, CHANNEL_ID, null, BUCKET_TS, BUCKET_SIZE);
        assertTrue(k1.equals(k2));
    }

    // ------------------------------------------------------------------
    // hashCode
    // ------------------------------------------------------------------

    @Test
    public void hashCode_equalObjects_sameHashCode() {
        RollKey k1 = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        RollKey k2 = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        assertEquals(k1.hashCode(), k2.hashCode());
    }

    // ------------------------------------------------------------------
    // toString
    // ------------------------------------------------------------------

    @Test
    public void toString_isNotNull() {
        RollKey k = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        assertNotNull(k.toString());
    }

    @Test
    public void toString_containsServerId() {
        RollKey k = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        assertTrue(k.toString().contains(SERVER_ID));
    }

    @Test
    public void toString_containsChannelId() {
        RollKey k = new RollKey(SERVER_ID, CHANNEL_ID, CONNECTOR_ID, BUCKET_TS, BUCKET_SIZE);
        assertTrue(k.toString().contains(CHANNEL_ID));
    }
}
