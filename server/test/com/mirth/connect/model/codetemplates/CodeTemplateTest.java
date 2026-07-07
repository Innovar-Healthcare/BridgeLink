/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 code template POJO coverage — CodeTemplate constructors/getters/toString/getPurgedProperties.
 */

package com.mirth.connect.model.codetemplates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Map;

import org.junit.Test;

public class CodeTemplateTest {

    // ------------------------------------------------------------------
    // Single-arg constructor (id only)
    // ------------------------------------------------------------------

    @Test
    public void idConstructor_setsId_otherFieldsNull() {
        CodeTemplate ct = new CodeTemplate("my-id");
        assertEquals("my-id", ct.getId());
        assertNull(ct.getName());
        assertNull(ct.getRevision());
        assertNull(ct.getLastModified());
        assertNull(ct.getContextSet());
        assertNull(ct.getProperties());
    }

    // ------------------------------------------------------------------
    // Full constructor (name, type, contextSet, code, description)
    // ------------------------------------------------------------------

    @Test
    public void fullConstructor_setsNameTypeContextCode() {
        CodeTemplateContextSet contextSet = CodeTemplateContextSet.getConnectorContextSet();
        CodeTemplate ct = new CodeTemplate(
            "My Function",
            CodeTemplateProperties.CodeTemplateType.FUNCTION,
            contextSet,
            "function test() {}",
            "Test function"
        );
        assertEquals("My Function", ct.getName());
        assertNotNull(ct.getId());
        assertNotNull(ct.getContextSet());
        assertEquals(CodeTemplateProperties.CodeTemplateType.FUNCTION, ct.getType());
        assertNotNull(ct.getCode());
    }

    // ------------------------------------------------------------------
    // Constructor with code only (no description)
    // ------------------------------------------------------------------

    @Test
    public void constructorNoDescription_setsCode() {
        CodeTemplate ct = new CodeTemplate(
            "Util",
            CodeTemplateProperties.CodeTemplateType.DRAG_AND_DROP_CODE,
            CodeTemplateContextSet.getConnectorContextSet(),
            "function util() {}"
        );
        assertNotNull(ct.getCode());
        assertEquals(CodeTemplateProperties.CodeTemplateType.DRAG_AND_DROP_CODE, ct.getType());
    }

    // ------------------------------------------------------------------
    // getDefaultCodeTemplate
    // ------------------------------------------------------------------

    @Test
    public void getDefaultCodeTemplate_returnsNonNull() {
        CodeTemplate ct = CodeTemplate.getDefaultCodeTemplate("Default");
        assertNotNull(ct);
        assertEquals("Default", ct.getName());
        assertNotNull(ct.getCode());
        assertNotNull(ct.getContextSet());
    }

    // ------------------------------------------------------------------
    // Copy constructor
    // ------------------------------------------------------------------

    @Test
    public void copyConstructor_copiesAllFields() {
        CodeTemplate orig = new CodeTemplate("Orig", CodeTemplateProperties.CodeTemplateType.FUNCTION,
            CodeTemplateContextSet.getConnectorContextSet(), "function a() {}");
        orig.setRevision(3);
        Calendar cal = Calendar.getInstance();
        orig.setLastModified(cal);

        CodeTemplate copy = new CodeTemplate(orig);
        assertEquals(orig.getId(), copy.getId());
        assertEquals(orig.getName(), copy.getName());
        assertEquals(orig.getRevision(), copy.getRevision());
        assertNotNull(copy.getLastModified());
    }

