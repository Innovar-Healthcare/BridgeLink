/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 userutil coverage — MapBuilder add/Map delegation.
 * Tests must be in the same package to access package-private constructors.
 */

package com.mirth.connect.userutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class MapBuilderTest {

    // ------------------------------------------------------------------
    // Default constructor: empty map
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_empty() {
        MapBuilder mb = new MapBuilder();
        assertTrue(mb.isEmpty());
        assertEquals(0, mb.size());
    }

    // ------------------------------------------------------------------
    // Key/value constructor: one entry
    // ------------------------------------------------------------------

    @Test
    public void kvConstructor_oneEntry() {
        MapBuilder mb = new MapBuilder("key1", "value1");
        assertEquals(1, mb.size());
        assertEquals("value1", mb.get("key1"));
    }

    // ------------------------------------------------------------------
    // Map constructor: wraps existing map
    // ------------------------------------------------------------------

    @Test
    public void mapConstructor_wrapsExistingMap() {
        Map<String, Integer> existing = new HashMap<String, Integer>();
        existing.put("a", 1);
        existing.put("b", 2);
        MapBuilder mb = new MapBuilder(existing);
        assertEquals(2, mb.size());
        assertEquals(1, mb.get("a"));
    }

    // ------------------------------------------------------------------
    // add: puts entry and returns builder (fluent)
    // ------------------------------------------------------------------

    @Test
    public void add_putsEntryReturnsBuilder() {
        MapBuilder mb = new MapBuilder();
        MapBuilder result = mb.add("k", "v");
        assertEquals(mb, result);
        assertEquals("v", mb.get("k"));
    }

    @Test
    public void add_fluentChain_multipleEntries() {
        MapBuilder mb = new MapBuilder();
        mb.add("name", "Alice").add("age", 30).add("city", "Boston");
        assertEquals(3, mb.size());
        assertEquals("Alice", mb.get("name"));
        assertEquals(30, mb.get("age"));
    }

    // ------------------------------------------------------------------
    // containsKey / containsValue
    // ------------------------------------------------------------------

    @Test
    public void containsKey_existingKey_returnsTrue() {
        MapBuilder mb = new MapBuilder("myKey", "myValue");
        assertTrue(mb.containsKey("myKey"));
    }

    @Test
    public void containsKey_absentKey_returnsFalse() {
        MapBuilder mb = new MapBuilder("myKey", "myValue");
        assertFalse(mb.containsKey("other"));
    }

    @Test
    public void containsValue_existingValue_returnsTrue() {
        MapBuilder mb = new MapBuilder("k", "present");
        assertTrue(mb.containsValue("present"));
    }

    @Test
    public void containsValue_absentValue_returnsFalse() {
        MapBuilder mb = new MapBuilder("k", "present");
        assertFalse(mb.containsValue("absent"));
    }

    // ------------------------------------------------------------------
    // put / get
    // ------------------------------------------------------------------

    @Test
    public void put_addsEntry() {
        MapBuilder mb = new MapBuilder();
        mb.put("x", 42);
        assertEquals(42, mb.get("x"));
    }

    @Test
    public void put_overwritesExisting() {
        MapBuilder mb = new MapBuilder("key", "old");
        mb.put("key", "new");
        assertEquals("new", mb.get("key"));
    }

    // ------------------------------------------------------------------
    // remove
    // ------------------------------------------------------------------

    @Test
    public void remove_removesEntry() {
        MapBuilder mb = new MapBuilder("r", "val");
        mb.remove("r");
        assertFalse(mb.containsKey("r"));
    }

    // ------------------------------------------------------------------
    // clear
    // ------------------------------------------------------------------

    @Test
    public void clear_emptiesMap() {
        MapBuilder mb = new MapBuilder();
        mb.add("a", 1).add("b", 2);
        mb.clear();
        assertTrue(mb.isEmpty());
    }

    // ------------------------------------------------------------------
    // keySet / values / entrySet
    // ------------------------------------------------------------------

    @Test
    public void keySet_returnsAllKeys() {
        MapBuilder mb = new MapBuilder();
        mb.add("k1", "v1").add("k2", "v2");
        assertNotNull(mb.keySet());
        assertEquals(2, mb.keySet().size());
        assertTrue(mb.keySet().contains("k1"));
    }

    @Test
    public void values_returnsAllValues() {
        MapBuilder mb = new MapBuilder();
        mb.add("a", "alpha").add("b", "beta");
        assertNotNull(mb.values());
        assertEquals(2, mb.values().size());
    }

    @Test
    public void entrySet_returnsAllEntries() {
        MapBuilder mb = new MapBuilder("e1", "val1");
        assertNotNull(mb.entrySet());
        assertEquals(1, mb.entrySet().size());
    }

    // ------------------------------------------------------------------
    // putAll
    // ------------------------------------------------------------------

    @Test
    public void putAll_addsAllEntries() {
        MapBuilder mb = new MapBuilder();
        Map<String, String> more = new HashMap<String, String>();
        more.put("x", "X");
        more.put("y", "Y");
        mb.putAll(more);
        assertEquals(2, mb.size());
        assertEquals("X", mb.get("x"));
    }
}
