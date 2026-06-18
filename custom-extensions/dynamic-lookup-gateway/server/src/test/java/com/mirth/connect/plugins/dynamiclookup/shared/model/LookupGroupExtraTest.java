package com.mirth.connect.plugins.dynamiclookup.shared.model;

import static org.junit.Assert.*;

import org.junit.Test;

public class LookupGroupExtraTest {

    @Test
    public void defaultConstructor_noException() {
        LookupGroupExtra extra = new LookupGroupExtra();
        assertNotNull(extra);
    }

    @Test
    public void threeArgConstructor_setsFields() {
        LookupGroupExtra extra = new LookupGroupExtra(5, "FIELD", "{\"a\":1}");
        assertEquals(5, extra.getGroupId());
        assertEquals("FIELD", extra.getJsonIndexMode());
        assertEquals("{\"a\":1}", extra.getIndexedJsonFields());
    }

    @Test
    public void settersGetters_groupId() {
        LookupGroupExtra extra = new LookupGroupExtra();
        extra.setGroupId(10);
        assertEquals(10, extra.getGroupId());
    }

    @Test
    public void settersGetters_jsonIndexMode() {
        LookupGroupExtra extra = new LookupGroupExtra();
        extra.setJsonIndexMode("NONE");
        assertEquals("NONE", extra.getJsonIndexMode());
    }

    @Test
    public void settersGetters_indexedJsonFields() {
        LookupGroupExtra extra = new LookupGroupExtra();
        extra.setIndexedJsonFields("[\"field1\"]");
        assertEquals("[\"field1\"]", extra.getIndexedJsonFields());
    }

    @Test
    public void toString_containsLookupGroupExtra() {
        LookupGroupExtra extra = new LookupGroupExtra(1, "FIELD", "{}");
        assertTrue(extra.toString().contains("LookupGroupExtra"));
    }
}