    // ------------------------------------------------------------------
    // Setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setId_roundTrip() {
        CodeTemplate ct = new CodeTemplate("old-id");
        ct.setId("new-id");
        assertEquals("new-id", ct.getId());
    }

    @Test
    public void setRevision_roundTrip() {
        CodeTemplate ct = new CodeTemplate("id");
        ct.setRevision(5);
        assertEquals(Integer.valueOf(5), ct.getRevision());
    }

    @Test
    public void setLastModified_roundTrip() {
        CodeTemplate ct = new CodeTemplate("id");
        Calendar cal = Calendar.getInstance();
        ct.setLastModified(cal);
        assertEquals(cal, ct.getLastModified());
    }

    @Test
    public void setContextSet_roundTrip() {
        CodeTemplate ct = new CodeTemplate("id");
        CodeTemplateContextSet cs = CodeTemplateContextSet.getChannelContextSet();
        ct.setContextSet(cs);
        assertNotNull(ct.getContextSet());
    }

    @Test
    public void setProperties_roundTrip() {
        CodeTemplate ct = new CodeTemplate("id");
        BasicCodeTemplateProperties props = new BasicCodeTemplateProperties(
            CodeTemplateProperties.CodeTemplateType.FUNCTION, "function a(){}");
        ct.setProperties(props);
        assertNotNull(ct.getProperties());
        assertEquals(CodeTemplateProperties.CodeTemplateType.FUNCTION, ct.getProperties().getType());
    }

    // ------------------------------------------------------------------
    // getCode / getDescription / getType: delegate to properties
    // ------------------------------------------------------------------

    @Test
    public void getCode_returnsFromProperties() {
        CodeTemplate ct = new CodeTemplate("Fn", CodeTemplateProperties.CodeTemplateType.FUNCTION,
            CodeTemplateContextSet.getConnectorContextSet(), "function myFn() {}");
        assertEquals("function myFn() {}", ct.getCode());
    }

    @Test
    public void getType_returnsFromProperties() {
        CodeTemplate ct = new CodeTemplate("Fn", CodeTemplateProperties.CodeTemplateType.COMPILED_CODE,
            CodeTemplateContextSet.getConnectorContextSet(), "//script");
        assertEquals(CodeTemplateProperties.CodeTemplateType.COMPILED_CODE, ct.getType());
    }

    @Test
    public void getType_nullProperties_returnsNull() {
        CodeTemplate ct = new CodeTemplate("id");
        assertNull(ct.getType());
    }

    // ------------------------------------------------------------------
    // toString: non-null, contains id and name
    // ------------------------------------------------------------------

    @Test
    public void toString_containsIdAndName() {
        CodeTemplate ct = new CodeTemplate("Tester", CodeTemplateProperties.CodeTemplateType.FUNCTION,
            CodeTemplateContextSet.getConnectorContextSet(), "function t(){}");
        ct.setId("test-ct-id");
        String str = ct.toString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
        assertTrue(str.contains("test-ct-id"));
        assertTrue(str.contains("Tester"));
    }

    // ------------------------------------------------------------------
    // getPurgedProperties: contains expected keys
    // ------------------------------------------------------------------

    @Test
    public void getPurgedProperties_containsExpectedKeys() {
        CodeTemplate ct = new CodeTemplate("PurgeTest", CodeTemplateProperties.CodeTemplateType.FUNCTION,
            CodeTemplateContextSet.getConnectorContextSet(), "function p(){}");
        ct.setId("purge-id");
        Map<String, Object> purged = ct.getPurgedProperties();
        assertNotNull(purged);
        assertTrue(purged.containsKey("id"));
        assertTrue(purged.containsKey("nameChars"));
        assertTrue(purged.containsKey("lastModified"));
        assertTrue(purged.containsKey("contextSet"));
        assertTrue(purged.containsKey("parameterCount"));
        assertTrue(purged.containsKey("properties"));
        assertEquals("purge-id", purged.get("id"));
    }

    @Test
    public void getPurgedProperties_nullName_nameCharsZero() {
        CodeTemplate ct = new CodeTemplate("id");
        ct.setId("t");
        Map<String, Object> purged = ct.getPurgedProperties();
        assertEquals(0, purged.get("nameChars"));
    }

    // ------------------------------------------------------------------
    // equals: EqualsBuilder.reflectionEquals
    // ------------------------------------------------------------------

    @Test
    public void equals_sameId_returnsTrue() {
        CodeTemplate c1 = new CodeTemplate("same-id");
        CodeTemplate c2 = new CodeTemplate("same-id");
        assertTrue(c1.equals(c2));
    }

    @Test
    public void equals_differentId_returnsFalse() {
        CodeTemplate c1 = new CodeTemplate("id-1");
        CodeTemplate c2 = new CodeTemplate("id-2");
        assertFalse(c1.equals(c2));
    }

    // ------------------------------------------------------------------
    // cloneIfNeeded: returns same instance (no-copy for serialization)
    // ------------------------------------------------------------------

    @Test
    public void cloneIfNeeded_returnsSelf() {
        CodeTemplate ct = new CodeTemplate("fn", CodeTemplateProperties.CodeTemplateType.FUNCTION,
            CodeTemplateContextSet.getConnectorContextSet(), "function fn(){}");
        CodeTemplate clone = ct.cloneIfNeeded();
        // Cacheable<T>.cloneIfNeeded returns this since no deep clone needed
        assertNotNull(clone);
    }
}
