/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 alert POJO coverage — AlertAction getter/setter/getPurgedProperties/toString.
 */

package com.mirth.connect.model.alert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class AlertActionTest {

    // ------------------------------------------------------------------
    // Constructor: sets protocol and recipient
    // ------------------------------------------------------------------

    @Test
    public void constructor_setsProtocolAndRecipient() {
        AlertAction a = new AlertAction("Email", "admin@example.com");
        assertEquals("Email", a.getProtocol());
        assertEquals("admin@example.com", a.getRecipient());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setProtocol_roundTrip() {
        AlertAction a = new AlertAction("Email", "user@example.com");
        a.setProtocol("SMS");
        assertEquals("SMS", a.getProtocol());
    }

    @Test
    public void setRecipient_roundTrip() {
        AlertAction a = new AlertAction("Email", "user@example.com");
        a.setRecipient("other@example.com");
        assertEquals("other@example.com", a.getRecipient());
    }

    // ------------------------------------------------------------------
    // toString: non-null, non-empty, contains field names
    // ------------------------------------------------------------------

    @Test
    public void toString_containsFieldValues() {
        AlertAction a = new AlertAction("Email", "test@example.com");
        String str = a.toString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
        assertTrue(str.contains("Email"));
        assertTrue(str.contains("test@example.com"));
    }

    // ------------------------------------------------------------------
    // getPurgedProperties: contains protocol
    // ------------------------------------------------------------------

    @Test
    public void getPurgedProperties_containsProtocol() {
        AlertAction a = new AlertAction("Email", "user@example.com");
        Map<String, Object> purged = a.getPurgedProperties();
        assertNotNull(purged);
        assertTrue(purged.containsKey("protocol"));
        assertEquals("Email", purged.get("protocol"));
    }

    @Test
    public void getPurgedProperties_nullProtocol_containsNullValue() {
        AlertAction a = new AlertAction(null, "user@example.com");
        Map<String, Object> purged = a.getPurgedProperties();
        assertTrue(purged.containsKey("protocol"));
        // null protocol stored
    }

    // ------------------------------------------------------------------
    // Multiple mutations: last value wins
    // ------------------------------------------------------------------

    @Test
    public void multipleSetProtocol_lastValueWins() {
        AlertAction a = new AlertAction("Email", "u@example.com");
        a.setProtocol("Slack");
        a.setProtocol("Teams");
        assertEquals("Teams", a.getProtocol());
    }
}
