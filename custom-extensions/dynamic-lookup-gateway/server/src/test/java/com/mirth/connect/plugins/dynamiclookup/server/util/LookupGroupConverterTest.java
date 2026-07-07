package com.mirth.connect.plugins.dynamiclookup.server.util;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

public class LookupGroupConverterTest {

    private static Map<String, String> map(String... pairs) {
        Map<String, String> m = new HashMap<String, String>();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    @Test
    public void toLookupGroup_allFieldsPopulated() {
        Map<String, String> m = map(
            "name", "myGroup",
            "description", "desc",
            "version", "v1",
            "cacheSize", "500",
            "cachePolicy", "FIFO",
            "valueType", "JSON",
            "jsonIndexMode", "FIELD",
            "indexedJsonFields", "[\"a\"]",
            "statisticsEnabled", "true"
        );
        LookupGroup g = LookupGroupConverter.toLookupGroup(m);
        assertEquals("myGroup", g.getName());
        assertEquals("desc", g.getDescription());
        assertEquals("v1", g.getVersion());
        assertEquals(500, g.getCacheSize());
        assertEquals("FIFO", g.getCachePolicy());
        assertEquals("JSON", g.getValueType());
        assertTrue(g.isStatisticsEnabled());
        assertNotNull(g.getExtra());
        assertEquals("FIELD", g.getExtra().getJsonIndexMode());
        assertEquals("[\"a\"]", g.getExtra().getIndexedJsonFields());
    }

    @Test
    public void toLookupGroup_nonNumericCacheSize_defaults1000() {
        Map<String, String> m = map("cacheSize", "notAnInt");
        LookupGroup g = LookupGroupConverter.toLookupGroup(m);
        assertEquals(1000, g.getCacheSize());
    }

    @Test
    public void toLookupGroup_nullCacheSize_defaults1000() {
        Map<String, String> m = new HashMap<String, String>();
        LookupGroup g = LookupGroupConverter.toLookupGroup(m);
        assertEquals(1000, g.getCacheSize());
    }

    @Test
    public void toLookupGroup_noJsonFields_extraIsNull() {
        Map<String, String> m = map("name", "g");
        LookupGroup g = LookupGroupConverter.toLookupGroup(m);
        assertNull(g.getExtra());
    }

    @Test
    public void toLookupGroup_jsonIndexModeOnly_extraIsSet() {
        Map<String, String> m = map("jsonIndexMode", "NONE");
        LookupGroup g = LookupGroupConverter.toLookupGroup(m);
        assertNotNull(g.getExtra());
        assertEquals("NONE", g.getExtra().getJsonIndexMode());
        assertNull(g.getExtra().getIndexedJsonFields());
    }

    @Test
    public void toLookupGroup_spacesOnlyValue_treatedAsNull() {
        Map<String, String> m = map("name", "   ");
        LookupGroup g = LookupGroupConverter.toLookupGroup(m);
        assertNull(g.getName());
    }
}
