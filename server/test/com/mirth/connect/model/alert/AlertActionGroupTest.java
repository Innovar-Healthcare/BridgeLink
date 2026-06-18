/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 alert POJO coverage — AlertActionGroup getter/setter/getPurgedProperties/toString.
 */

package com.mirth.connect.model.alert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class AlertActionGroupTest {

    // ------------------------------------------------------------------
    // Default constructor: empty actions list, null subject/template
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_emptyActionsNullSubjectTemplate() {
        AlertActionGroup g = new AlertActionGroup();
        assertNotNull(g.getActions());
        assertTrue(g.getActions().isEmpty());
        assertNull(g.getSubject());
        assertNull(g.getTemplate());
    }

    // ------------------------------------------------------------------
    // setSubject / getSubject round-trip
    // ------------------------------------------------------------------

    @Test
    public void setSubject_roundTrip() {
        AlertActionGroup g = new AlertActionGroup();
        g.setSubject("Alert: Critical Error");
        assertEquals("Alert: Critical Error", g.getSubject());
    }

    // ------------------------------------------------------------------
    // setTemplate / getTemplate round-trip
    // ------------------------------------------------------------------

    @Test
    public void setTemplate_roundTrip() {
        AlertActionGroup g = new AlertActionGroup();
        g.setTemplate("Error occurred: ${message}");
        assertEquals("Error occurred: ${message}", g.getTemplate());
    }

    // ------------------------------------------------------------------
    // setActions / getActions round-trip
    // ------------------------------------------------------------------

    @Test
    public void setActions_roundTrip() {
        AlertActionGroup g = new AlertActionGroup();
        List<AlertAction> actions = new ArrayList<AlertAction>();
        actions.add(new AlertAction("Email", "admin@example.com"));
        actions.add(new AlertAction("SMS", "+15555555555"));
        g.setActions(actions);

        assertEquals(2, g.getActions().size());
        assertEquals("Email", g.getActions().get(0).getProtocol());
        assertEquals("+15555555555", g.getActions().get(1).getRecipient());
    }

    @Test
    public void setActions_null_overwritesPrevious() {
        AlertActionGroup g = new AlertActionGroup();
        g.setActions(null);
        assertNull(g.getActions());
    }

    // ------------------------------------------------------------------
    // toString: non-null, non-empty, contains class name
    // ------------------------------------------------------------------

    @Test
    public void toString_returnsNonEmpty() {
        AlertActionGroup g = new AlertActionGroup();
        g.setSubject("Test Alert");
        g.setTemplate("An error occurred.");
        String str = g.toString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
        assertTrue(str.contains("Test Alert"));
    }

    // ------------------------------------------------------------------
    // getPurgedProperties: contains actions count and template lines
    // ------------------------------------------------------------------

    @Test
    public void getPurgedProperties_emptyActions_actionsKey() {
        AlertActionGroup g = new AlertActionGroup();
        g.setSubject("Subj");
        g.setTemplate("line1\nline2\nline3");
        Map<String, Object> purged = g.getPurgedProperties();
        assertNotNull(purged);
        assertTrue(purged.containsKey("actions"));
        assertTrue(purged.containsKey("templateLines"));
    }

    @Test
    public void getPurgedProperties_withActions_actionsListPresent() {
        AlertActionGroup g = new AlertActionGroup();
        List<AlertAction> actions = new ArrayList<AlertAction>();
        actions.add(new AlertAction("Email", "a@b.com"));
        g.setActions(actions);
        Map<String, Object> purged = g.getPurgedProperties();
        assertNotNull(purged.get("actions"));
    }
}
