package com.mirth.connect.plugins.dynamiclookup.shared.model;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

public class LookupGroupTest {

    @Test
    public void defaultConstructor_defaults() {
        LookupGroup g = new LookupGroup();
        assertEquals(0, g.getId());
        assertEquals("", g.getName());
        assertEquals(1000, g.getCacheSize());
        assertEquals("LRU", g.getCachePolicy());
        assertEquals("TEXT", g.getValueType());
        assertTrue(g.isStatisticsEnabled());
    }

    @Test
    public void settersGetters_id() {
        LookupGroup g = new LookupGroup();
        g.setId(42);
        assertEquals(42, g.getId());
    }

    @Test
    public void settersGetters_name() {
        LookupGroup g = new LookupGroup();
        g.setName("myGroup");
        assertEquals("myGroup", g.getName());
    }

    @Test
    public void settersGetters_description() {
        LookupGroup g = new LookupGroup();
        g.setDescription("desc");
        assertEquals("desc", g.getDescription());
    }

    @Test
    public void settersGetters_version() {
        LookupGroup g = new LookupGroup();
        g.setVersion("v2");
        assertEquals("v2", g.getVersion());
    }

    @Test
    public void settersGetters_cacheSize() {
        LookupGroup g = new LookupGroup();
        g.setCacheSize(500);
        assertEquals(500, g.getCacheSize());
    }

    @Test
    public void settersGetters_cachePolicy() {
        LookupGroup g = new LookupGroup();
        g.setCachePolicy("FIFO");
        assertEquals("FIFO", g.getCachePolicy());
    }

    @Test
    public void settersGetters_valueType() {
        LookupGroup g = new LookupGroup();
        g.setValueType("JSON");
        assertEquals("JSON", g.getValueType());
    }

    @Test
    public void settersGetters_statisticsEnabled() {
        LookupGroup g = new LookupGroup();
        g.setStatisticsEnabled(false);
        assertFalse(g.isStatisticsEnabled());
    }

    @Test
    public void settersGetters_extra() {
        LookupGroup g = new LookupGroup();
        LookupGroupExtra extra = new LookupGroupExtra(1, "FIELD", "{}");
        g.setExtra(extra);
        assertSame(extra, g.getExtra());
    }

    @Test
    public void copyConstructor_preservesAllFields() {
        LookupGroup orig = new LookupGroup();
        orig.setId(7);
        orig.setName("test");
        orig.setDescription("d");
        orig.setVersion("1");
        orig.setCacheSize(200);
        orig.setCachePolicy("FIFO");
        orig.setValueType("JSON");
        orig.setStatisticsEnabled(false);
        Date created = new Date(1_000_000L);
        orig.setCreatedDate(created);

        LookupGroup copy = new LookupGroup(orig);

        assertEquals(7, copy.getId());
        assertEquals("test", copy.getName());
        assertEquals("d", copy.getDescription());
        assertEquals("1", copy.getVersion());
        assertEquals(200, copy.getCacheSize());
        assertEquals("FIFO", copy.getCachePolicy());
        assertEquals("JSON", copy.getValueType());
        assertFalse(copy.isStatisticsEnabled());
        assertEquals(1_000_000L, copy.getCreatedDate().getTime());
    }

    @Test
    public void copyConstructor_deepCopiesCreatedDate() {
        LookupGroup orig = new LookupGroup();
        Date created = new Date(1_000_000L);
        orig.setCreatedDate(created);

        LookupGroup copy = new LookupGroup(orig);

        // mutate original date — copy must not be affected
        created.setTime(9_999_999L);
        assertEquals(1_000_000L, copy.getCreatedDate().getTime());
    }
}
