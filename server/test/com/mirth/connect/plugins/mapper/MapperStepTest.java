/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 plugin POJO coverage — MapperStep constructor/getter/setter/Scope enum/getPurgedProperties.
 */

package com.mirth.connect.plugins.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import com.mirth.connect.model.Step;

public class MapperStepTest {

    // ------------------------------------------------------------------
    // Default constructor
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_variableEmpty() {
        MapperStep step = new MapperStep();
        assertEquals("", step.getVariable());
    }

    @Test
    public void defaultConstructor_mappingEmpty() {
        MapperStep step = new MapperStep();
        assertEquals("", step.getMapping());
    }

    @Test
    public void defaultConstructor_defaultValueEmpty() {
        MapperStep step = new MapperStep();
        assertEquals("", step.getDefaultValue());
    }

    @Test
    public void defaultConstructor_scopeIsChannel() {
        MapperStep step = new MapperStep();
        assertEquals(MapperStep.Scope.CHANNEL, step.getScope());
    }

    @Test
    public void defaultConstructor_replacementsEmpty() {
        MapperStep step = new MapperStep();
        assertNotNull(step.getReplacements());
        assertTrue(step.getReplacements().isEmpty());
    }

    // ------------------------------------------------------------------
    // Copy constructor
    // ------------------------------------------------------------------

    @Test
    public void copyConstructor_copiesAllFields() {
        MapperStep orig = new MapperStep();
        orig.setVariable("myVar");
        orig.setMapping("msg['PID']['PID.5']");
        orig.setDefaultValue("unknown");
        orig.setScope(MapperStep.Scope.GLOBAL);
        List<Pair<String, String>> replacements = new ArrayList<Pair<String, String>>();
        replacements.add(new ImmutablePair<String, String>("find", "replace"));
        orig.setReplacements(replacements);

        MapperStep copy = new MapperStep(orig);
        assertEquals("myVar", copy.getVariable());
        assertEquals("msg['PID']['PID.5']", copy.getMapping());
        assertEquals("unknown", copy.getDefaultValue());
        assertEquals(MapperStep.Scope.GLOBAL, copy.getScope());
        assertEquals(1, copy.getReplacements().size());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setVariable_roundTrip() {
        MapperStep step = new MapperStep();
        step.setVariable("testVariable");
        assertEquals("testVariable", step.getVariable());
    }

    @Test
    public void setMapping_roundTrip() {
        MapperStep step = new MapperStep();
        step.setMapping("msg['MSH']");
        assertEquals("msg['MSH']", step.getMapping());
    }

    @Test
    public void setDefaultValue_roundTrip() {
        MapperStep step = new MapperStep();
        step.setDefaultValue("N/A");
        assertEquals("N/A", step.getDefaultValue());
    }

    @Test
    public void setScope_connector() {
        MapperStep step = new MapperStep();
        step.setScope(MapperStep.Scope.CONNECTOR);
        assertEquals(MapperStep.Scope.CONNECTOR, step.getScope());
    }

    @Test
    public void setScope_response() {
        MapperStep step = new MapperStep();
        step.setScope(MapperStep.Scope.RESPONSE);
        assertEquals(MapperStep.Scope.RESPONSE, step.getScope());
    }

    @Test
    public void setScope_globalChannel() {
        MapperStep step = new MapperStep();
        step.setScope(MapperStep.Scope.GLOBAL_CHANNEL);
        assertEquals(MapperStep.Scope.GLOBAL_CHANNEL, step.getScope());
    }

    @Test
    public void setReplacements_roundTrip() {
        MapperStep step = new MapperStep();
        List<Pair<String, String>> replacements = new ArrayList<Pair<String, String>>();
        replacements.add(new ImmutablePair<String, String>("a", "b"));
        replacements.add(new ImmutablePair<String, String>("c", "d"));
        step.setReplacements(replacements);
        assertEquals(2, step.getReplacements().size());
        assertEquals("a", step.getReplacements().get(0).getLeft());
        assertEquals("b", step.getReplacements().get(0).getRight());
    }

    // ------------------------------------------------------------------
    // Scope enum: toString returns label
    // ------------------------------------------------------------------

    @Test
    public void scope_connector_toStringReturnsLabel() {
        assertEquals("Connector Map", MapperStep.Scope.CONNECTOR.toString());
    }

    @Test
    public void scope_channel_toStringReturnsLabel() {
        assertEquals("Channel Map", MapperStep.Scope.CHANNEL.toString());
    }

    @Test
    public void scope_global_toStringReturnsLabel() {
        assertEquals("Global Map", MapperStep.Scope.GLOBAL.toString());
    }

    @Test
    public void scope_globalChannel_toStringReturnsLabel() {
        assertEquals("Global Channel Map", MapperStep.Scope.GLOBAL_CHANNEL.toString());
    }

    @Test
    public void scope_response_toStringReturnsLabel() {
        assertEquals("Response Map", MapperStep.Scope.RESPONSE.toString());
    }

    @Test
    public void scope_connector_mapField() {
        assertEquals("connectorMap", MapperStep.Scope.CONNECTOR.map);
    }

    @Test
    public void scope_channel_mapField() {
        assertEquals("channelMap", MapperStep.Scope.CHANNEL.map);
    }

    @Test
    public void scope_responseMap_mapField() {
        assertEquals("responseMap", MapperStep.Scope.RESPONSE.map);
    }

    // ------------------------------------------------------------------
    // getType: returns plugin point constant
    // ------------------------------------------------------------------

    @Test
    public void getType_returnsMapper() {
        MapperStep step = new MapperStep();
        assertEquals("Mapper", step.getType());
    }

    // ------------------------------------------------------------------
    // clone: returns a MapperStep copy
    // ------------------------------------------------------------------

    @Test
    public void clone_returnsNewInstance() {
        MapperStep step = new MapperStep();
        step.setVariable("cloneVar");
        Step clone = step.clone();
        assertNotNull(clone);
        assertTrue(clone instanceof MapperStep);
        assertEquals("cloneVar", ((MapperStep) clone).getVariable());
    }

    // ------------------------------------------------------------------
    // getPurgedProperties: contains replacementsCount and scope
    // ------------------------------------------------------------------

    @Test
    public void getPurgedProperties_containsReplacementsCountAndScope() {
        MapperStep step = new MapperStep();
        step.setVariable("testVar");
        step.setScope(MapperStep.Scope.GLOBAL);
        List<Pair<String, String>> replacements = new ArrayList<Pair<String, String>>();
        replacements.add(new ImmutablePair<String, String>("find", "replace"));
        step.setReplacements(replacements);

        Map<String, Object> purged = step.getPurgedProperties();
        assertNotNull(purged);
        assertTrue(purged.containsKey("replacementsCount"));
        assertEquals(1, purged.get("replacementsCount"));
        assertEquals(MapperStep.Scope.GLOBAL, purged.get("scope"));
    }
}
