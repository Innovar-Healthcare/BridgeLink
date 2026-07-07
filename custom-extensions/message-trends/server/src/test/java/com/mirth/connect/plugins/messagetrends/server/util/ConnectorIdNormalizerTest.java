/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1056: JUnit coverage improvement — ConnectorIdNormalizer.
 */

package com.mirth.connect.plugins.messagetrends.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link ConnectorIdNormalizer}.
 */
public class ConnectorIdNormalizerTest {

    // ------------------------------------------------------------------
    // toDb: API → DB normalization
    // ------------------------------------------------------------------

    @Test
    public void toDb_null_returnsEmptyMarker() {
        assertEquals(ConnectorIdNormalizer.EMPTY_CONNECTOR, ConnectorIdNormalizer.toDb(null));
    }

    @Test
    public void toDb_emptyString_returnsEmptyMarker() {
        assertEquals(ConnectorIdNormalizer.EMPTY_CONNECTOR, ConnectorIdNormalizer.toDb(""));
    }

    @Test
    public void toDb_realId_returnsPassthrough() {
        assertEquals("connector-001", ConnectorIdNormalizer.toDb("connector-001"));
    }

    @Test
    public void toDb_alreadyEmptyMarker_returnsItself() {
        // A real connector ID that happens to be the marker — preserved as-is
        assertEquals(ConnectorIdNormalizer.EMPTY_CONNECTOR,
            ConnectorIdNormalizer.toDb(ConnectorIdNormalizer.EMPTY_CONNECTOR));
    }

    // ------------------------------------------------------------------
    // toApi: DB → API normalization
    // ------------------------------------------------------------------

    @Test
    public void toApi_emptyMarker_returnsEmptyString() {
        assertEquals("", ConnectorIdNormalizer.toApi(ConnectorIdNormalizer.EMPTY_CONNECTOR));
    }

    @Test
    public void toApi_null_returnsNull() {
        assertNull(ConnectorIdNormalizer.toApi(null));
    }

    @Test
    public void toApi_realId_returnsPassthrough() {
        assertEquals("my-connector-001",
            ConnectorIdNormalizer.toApi("my-connector-001"));
    }

    // ------------------------------------------------------------------
    // Round-trip: toApi(toDb(x)) == original for real IDs
    // ------------------------------------------------------------------

    @Test
    public void roundTrip_realId_preservesValue() {
        String id = "source-001";
        assertEquals(id, ConnectorIdNormalizer.toApi(ConnectorIdNormalizer.toDb(id)));
    }

    @Test
    public void roundTrip_null_becomesEmptyString() {
        // null → toDb → "__EMPTY__" → toApi → ""
        assertEquals("", ConnectorIdNormalizer.toApi(ConnectorIdNormalizer.toDb(null)));
    }

    @Test
    public void roundTrip_emptyString_becomesEmptyString() {
        assertEquals("", ConnectorIdNormalizer.toApi(ConnectorIdNormalizer.toDb("")));
    }

    // ------------------------------------------------------------------
    // isChannelLevel
    // ------------------------------------------------------------------

    @Test
    public void isChannelLevel_null_returnsTrue() {
        assertTrue(ConnectorIdNormalizer.isChannelLevel(null));
    }

    @Test
    public void isChannelLevel_emptyString_returnsTrue() {
        assertTrue(ConnectorIdNormalizer.isChannelLevel(""));
    }

    @Test
    public void isChannelLevel_emptyMarker_returnsTrue() {
        assertTrue(ConnectorIdNormalizer.isChannelLevel(ConnectorIdNormalizer.EMPTY_CONNECTOR));
    }

    @Test
    public void isChannelLevel_realId_returnsFalse() {
        assertFalse(ConnectorIdNormalizer.isChannelLevel("connector-001"));
    }

    @Test
    public void isChannelLevel_whitespaceString_returnsFalse() {
        // Whitespace is NOT considered channel-level (not empty/null/marker)
        assertFalse(ConnectorIdNormalizer.isChannelLevel("   "));
    }

    // ------------------------------------------------------------------
    // EMPTY_CONNECTOR constant value
    // ------------------------------------------------------------------

    @Test
    public void emptyConnectorConstant_hasExpectedValue() {
        assertEquals("__EMPTY__", ConnectorIdNormalizer.EMPTY_CONNECTOR);
    }
}
