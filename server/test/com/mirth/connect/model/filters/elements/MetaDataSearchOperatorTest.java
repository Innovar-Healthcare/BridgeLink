/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 enum coverage — MetaDataSearchOperator toString/toFullString/valuesForColumnType.
 */

package com.mirth.connect.model.filters.elements;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.mirth.connect.donkey.model.channel.MetaDataColumnType;

import org.junit.Test;

public class MetaDataSearchOperatorTest {

    // ------------------------------------------------------------------
    // toString: returns short operator string
    // ------------------------------------------------------------------

    @Test
    public void toString_equal() {
        assertEquals("=", MetaDataSearchOperator.EQUAL.toString());
    }

    @Test
    public void toString_notEqual() {
        assertEquals("!=", MetaDataSearchOperator.NOT_EQUAL.toString());
    }

    @Test
    public void toString_lessThan() {
        assertEquals("<", MetaDataSearchOperator.LESS_THAN.toString());
    }

    @Test
    public void toString_lessThanOrEqual() {
        assertEquals("<=", MetaDataSearchOperator.LESS_THAN_OR_EQUAL.toString());
    }

    @Test
    public void toString_greaterThan() {
        assertEquals(">", MetaDataSearchOperator.GREATER_THAN.toString());
    }

    @Test
    public void toString_greaterThanOrEqual() {
        assertEquals(">=", MetaDataSearchOperator.GREATER_THAN_OR_EQUAL.toString());
    }

    @Test
    public void toString_contains() {
        assertEquals("CONTAINS", MetaDataSearchOperator.CONTAINS.toString());
    }

    @Test
    public void toString_doesNotContain() {
        assertEquals("DOES NOT CONTAIN", MetaDataSearchOperator.DOES_NOT_CONTAIN.toString());
    }

    @Test
    public void toString_startsWith() {
        assertEquals("STARTS WITH", MetaDataSearchOperator.STARTS_WITH.toString());
    }

    @Test
    public void toString_doesNotStartWith() {
        assertEquals("DOES NOT START WITH", MetaDataSearchOperator.DOES_NOT_START_WITH.toString());
    }

    @Test
    public void toString_endsWith() {
        assertEquals("ENDS WITH", MetaDataSearchOperator.ENDS_WITH.toString());
    }

    @Test
    public void toString_doesNotEndWith() {
        assertEquals("DOES NOT END WITH", MetaDataSearchOperator.DOES_NOT_END_WITH.toString());
    }

    // ------------------------------------------------------------------
    // toFullString: returns enum name (e.g., "EQUAL")
    // ------------------------------------------------------------------

    @Test
    public void toFullString_equal_returnsEnumName() {
        assertEquals("EQUAL", MetaDataSearchOperator.EQUAL.toFullString());
    }

    @Test
    public void toFullString_contains_returnsEnumName() {
        assertEquals("CONTAINS", MetaDataSearchOperator.CONTAINS.toFullString());
    }

    // ------------------------------------------------------------------
    // valuesForColumnType: correct operators returned per type
    // ------------------------------------------------------------------

    @Test
    public void valuesForColumnType_boolean_returnsEqualAndNotEqual() {
        MetaDataSearchOperator[] ops = MetaDataSearchOperator.valuesForColumnType(MetaDataColumnType.BOOLEAN);
        assertNotNull(ops);
        assertEquals(2, ops.length);
        assertEquals(MetaDataSearchOperator.EQUAL, ops[0]);
        assertEquals(MetaDataSearchOperator.NOT_EQUAL, ops[1]);
    }

    @Test
    public void valuesForColumnType_number_returnsComparisonOps() {
        MetaDataSearchOperator[] ops = MetaDataSearchOperator.valuesForColumnType(MetaDataColumnType.NUMBER);
        assertNotNull(ops);
        assertEquals(6, ops.length);
        // First two are EQUAL and NOT_EQUAL
        assertEquals(MetaDataSearchOperator.EQUAL, ops[0]);
        assertEquals(MetaDataSearchOperator.NOT_EQUAL, ops[1]);
    }

