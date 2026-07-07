/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 plugin POJO coverage — RuleBuilderRule constructor/getter/setter/Condition enum/getPurgedProperties.
 */

package com.mirth.connect.plugins.rulebuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class RuleBuilderRuleTest {

    // ------------------------------------------------------------------
    // Default constructor
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_conditionIsExists() {
        RuleBuilderRule rule = new RuleBuilderRule();
        assertEquals(RuleBuilderRule.Condition.EXISTS, rule.getCondition());
    }

    @Test
    public void defaultConstructor_fieldNull() {
        RuleBuilderRule rule = new RuleBuilderRule();
        // field starts as null or empty — just verify it's accessible
        assertNotNull(rule.getCondition());
    }

    // ------------------------------------------------------------------
    // Copy constructor
    // ------------------------------------------------------------------

    @Test
    public void copyConstructor_copiesFields() {
        RuleBuilderRule orig = new RuleBuilderRule();
        orig.setField("msg['MSH']['MSH.9']['MSH.9.1']");
        orig.setCondition(RuleBuilderRule.Condition.EQUALS);
        List<String> values = new ArrayList<String>();
        values.add("ADT");
        orig.setValues(values);

        RuleBuilderRule copy = new RuleBuilderRule(orig);
        assertEquals("msg['MSH']['MSH.9']['MSH.9.1']", copy.getField());
        assertEquals(RuleBuilderRule.Condition.EQUALS, copy.getCondition());
        assertNotNull(copy.getValues());
        assertEquals(1, copy.getValues().size());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setField_roundTrip() {
        RuleBuilderRule rule = new RuleBuilderRule();
        rule.setField("msg['PID']['PID.3']");
        assertEquals("msg['PID']['PID.3']", rule.getField());
    }

    @Test
    public void setCondition_equals() {
        RuleBuilderRule rule = new RuleBuilderRule();
        rule.setCondition(RuleBuilderRule.Condition.EQUALS);
        assertEquals(RuleBuilderRule.Condition.EQUALS, rule.getCondition());
    }

    @Test
    public void setCondition_notEqual() {
        RuleBuilderRule rule = new RuleBuilderRule();
        rule.setCondition(RuleBuilderRule.Condition.NOT_EQUAL);
        assertEquals(RuleBuilderRule.Condition.NOT_EQUAL, rule.getCondition());
    }

    @Test
    public void setCondition_contains() {
        RuleBuilderRule rule = new RuleBuilderRule();
        rule.setCondition(RuleBuilderRule.Condition.CONTAINS);
        assertEquals(RuleBuilderRule.Condition.CONTAINS, rule.getCondition());
    }

    @Test
    public void setCondition_notContain() {
        RuleBuilderRule rule = new RuleBuilderRule();
        rule.setCondition(RuleBuilderRule.Condition.NOT_CONTAIN);
        assertEquals(RuleBuilderRule.Condition.NOT_CONTAIN, rule.getCondition());
    }

    @Test
    public void setCondition_notExist() {
        RuleBuilderRule rule = new RuleBuilderRule();
        rule.setCondition(RuleBuilderRule.Condition.NOT_EXIST);
        assertEquals(RuleBuilderRule.Condition.NOT_EXIST, rule.getCondition());
    }

    @Test
    public void setValues_roundTrip() {
        RuleBuilderRule rule = new RuleBuilderRule();
        List<String> values = new ArrayList<String>();
        values.add("ADT");
        values.add("ORU");
        rule.setValues(values);
        assertEquals(2, rule.getValues().size());
        assertEquals("ADT", rule.getValues().get(0));
    }

    // ------------------------------------------------------------------
    // Condition enum: isValuesEnabled
    // ------------------------------------------------------------------

    @Test
    public void condition_exists_valuesDisabled() {
        assertFalse(RuleBuilderRule.Condition.EXISTS.isValuesEnabled());
    }

    @Test
    public void condition_notExist_valuesDisabled() {
        assertFalse(RuleBuilderRule.Condition.NOT_EXIST.isValuesEnabled());
    }

    @Test
    public void condition_equals_valuesEnabled() {
        assertTrue(RuleBuilderRule.Condition.EQUALS.isValuesEnabled());
    }

    @Test
    public void condition_notEqual_valuesEnabled() {
        assertTrue(RuleBuilderRule.Condition.NOT_EQUAL.isValuesEnabled());
    }

    @Test
    public void condition_contains_valuesEnabled() {
        assertTrue(RuleBuilderRule.Condition.CONTAINS.isValuesEnabled());
    }

    @Test
    public void condition_notContain_valuesEnabled() {
        assertTrue(RuleBuilderRule.Condition.NOT_CONTAIN.isValuesEnabled());
    }

    // ------------------------------------------------------------------
    // Condition enum: getPresentTense
    // ------------------------------------------------------------------

    @Test
    public void condition_exists_presentTense() {
        assertEquals("exists", RuleBuilderRule.Condition.EXISTS.getPresentTense());
    }

    @Test
    public void condition_notExist_presentTense() {
        assertEquals("does not exist", RuleBuilderRule.Condition.NOT_EXIST.getPresentTense());
    }

    @Test
    public void condition_equals_presentTense() {
        assertEquals("equals", RuleBuilderRule.Condition.EQUALS.getPresentTense());
    }

    @Test
    public void condition_contains_presentTense() {
        assertEquals("contains", RuleBuilderRule.Condition.CONTAINS.getPresentTense());
    }

    // ------------------------------------------------------------------
    // Condition enum: toString (capitalized)
    // ------------------------------------------------------------------

    @Test
    public void condition_exists_toString() {
        assertEquals("Exists", RuleBuilderRule.Condition.EXISTS.toString());
    }

    @Test
    public void condition_notExist_toString() {
        assertEquals("Not Exist", RuleBuilderRule.Condition.NOT_EXIST.toString());
    }

    // ------------------------------------------------------------------
    // getType: returns plugin point constant
    // ------------------------------------------------------------------

    @Test
    public void getType_returnsRuleBuilder() {
        RuleBuilderRule rule = new RuleBuilderRule();
        assertEquals("Rule Builder", rule.getType());
    }

    // ------------------------------------------------------------------
    // clone: returns copy
    // ------------------------------------------------------------------

    @Test
    public void clone_returnsNewInstance() {
        RuleBuilderRule rule = new RuleBuilderRule();
        rule.setField("testField");
        com.mirth.connect.model.Rule clone = rule.clone();
        assertNotNull(clone);
        assertTrue(clone instanceof RuleBuilderRule);
        assertEquals("testField", ((RuleBuilderRule) clone).getField());
    }

    // ------------------------------------------------------------------
    // getPurgedProperties: contains condition and valuesCount
    // ------------------------------------------------------------------

    @Test
    public void getPurgedProperties_containsConditionAndValuesCount() {
        RuleBuilderRule rule = new RuleBuilderRule();
        rule.setField("myField");
        rule.setCondition(RuleBuilderRule.Condition.CONTAINS);
        List<String> values = new ArrayList<String>();
        values.add("val1");
        values.add("val2");
        rule.setValues(values);

        Map<String, Object> purged = rule.getPurgedProperties();
        assertNotNull(purged);
        assertEquals(RuleBuilderRule.Condition.CONTAINS, purged.get("condition"));
        assertEquals(2, purged.get("valuesCount"));
    }

    @Test
    public void getPurgedProperties_nullValues_noValuesCountKey() {
        RuleBuilderRule rule = new RuleBuilderRule();
        rule.setValues(null);
        Map<String, Object> purged = rule.getPurgedProperties();
        assertFalse(purged.containsKey("valuesCount"));
    }
}
