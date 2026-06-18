/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1056: JUnit coverage improvement — JsonFieldCriterion.
 */

package com.mirth.connect.plugins.dynamiclookup.server.service.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Unit tests for {@link JsonFieldCriterion}.
 *
 * JsonFieldCriterion is a data-transfer object (DTO) — tests cover all setters/getters.
 */
public class JsonFieldCriterionTest {

    @Test
    public void defaultConstructor_allFieldsAreNull() {
        JsonFieldCriterion c = new JsonFieldCriterion();
        assertNull(c.getExpression());
        assertNull(c.getTypeCheckSql());
        assertNull(c.getOperatorSql());
        assertNull(c.getValue());
        assertNull(c.getValueSql());
    }

    @Test
    public void setAndGetExpression() {
        JsonFieldCriterion c = new JsonFieldCriterion();
        c.setExpression("$.name");
        assertEquals("$.name", c.getExpression());
    }

    @Test
    public void setAndGetTypeCheckSql() {
        JsonFieldCriterion c = new JsonFieldCriterion();
        c.setTypeCheckSql("JSON_TYPE(val) = 'STRING'");
        assertEquals("JSON_TYPE(val) = 'STRING'", c.getTypeCheckSql());
    }

    @Test
    public void setAndGetOperatorSql() {
        JsonFieldCriterion c = new JsonFieldCriterion();
        c.setOperatorSql("=");
        assertEquals("=", c.getOperatorSql());
    }

    @Test
    public void setAndGetValue_stringValue() {
        JsonFieldCriterion c = new JsonFieldCriterion();
        c.setValue("hello");
        assertEquals("hello", c.getValue());
    }

    @Test
    public void setAndGetValue_integerValue() {
        JsonFieldCriterion c = new JsonFieldCriterion();
        c.setValue(42);
        assertEquals(42, c.getValue());
    }

    @Test
    public void setAndGetValueSql() {
        JsonFieldCriterion c = new JsonFieldCriterion();
        c.setValueSql("?");
        assertEquals("?", c.getValueSql());
    }

    @Test
    public void setNull_allowsNullForAllFields() {
        JsonFieldCriterion c = new JsonFieldCriterion();
        c.setExpression("$.x");
        c.setExpression(null);
        assertNull(c.getExpression());

        c.setTypeCheckSql("something");
        c.setTypeCheckSql(null);
        assertNull(c.getTypeCheckSql());
    }

    @Test
    public void multipleFieldsSetIndependently() {
        JsonFieldCriterion c = new JsonFieldCriterion();
        c.setExpression("$.id");
        c.setTypeCheckSql("JSON_TYPE(val) = 'INTEGER'");
        c.setOperatorSql(">");
        c.setValue(100);
        c.setValueSql("?");

        assertEquals("$.id", c.getExpression());
        assertEquals("JSON_TYPE(val) = 'INTEGER'", c.getTypeCheckSql());
        assertEquals(">", c.getOperatorSql());
        assertEquals(100, c.getValue());
        assertEquals("?", c.getValueSql());
    }
}
