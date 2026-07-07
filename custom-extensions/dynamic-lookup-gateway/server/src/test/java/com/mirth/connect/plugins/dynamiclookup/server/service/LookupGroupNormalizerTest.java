/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1056: JUnit coverage improvement — LookupGroupNormalizer.
 */

package com.mirth.connect.plugins.dynamiclookup.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;

/**
 * Unit tests for {@link LookupGroupNormalizer}.
 */
public class LookupGroupNormalizerTest {

    // ------------------------------------------------------------------
    // Null-safety
    // ------------------------------------------------------------------

    @Test
    public void normalize_nullGroup_isNoOp() {
        LookupGroupNormalizer.normalize(null); // must not throw
    }

    // ------------------------------------------------------------------
    // TEXT mode: extra is cleared
    // ------------------------------------------------------------------

    @Test
    public void normalize_textType_lowercaseInput_uppercasesValueType() {
        LookupGroup g = new LookupGroup();
        g.setValueType("text");
        LookupGroupNormalizer.normalize(g);
        assertEquals("TEXT", g.getValueType());
    }

    @Test
    public void normalize_textType_setsExtraToNull() {
        LookupGroup g = new LookupGroup();
        g.setValueType("text");
        LookupGroupExtra extra = new LookupGroupExtra();
        extra.setIndexedJsonFields("[\"$.name\"]");
        g.setExtra(extra);
        LookupGroupNormalizer.normalize(g);
        assertNull(g.getExtra()); // TEXT mode clears extra
    }

    @Test
    public void normalize_unknownType_defaultsToText() {
        LookupGroup g = new LookupGroup();
        g.setValueType("BLOB"); // unknown → TEXT
        LookupGroupNormalizer.normalize(g);
        assertEquals("TEXT", g.getValueType());
    }

    @Test
    public void normalize_nullValueType_defaultsToText() {
        LookupGroup g = new LookupGroup();
        g.setValueType(null);
        LookupGroupNormalizer.normalize(g);
        assertEquals("TEXT", g.getValueType());
    }

    // ------------------------------------------------------------------
    // JSON mode: extra is preserved / fields nulled on NONE
    // ------------------------------------------------------------------

    @Test
    public void normalize_jsonType_lowercaseInput_uppercasesValueType() {
        LookupGroup g = new LookupGroup();
        g.setValueType("json");
        LookupGroupNormalizer.normalize(g);
        assertEquals("JSON", g.getValueType());
    }

    @Test
    public void normalize_jsonType_nullExtra_doesNotThrow() {
        LookupGroup g = new LookupGroup();
        g.setValueType("JSON");
        g.setExtra(null);
        LookupGroupNormalizer.normalize(g); // no NPE
    }

    @Test
    public void normalize_jsonType_noneIndexMode_nullsIndexedFields() {
        LookupGroup g = new LookupGroup();
        g.setValueType("json");
        LookupGroupExtra extra = new LookupGroupExtra();
        extra.setJsonIndexMode("NONE");
        extra.setIndexedJsonFields("[\"$.name\"]");
        g.setExtra(extra);

        LookupGroupNormalizer.normalize(g);

        assertNull(extra.getIndexedJsonFields()); // NONE → cleared
    }

    @Test
    public void normalize_jsonType_fieldIndexMode_preservesIndexedFields() {
        LookupGroup g = new LookupGroup();
        g.setValueType("JSON");
        LookupGroupExtra extra = new LookupGroupExtra();
        extra.setJsonIndexMode("FIELD");
        extra.setIndexedJsonFields("[\"$.name\"]");
        g.setExtra(extra);

        LookupGroupNormalizer.normalize(g);

        assertNotNull(extra.getIndexedJsonFields()); // FIELD → preserved
        assertEquals("[\"$.name\"]", extra.getIndexedJsonFields());
    }

    @Test
    public void normalize_jsonType_noIndexMode_nullsIndexedFields() {
        LookupGroup g = new LookupGroup();
        g.setValueType("JSON");
        LookupGroupExtra extra = new LookupGroupExtra();
        extra.setJsonIndexMode(null); // null → treated as NONE
        extra.setIndexedJsonFields("[\"$.id\"]");
        g.setExtra(extra);

        LookupGroupNormalizer.normalize(g);

        assertNull(extra.getIndexedJsonFields());
    }
}
