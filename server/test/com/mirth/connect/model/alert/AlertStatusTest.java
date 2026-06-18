/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 alert POJO coverage — AlertStatus constructor/getter/setter/equals.
 */

package com.mirth.connect.model.alert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlertStatusTest {

    // ------------------------------------------------------------------
    // Default constructor: all fields null/default
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_allNull() {
        AlertStatus s = new AlertStatus();
        assertNull(s.getId());
        assertNull(s.getName());
        assertFalse(s.isEnabled());
        assertNull(s.getAlertedCount());
    }

    // ------------------------------------------------------------------
    // Full constructor
    // ------------------------------------------------------------------

    @Test
    public void fullConstructor_setsAllFields() {
        AlertStatus s = new AlertStatus("alert-001", "Critical Error Alert", true, 5);
        assertEquals("alert-001", s.getId());
        assertEquals("Critical Error Alert", s.getName());
        assertTrue(s.isEnabled());
        assertEquals(Integer.valueOf(5), s.getAlertedCount());
    }

    @Test
    public void fullConstructor_disabledAlert_alertedCountSet() {
        AlertStatus s = new AlertStatus("alert-002", "Warning Alert", false, 0);
        assertFalse(s.isEnabled());
        assertEquals(Integer.valueOf(0), s.getAlertedCount());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setId_roundTrip() {
        AlertStatus s = new AlertStatus();
        s.setId("test-id");
        assertEquals("test-id", s.getId());
    }

    @Test
    public void setName_roundTrip() {
        AlertStatus s = new AlertStatus();
        s.setName("My Alert");
        assertEquals("My Alert", s.getName());
    }

    @Test
    public void setEnabled_true_roundTrip() {
        AlertStatus s = new AlertStatus();
        s.setEnabled(true);
        assertTrue(s.isEnabled());
    }

    @Test
    public void setEnabled_false_roundTrip() {
        AlertStatus s = new AlertStatus();
        s.setEnabled(true);
        s.setEnabled(false);
        assertFalse(s.isEnabled());
    }

    @Test
    public void setAlertedCount_roundTrip() {
        AlertStatus s = new AlertStatus();
        s.setAlertedCount(42);
        assertEquals(Integer.valueOf(42), s.getAlertedCount());
    }

    // ------------------------------------------------------------------
    // equals: all fields must match
    // ------------------------------------------------------------------

    @Test
    public void equals_identicalFields_returnsTrue() {
        AlertStatus s1 = new AlertStatus("id-1", "Alert A", true, 3);
        AlertStatus s2 = new AlertStatus("id-1", "Alert A", true, 3);
        assertTrue(s1.equals(s2));
    }

    @Test
    public void equals_differentId_returnsFalse() {
        AlertStatus s1 = new AlertStatus("id-1", "Alert A", true, 3);
        AlertStatus s2 = new AlertStatus("id-2", "Alert A", true, 3);
        assertFalse(s1.equals(s2));
    }

    @Test
    public void equals_differentName_returnsFalse() {
        AlertStatus s1 = new AlertStatus("id-1", "Alert A", true, 3);
        AlertStatus s2 = new AlertStatus("id-1", "Alert B", true, 3);
        assertFalse(s1.equals(s2));
    }

    @Test
    public void equals_differentEnabled_returnsFalse() {
        AlertStatus s1 = new AlertStatus("id-1", "Alert A", true, 3);
        AlertStatus s2 = new AlertStatus("id-1", "Alert A", false, 3);
        assertFalse(s1.equals(s2));
    }

    @Test
    public void equals_differentAlertedCount_returnsFalse() {
        AlertStatus s1 = new AlertStatus("id-1", "Alert A", true, 3);
        AlertStatus s2 = new AlertStatus("id-1", "Alert A", true, 7);
        assertFalse(s1.equals(s2));
    }

    @Test
    public void equals_nonAlertStatusObject_returnsFalse() {
        AlertStatus s = new AlertStatus("id-1", "Alert A", true, 0);
        assertFalse(s.equals("not an AlertStatus"));
    }

    @Test
    public void equals_nullFields_handledByObjects_equals() {
        AlertStatus s1 = new AlertStatus();
        AlertStatus s2 = new AlertStatus();
        assertTrue(s1.equals(s2)); // both null id/name/count
    }

    // ------------------------------------------------------------------
    // Mutation guard: setters update individual fields
    // ------------------------------------------------------------------

    @Test
    public void setAlertedCount_overwrite_lastValueWins() {
        AlertStatus s = new AlertStatus("a", "b", true, 0);
        s.setAlertedCount(10);
        s.setAlertedCount(99);
        assertEquals(Integer.valueOf(99), s.getAlertedCount());
    }
}
