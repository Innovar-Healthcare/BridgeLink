/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 alert POJO coverage — AlertModel constructor/getter/setter/getPurgedProperties/toString.
 */

package com.mirth.connect.model.alert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class AlertModelTest {

    // Helper: create a minimal AlertModel
    private AlertModel createModel() {
        AlertActionGroup group = new AlertActionGroup();
        group.setSubject("Test Subject");
        DefaultTrigger trigger = new DefaultTrigger();
        return new AlertModel(trigger, group);
    }

    // ------------------------------------------------------------------
    // Constructor: id is non-null UUID, enabled=false, trigger+group set
    // ------------------------------------------------------------------

    @Test
    public void constructor_idNotNull() {
        AlertModel m = createModel();
        assertNotNull(m.getId());
        assertFalse(m.isEnabled());
    }

    @Test
    public void constructor_actionGroupsContainsSingleGroup() {
        AlertActionGroup group = new AlertActionGroup();
        group.setSubject("S");
        AlertModel m = new AlertModel(new DefaultTrigger(), group);
        assertNotNull(m.getActionGroups());
        assertEquals(1, m.getActionGroups().size());
    }

    @Test
    public void constructor_propertiesMapNotNull() {
        AlertModel m = createModel();
        assertNotNull(m.getProperties());
        assertTrue(m.getProperties().isEmpty());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setId_roundTrip() {
        AlertModel m = createModel();
        m.setId("custom-alert-id");
        assertEquals("custom-alert-id", m.getId());
    }

    @Test
    public void setName_roundTrip() {
        AlertModel m = createModel();
        m.setName("Channel Error Alert");
        assertEquals("Channel Error Alert", m.getName());
    }

    @Test
    public void setEnabled_true() {
        AlertModel m = createModel();
        m.setEnabled(true);
        assertTrue(m.isEnabled());
    }

    @Test
    public void setEnabled_false_afterTrue() {
        AlertModel m = createModel();
        m.setEnabled(true);
        m.setEnabled(false);
        assertFalse(m.isEnabled());
    }

    @Test
    public void setTrigger_roundTrip() {
        AlertModel m = createModel();
        DefaultTrigger trigger2 = new DefaultTrigger();
        trigger2.setRegex("ERROR.*");
        m.setTrigger(trigger2);
        assertNotNull(m.getTrigger());
        assertTrue(m.getTrigger() instanceof DefaultTrigger);
        assertEquals("ERROR.*", ((DefaultTrigger) m.getTrigger()).getRegex());
    }

    @Test
    public void setActionGroups_roundTrip() {
        AlertModel m = createModel();
        List<AlertActionGroup> groups = new ArrayList<AlertActionGroup>();
        AlertActionGroup g1 = new AlertActionGroup();
        g1.setSubject("Group 1");
        AlertActionGroup g2 = new AlertActionGroup();
        g2.setSubject("Group 2");
        groups.add(g1);
        groups.add(g2);
        m.setActionGroups(groups);
        assertEquals(2, m.getActionGroups().size());
        assertEquals("Group 1", m.getActionGroups().get(0).getSubject());
    }

    @Test
    public void setProperties_roundTrip() {
        AlertModel m = createModel();
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("throttle", 100);
        props.put("channel", "chan-001");
        m.setProperties(props);
        assertEquals(100, m.getProperties().get("throttle"));
        assertEquals("chan-001", m.getProperties().get("channel"));
    }

    // ------------------------------------------------------------------
    // toString: non-null, contains field values
    // ------------------------------------------------------------------

    @Test
    public void toString_returnsNonEmpty() {
        AlertModel m = createModel();
        m.setName("Test Alert");
        String str = m.toString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
        assertTrue(str.contains("Test Alert"));
    }

    // ------------------------------------------------------------------
    // getPurgedProperties: contains expected keys
    // ------------------------------------------------------------------

    @Test
    public void getPurgedProperties_containsExpectedKeys() {
        AlertModel m = createModel();
        m.setId("purge-test-id");
        m.setName("Purge Alert");
        m.setEnabled(true);
        Map<String, Object> purged = m.getPurgedProperties();
        assertNotNull(purged);
        assertTrue(purged.containsKey("id"));
        assertTrue(purged.containsKey("nameChars"));
        assertTrue(purged.containsKey("enabled"));
        assertTrue(purged.containsKey("actionGroups"));
        assertEquals("purge-test-id", purged.get("id"));
        assertTrue((Boolean) purged.get("enabled"));
    }

    @Test
    public void getPurgedProperties_nullName_nameCharsZero() {
        AlertModel m = createModel();
        m.setName(null);
        Map<String, Object> purged = m.getPurgedProperties();
        assertNotNull(purged.get("nameChars"));
        assertEquals(0, purged.get("nameChars"));
    }
}
