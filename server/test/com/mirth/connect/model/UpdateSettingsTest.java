/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 model POJO coverage — UpdateSettings Properties round-trip and getter/setters.
 */

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;

public class UpdateSettingsTest {

    // ------------------------------------------------------------------
    // Default constructor
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_statsEnabledFalse() {
        UpdateSettings s = new UpdateSettings();
        assertFalse(s.getStatsEnabled());
        assertNull(s.getLastStatsTime());
    }

    // ------------------------------------------------------------------
    // Properties constructor: empty Properties → defaults
    // ------------------------------------------------------------------

    @Test
    public void propertiesConstructor_emptyProperties_statsEnabledNull() {
        // intToBooleanObject(null) returns null
        UpdateSettings s = new UpdateSettings(new Properties());
        assertNull(s.getStatsEnabled());
        assertNull(s.getLastStatsTime());
    }

    // ------------------------------------------------------------------
    // Properties constructor: explicit values
    // ------------------------------------------------------------------

    @Test
    public void propertiesConstructor_statsEnabled_1() {
        Properties p = new Properties();
        p.setProperty("stats.enabled", "1");
        UpdateSettings s = new UpdateSettings(p);
        assertTrue(s.getStatsEnabled());
    }

    @Test
    public void propertiesConstructor_statsEnabled_0() {
        Properties p = new Properties();
        p.setProperty("stats.enabled", "0");
        UpdateSettings s = new UpdateSettings(p);
        assertFalse(s.getStatsEnabled());
    }

    @Test
    public void propertiesConstructor_lastStatsTime_set() {
        Properties p = new Properties();
        p.setProperty("stats.time", "1718000000000");
        UpdateSettings s = new UpdateSettings(p);
        assertEquals(Long.valueOf(1718000000000L), s.getLastStatsTime());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setStatsEnabled_true_getStatsEnabled() {
        UpdateSettings s = new UpdateSettings();
        s.setStatsEnabled(true);
        assertTrue(s.getStatsEnabled());
    }

    @Test
    public void setStatsEnabled_false_getStatsEnabled() {
        UpdateSettings s = new UpdateSettings();
        s.setStatsEnabled(false);
        assertFalse(s.getStatsEnabled());
    }

    @Test
    public void setLastStatsTime_roundTrip() {
        UpdateSettings s = new UpdateSettings();
        s.setLastStatsTime(9999L);
        assertEquals(Long.valueOf(9999L), s.getLastStatsTime());
    }

    @Test
    public void setLastStatsTime_null_roundTrip() {
        UpdateSettings s = new UpdateSettings();
        s.setLastStatsTime(12345L);
        s.setLastStatsTime(null);
        assertNull(s.getLastStatsTime());
    }

    // ------------------------------------------------------------------
    // getProperties: round-trip through Properties
    // ------------------------------------------------------------------

    @Test
    public void getProperties_statsEnabledTrue_containsKey() {
        UpdateSettings s = new UpdateSettings();
        s.setStatsEnabled(true);
        s.setLastStatsTime(1000L);
        Properties p = s.getProperties();
        assertNotNull(p);
        assertTrue(p.containsKey("stats.enabled"));
        assertTrue(p.containsKey("stats.time"));
    }

    @Test
    public void getProperties_statsEnabledNull_emptyProperties() {
        UpdateSettings s = new UpdateSettings();
        s.setStatsEnabled(null);
        Properties p = s.getProperties();
        assertNotNull(p);
        assertFalse(p.containsKey("stats.enabled"));
        assertFalse(p.containsKey("stats.time"));
    }

    // ------------------------------------------------------------------
    // getPurgedProperties: returns non-null empty map
    // ------------------------------------------------------------------

    @Test
    public void getPurgedProperties_returnsEmptyMap() {
        UpdateSettings s = new UpdateSettings();
        s.setStatsEnabled(true);
        assertNotNull(s.getPurgedProperties());
        // UpdateSettings.getPurgedProperties() returns empty HashMap
        assertTrue(s.getPurgedProperties().isEmpty());
    }

    // ------------------------------------------------------------------
    // toAuditString: non-null, non-empty
    // ------------------------------------------------------------------

    @Test
    public void toAuditString_returnsNonEmpty() {
        UpdateSettings s = new UpdateSettings();
        s.setStatsEnabled(true);
        String str = s.toAuditString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
    }
}
