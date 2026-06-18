package com.mirth.connect.plugins.dynamiclookup.shared.model;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

public class LookupValueTest {

    @Test
    public void defaultConstructor_emptyStrings() {
        LookupValue v = new LookupValue();
        assertEquals("", v.getKeyValue());
        assertEquals("", v.getValueData());
    }

    @Test
    public void twoArgConstructor_setsFields() {
        LookupValue v = new LookupValue("key1", "val1");
        assertEquals("key1", v.getKeyValue());
        assertEquals("val1", v.getValueData());
    }

    @Test
    public void copyConstructor_preservesFields() {
        LookupValue orig = new LookupValue("k", "v");
        LookupValue copy = new LookupValue(orig);
        assertEquals("k", copy.getKeyValue());
        assertEquals("v", copy.getValueData());
    }

    @Test
    public void copyConstructor_deepCopiesDates() {
        LookupValue orig = new LookupValue("k", "v");
        Date created = new Date(2_000_000L);
        orig.setCreatedDate(created);

        LookupValue copy = new LookupValue(orig);
        created.setTime(9_999_999L);

        assertEquals(2_000_000L, copy.getCreatedDate().getTime());
    }

    @Test
    public void settersGetters_keyValue() {
        LookupValue v = new LookupValue();
        v.setKeyValue("abc");
        assertEquals("abc", v.getKeyValue());
    }

    @Test
    public void settersGetters_valueData() {
        LookupValue v = new LookupValue();
        v.setValueData("xyz");
        assertEquals("xyz", v.getValueData());
    }

    @Test
    public void settersGetters_dates() {
        LookupValue v = new LookupValue();
        Date created = new Date(1_000L);
        Date updated = new Date(2_000L);
        v.setCreatedDate(created);
        v.setUpdatedDate(updated);
        assertEquals(1_000L, v.getCreatedDate().getTime());
        assertEquals(2_000L, v.getUpdatedDate().getTime());
    }
}
