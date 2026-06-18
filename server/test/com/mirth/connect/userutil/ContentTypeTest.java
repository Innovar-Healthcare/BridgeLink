/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 enum coverage — ContentType toString/values/valueOf.
 */

package com.mirth.connect.userutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContentTypeTest {

    // ------------------------------------------------------------------
    // toString: capitalized human-readable name
    // ------------------------------------------------------------------

    @Test
    public void toString_raw_returnsRaw() {
        assertEquals("Raw", ContentType.RAW.toString());
    }

    @Test
    public void toString_processedRaw_returnsProcessedRaw() {
        assertEquals("Processed Raw", ContentType.PROCESSED_RAW.toString());
    }

    @Test
    public void toString_transformed_returnsTransformed() {
        assertEquals("Transformed", ContentType.TRANSFORMED.toString());
    }

    @Test
    public void toString_encoded_returnsEncoded() {
        assertEquals("Encoded", ContentType.ENCODED.toString());
    }

    @Test
    public void toString_sent_returnsSent() {
        assertEquals("Sent", ContentType.SENT.toString());
    }

    @Test
    public void toString_response_returnsResponse() {
        assertEquals("Response", ContentType.RESPONSE.toString());
    }

    @Test
    public void toString_responseTransformed_returnsResponseTransformed() {
        assertEquals("Response Transformed", ContentType.RESPONSE_TRANSFORMED.toString());
    }

    @Test
    public void toString_processedResponse_returnsProcessedResponse() {
        assertEquals("Processed Response", ContentType.PROCESSED_RESPONSE.toString());
    }

    @Test
    public void toString_connectorMap_returnsConnectorMap() {
        assertEquals("Connector Map", ContentType.CONNECTOR_MAP.toString());
    }

    @Test
    public void toString_channelMap_returnsChannelMap() {
        assertEquals("Channel Map", ContentType.CHANNEL_MAP.toString());
    }

    @Test
    public void toString_responseMap_returnsResponseMap() {
        assertEquals("Response Map", ContentType.RESPONSE_MAP.toString());
    }

    @Test
    public void toString_processingError_returnsProcessingError() {
        assertEquals("Processing Error", ContentType.PROCESSING_ERROR.toString());
    }

    @Test
    public void toString_postprocessorError_returnsPostprocessorError() {
        assertEquals("Postprocessor Error", ContentType.POSTPROCESSOR_ERROR.toString());
    }

    @Test
    public void toString_responseError_returnsResponseError() {
        assertEquals("Response Error", ContentType.RESPONSE_ERROR.toString());
    }

    @Test
    public void toString_sourceMap_returnsSourceMap() {
        assertEquals("Source Map", ContentType.SOURCE_MAP.toString());
    }

    // ------------------------------------------------------------------
    // values: all 15 types present
    // ------------------------------------------------------------------

    @Test
    public void values_returns15Types() {
        ContentType[] types = ContentType.values();
        assertNotNull(types);
        assertEquals(15, types.length);
    }

    // ------------------------------------------------------------------
    // valueOf: roundtrip via name
    // ------------------------------------------------------------------

    @Test
    public void valueOf_raw_returnsRaw() {
        assertEquals(ContentType.RAW, ContentType.valueOf("RAW"));
    }

    @Test
    public void valueOf_connectorMap_returnsConnectorMap() {
        assertEquals(ContentType.CONNECTOR_MAP, ContentType.valueOf("CONNECTOR_MAP"));
    }

    @Test
    public void valueOf_postprocessorError_returnsPostprocessorError() {
        assertEquals(ContentType.POSTPROCESSOR_ERROR, ContentType.valueOf("POSTPROCESSOR_ERROR"));
    }

    @Test
    public void valueOf_sourceMap_returnsSourceMap() {
        assertEquals(ContentType.SOURCE_MAP, ContentType.valueOf("SOURCE_MAP"));
    }

    // ------------------------------------------------------------------
    // name: each value's name matches expected
    // ------------------------------------------------------------------

    @Test
    public void name_processedRaw_matchesExpected() {
        assertEquals("PROCESSED_RAW", ContentType.PROCESSED_RAW.name());
    }
}
