/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 code template POJO coverage — CodeTemplateLibrary constructor/getter/setter/toString/getPurgedProperties.
 */

package com.mirth.connect.model.codetemplates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class CodeTemplateLibraryTest {

    // Helper: create a library with a single CodeTemplate
    private CodeTemplateLibrary createLibrary(String name) {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        lib.setName(name);
        return lib;
    }

    // ------------------------------------------------------------------
    // Default constructor: id auto-assigned, empty sets/lists
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_idNotNull() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        assertNotNull(lib.getId());
    }

    @Test
    public void defaultConstructor_enabledChannelIdsEmpty() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        assertNotNull(lib.getEnabledChannelIds());
        assertTrue(lib.getEnabledChannelIds().isEmpty());
    }

    @Test
    public void defaultConstructor_disabledChannelIdsEmpty() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        assertNotNull(lib.getDisabledChannelIds());
        assertTrue(lib.getDisabledChannelIds().isEmpty());
    }

    @Test
    public void defaultConstructor_codeTemplatesEmpty() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        assertNotNull(lib.getCodeTemplates());
        assertTrue(lib.getCodeTemplates().isEmpty());
    }

    // ------------------------------------------------------------------
    // Copy constructor
    // ------------------------------------------------------------------

    @Test
    public void copyConstructor_copiesId() {
        CodeTemplateLibrary orig = new CodeTemplateLibrary();
        orig.setName("Original Library");
        orig.setDescription("My description");
        orig.setIncludeNewChannels(true);
        Set<String> enabled = new HashSet<String>();
        enabled.add("chan-001");
        orig.setEnabledChannelIds(enabled);

        CodeTemplateLibrary copy = new CodeTemplateLibrary(orig);
        assertEquals(orig.getId(), copy.getId());
        assertEquals(orig.getName(), copy.getName());
        assertEquals(orig.getDescription(), copy.getDescription());
        assertTrue(copy.isIncludeNewChannels());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setId_roundTrip() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        lib.setId("lib-001");
        assertEquals("lib-001", lib.getId());
    }

    @Test
    public void setName_roundTrip() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        lib.setName("Test Library");
        assertEquals("Test Library", lib.getName());
    }

    @Test
    public void setRevision_roundTrip() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        lib.setRevision(7);
        assertEquals(Integer.valueOf(7), lib.getRevision());
    }

    @Test
    public void setLastModified_roundTrip() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        Calendar cal = Calendar.getInstance();
        lib.setLastModified(cal);
        assertEquals(cal, lib.getLastModified());
    }

    @Test
    public void setDescription_roundTrip() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        lib.setDescription("A test library description");
        assertEquals("A test library description", lib.getDescription());
    }

    @Test
    public void setIncludeNewChannels_true() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        lib.setIncludeNewChannels(true);
        assertTrue(lib.isIncludeNewChannels());
    }

    @Test
    public void setIncludeNewChannels_false() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        lib.setIncludeNewChannels(true);
        lib.setIncludeNewChannels(false);
        assertFalse(lib.isIncludeNewChannels());
    }

    @Test
    public void setEnabledChannelIds_roundTrip() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        Set<String> ids = new HashSet<String>();
        ids.add("chan-a");
        ids.add("chan-b");
        lib.setEnabledChannelIds(ids);
        assertEquals(2, lib.getEnabledChannelIds().size());
        assertTrue(lib.getEnabledChannelIds().contains("chan-a"));
    }

    @Test
    public void setDisabledChannelIds_roundTrip() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        Set<String> ids = new HashSet<String>();
        ids.add("chan-x");
        lib.setDisabledChannelIds(ids);
        assertEquals(1, lib.getDisabledChannelIds().size());
        assertTrue(lib.getDisabledChannelIds().contains("chan-x"));
    }

    @Test
    public void setCodeTemplates_roundTrip() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        List<CodeTemplate> templates = new ArrayList<CodeTemplate>();
        templates.add(new CodeTemplate("tmpl-1"));
        templates.add(new CodeTemplate("tmpl-2"));
        lib.setCodeTemplates(templates);
        assertEquals(2, lib.getCodeTemplates().size());
    }

    // ------------------------------------------------------------------
    // sortCodeTemplates: sorts by name (null-first)
    // ------------------------------------------------------------------

    @Test
    public void sortCodeTemplates_sortsAlphabetically() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        List<CodeTemplate> templates = new ArrayList<CodeTemplate>();
        CodeTemplate ct1 = new CodeTemplate("Zebra Function", CodeTemplateProperties.CodeTemplateType.FUNCTION,
            CodeTemplateContextSet.getConnectorContextSet(), "function z(){}");
        CodeTemplate ct2 = new CodeTemplate("Apple Function", CodeTemplateProperties.CodeTemplateType.FUNCTION,
            CodeTemplateContextSet.getConnectorContextSet(), "function a(){}");
        templates.add(ct1);
        templates.add(ct2);
        lib.setCodeTemplates(templates);
        lib.sortCodeTemplates();
        assertEquals("Apple Function", lib.getCodeTemplates().get(0).getName());
        assertEquals("Zebra Function", lib.getCodeTemplates().get(1).getName());
    }

    @Test
    public void sortCodeTemplates_nullNameFirst() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        List<CodeTemplate> templates = new ArrayList<CodeTemplate>();
        CodeTemplate ct1 = new CodeTemplate("id1");
        ct1.setId("id1");
        CodeTemplate ct2 = new CodeTemplate("Beta Function", CodeTemplateProperties.CodeTemplateType.FUNCTION,
            CodeTemplateContextSet.getConnectorContextSet(), "function b(){}");
        templates.add(ct2);
        templates.add(ct1);  // null name
        lib.setCodeTemplates(templates);
        lib.sortCodeTemplates();
        // null name should come first
        assertNull(lib.getCodeTemplates().get(0).getName());
    }

    @Test
    public void sortCodeTemplates_bothNullNames_returnsZero() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        List<CodeTemplate> templates = new ArrayList<CodeTemplate>();
        CodeTemplate ct1 = new CodeTemplate("id1");
        CodeTemplate ct2 = new CodeTemplate("id2");
        templates.add(ct1);
        templates.add(ct2);
        lib.setCodeTemplates(templates);
        lib.sortCodeTemplates(); // should not throw
        assertEquals(2, lib.getCodeTemplates().size());
    }

    // ------------------------------------------------------------------
    // replaceCodeTemplatesWithIds: replaces templates with id-only versions
    // ------------------------------------------------------------------

    @Test
    public void replaceCodeTemplatesWithIds_replacesWithIdOnly() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        List<CodeTemplate> templates = new ArrayList<CodeTemplate>();
        CodeTemplate ct = new CodeTemplate("My Function", CodeTemplateProperties.CodeTemplateType.FUNCTION,
            CodeTemplateContextSet.getConnectorContextSet(), "function f(){}");
        ct.setId("template-abc");
        templates.add(ct);
        lib.setCodeTemplates(templates);

        lib.replaceCodeTemplatesWithIds();

        assertEquals(1, lib.getCodeTemplates().size());
        assertEquals("template-abc", lib.getCodeTemplates().get(0).getId());
        // The replaced entry has id only (name is null from id-only constructor)
        assertNull(lib.getCodeTemplates().get(0).getName());
    }

    // ------------------------------------------------------------------
    // cloneIfNeeded: returns a new copy
    // ------------------------------------------------------------------

    @Test
    public void cloneIfNeeded_returnsNonNull() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        lib.setName("Clone Test");
        CodeTemplateLibrary clone = lib.cloneIfNeeded();
        assertNotNull(clone);
        assertEquals("Clone Test", clone.getName());
    }

    // ------------------------------------------------------------------
    // toString: non-null and contains key fields
    // ------------------------------------------------------------------

    @Test
    public void toString_containsIdAndName() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        lib.setId("lib-str-id");
        lib.setName("String Test Library");
        String str = lib.toString();
        assertNotNull(str);
        assertTrue(str.contains("lib-str-id"));
        assertTrue(str.contains("String Test Library"));
    }

    // ------------------------------------------------------------------
    // getPurgedProperties: contains expected keys
    // ------------------------------------------------------------------

    @Test
    public void getPurgedProperties_containsExpectedKeys() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        lib.setId("lib-purge-id");
        lib.setName("Purge Library");
        lib.setDescription("A description");
        lib.setIncludeNewChannels(true);

        Map<String, Object> purged = lib.getPurgedProperties();
        assertNotNull(purged);
        assertTrue(purged.containsKey("id"));
        assertTrue(purged.containsKey("nameChars"));
        assertTrue(purged.containsKey("lastModified"));
        assertTrue(purged.containsKey("descriptionChars"));
        assertTrue(purged.containsKey("includeNewChannels"));
        assertTrue(purged.containsKey("enabledChannelIdsCount"));
        assertTrue(purged.containsKey("disabledChannelIdsCount"));
        assertTrue(purged.containsKey("codeTemplates"));

        assertEquals("lib-purge-id", purged.get("id"));
        assertEquals(true, purged.get("includeNewChannels"));
        assertEquals(0, purged.get("enabledChannelIdsCount"));
    }

    @Test
    public void getPurgedProperties_withChannelIds_countCorrect() {
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        Set<String> enabled = new HashSet<String>();
        enabled.add("e1");
        enabled.add("e2");
        lib.setEnabledChannelIds(enabled);
        Set<String> disabled = new HashSet<String>();
        disabled.add("d1");
        lib.setDisabledChannelIds(disabled);

        Map<String, Object> purged = lib.getPurgedProperties();
        assertEquals(2, purged.get("enabledChannelIdsCount"));
        assertEquals(1, purged.get("disabledChannelIdsCount"));
    }

    // ------------------------------------------------------------------
    // equals: reflectionEquals ignoring special constants
    // ------------------------------------------------------------------

    @Test
    public void equals_sameId_returnsTrue() {
        CodeTemplateLibrary lib1 = new CodeTemplateLibrary();
        lib1.setId("shared-id");
        lib1.setName("Lib A");

        CodeTemplateLibrary lib2 = new CodeTemplateLibrary();
        lib2.setId("shared-id");
        lib2.setName("Lib A");

        assertTrue(lib1.equals(lib2));
    }

    @Test
    public void equals_differentId_returnsFalse() {
        CodeTemplateLibrary lib1 = new CodeTemplateLibrary();
        lib1.setId("id-1");

        CodeTemplateLibrary lib2 = new CodeTemplateLibrary();
        lib2.setId("id-2");

        assertFalse(lib1.equals(lib2));
    }
}
