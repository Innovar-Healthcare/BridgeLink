/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1056: JUnit coverage improvement — LookupTableNaming.
 */

package com.mirth.connect.plugins.dynamiclookup.server.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

/**
 * Unit tests for {@link LookupTableNaming}.
 */
public class LookupTableNamingTest {

    @Test
    public void valueTableName_fromGroup_returnsCorrectName() {
        LookupGroup group = new LookupGroup();
        group.setId(1);
        assertEquals("LOOKUP_VALUE_1", LookupTableNaming.valueTableName(group));
    }

    @Test
    public void valueTableName_fromGroupId_returnsCorrectName() {
        assertEquals("LOOKUP_VALUE_42", LookupTableNaming.valueTableName(42));
    }

    @Test
    public void valueTableName_groupIdZero_returnsLookupValue0() {
        assertEquals("LOOKUP_VALUE_0", LookupTableNaming.valueTableName(0));
    }

    @Test
    public void valueTableName_largeGroupId_returnsCorrectName() {
        assertEquals("LOOKUP_VALUE_100000", LookupTableNaming.valueTableName(100000));
    }

    @Test
    public void valueTableName_groupOverload_matchesIntOverload() {
        LookupGroup group = new LookupGroup();
        group.setId(99);
        assertEquals(LookupTableNaming.valueTableName(99), LookupTableNaming.valueTableName(group));
    }
}