    @Test
    public void valuesForColumnType_string_returnsStringOps() {
        MetaDataSearchOperator[] ops = MetaDataSearchOperator.valuesForColumnType(MetaDataColumnType.STRING);
        assertNotNull(ops);
        assertEquals(8, ops.length);
        assertEquals(MetaDataSearchOperator.EQUAL, ops[0]);
        assertEquals(MetaDataSearchOperator.CONTAINS, ops[2]);
    }

    @Test
    public void valuesForColumnType_timestamp_returnsComparisonOps() {
        MetaDataSearchOperator[] ops = MetaDataSearchOperator.valuesForColumnType(MetaDataColumnType.TIMESTAMP);
        assertNotNull(ops);
        assertEquals(6, ops.length);
    }

    // ------------------------------------------------------------------
    // fromString: parses string back to enum constant
    // ------------------------------------------------------------------

    @Test
    public void fromString_equal_returnsEqual() {
        assertEquals(MetaDataSearchOperator.EQUAL, MetaDataSearchOperator.fromString("EQUAL"));
    }

    @Test
    public void fromString_contains_returnsContains() {
        assertEquals(MetaDataSearchOperator.CONTAINS, MetaDataSearchOperator.fromString("CONTAINS"));
    }

    @Test
    public void fromString_doesNotContain_returnsDNC() {
        assertEquals(MetaDataSearchOperator.DOES_NOT_CONTAIN, MetaDataSearchOperator.fromString("DOES_NOT_CONTAIN"));
    }

    @Test
    public void fromString_startsWith_returnsSW() {
        assertEquals(MetaDataSearchOperator.STARTS_WITH, MetaDataSearchOperator.fromString("STARTS_WITH"));
    }

    @Test
    public void fromString_doesNotStartWith_returnsDNSW() {
        assertEquals(MetaDataSearchOperator.DOES_NOT_START_WITH, MetaDataSearchOperator.fromString("DOES_NOT_START_WITH"));
    }

    @Test
    public void fromString_endsWith_returnsEW() {
        assertEquals(MetaDataSearchOperator.ENDS_WITH, MetaDataSearchOperator.fromString("ENDS_WITH"));
    }

    @Test
    public void fromString_doesNotEndWith_returnsDNEW() {
        assertEquals(MetaDataSearchOperator.DOES_NOT_END_WITH, MetaDataSearchOperator.fromString("DOES_NOT_END_WITH"));
    }

    @Test
    public void fromString_lessThan_returnsLT() {
        assertEquals(MetaDataSearchOperator.LESS_THAN, MetaDataSearchOperator.fromString("LESS_THAN"));
    }

    @Test
    public void fromString_lessThanOrEqual_returnsLTE() {
        assertEquals(MetaDataSearchOperator.LESS_THAN_OR_EQUAL, MetaDataSearchOperator.fromString("LESS_THAN_OR_EQUAL"));
    }

    @Test
    public void fromString_greaterThan_returnsGT() {
        assertEquals(MetaDataSearchOperator.GREATER_THAN, MetaDataSearchOperator.fromString("GREATER_THAN"));
    }

    @Test
    public void fromString_greaterThanOrEqual_returnsGTE() {
        assertEquals(MetaDataSearchOperator.GREATER_THAN_OR_EQUAL, MetaDataSearchOperator.fromString("GREATER_THAN_OR_EQUAL"));
    }

    @Test
    public void fromString_unknownString_returnsNull() {
        assertNull(MetaDataSearchOperator.fromString("UNKNOWN_OPERATOR"));
    }

    @Test
    public void fromString_notEqual_returnsNotEqual() {
        assertEquals(MetaDataSearchOperator.NOT_EQUAL, MetaDataSearchOperator.fromString("NOT_EQUAL"));
    }
}
