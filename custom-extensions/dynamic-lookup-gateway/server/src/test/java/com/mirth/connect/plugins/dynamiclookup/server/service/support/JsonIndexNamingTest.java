/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1056: JUnit coverage improvement — JsonIndexNaming.
 */

package com.mirth.connect.plugins.dynamiclookup.server.service.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link JsonIndexNaming}.
 */
public class JsonIndexNamingTest {

    @Test
    public void buildIndexName_resultIsNotNull() {
        assertNotNull(JsonIndexNaming.buildIndexName("LOOKUP_VALUE_1", "$.name"));
    }

    @Test
    public void buildIndexName_resultStartsWithIdxPrefix() {
        String name = JsonIndexNaming.buildIndexName("LOOKUP_VALUE_1", "$.name");
        assertTrue(name.startsWith("idx_"));
    }

    @Test
    public void buildIndexName_isDeterministic() {
        String n1 = JsonIndexNaming.buildIndexName("LOOKUP_VALUE_1", "$.name");
        String n2 = JsonIndexNaming.buildIndexName("LOOKUP_VALUE_1", "$.name");
        assertEquals(n1, n2);
    }

    @Test
    public void buildIndexName_differsByField() {
        String n1 = JsonIndexNaming.buildIndexName("T", "$.a");
        String n2 = JsonIndexNaming.buildIndexName("T", "$.b");
        assertNotEquals(n1, n2);
    }

    @Test
    public void buildIndexName_differsByTableName() {
        String n1 = JsonIndexNaming.buildIndexName("TABLE_A", "$.name");
        String n2 = JsonIndexNaming.buildIndexName("TABLE_B", "$.name");
        assertNotEquals(n1, n2);
    }

    @Test
    public void buildIndexName_tableNameIsLowercased() {
        String lower = JsonIndexNaming.buildIndexName("lookup_value_1", "$.x");
        String upper = JsonIndexNaming.buildIndexName("LOOKUP_VALUE_1", "$.x");
        assertEquals(lower, upper);
    }

    @Test
    public void buildIndexName_nullFieldPath_doesNotThrow() {
        String name = JsonIndexNaming.buildIndexName("T", null);
        assertNotNull(name);
    }

    @Test
    public void buildIndexName_specialCharsInTable_sanitized() {
        // Non-alphanumeric chars in table name should become underscores
        String name = JsonIndexNaming.buildIndexName("LOOKUP-VALUE/1", "$.name");
        assertNotNull(name);
        // After sanitization, name must only contain idx_ + alnum + _ + hash
        assertTrue(name.matches("idx_[a-z0-9_]+"));
    }

    @Test
    public void buildIndexName_hashIs16HexChars() {
        String name = JsonIndexNaming.buildIndexName("T", "$.field");
        // Format: idx_<table>_json_<16hexchars>
        String[] parts = name.split("_json_");
        assertEquals(2, parts.length);
        assertEquals(16, parts[1].length());
        assertTrue(parts[1].matches("[0-9a-f]{16}"));
    }
}
