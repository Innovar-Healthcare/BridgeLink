/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.http;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;

import org.junit.Test;

/**
 * Unit tests for {@link HttpReceiverProperties} — pure POJO, no network, no database.
 *
 * <p>Specifically covers the {@code requestHeaderSize} field introduced to support
 * configurable HTTP request header size on the HTTP Listener connector.
 */
public class HttpReceiverPropertiesTest {

    /**
     * A freshly constructed {@link HttpReceiverProperties} must default to Jetty's
     * built-in limit of 8 192 bytes so that existing channels are unaffected.
     */
    @Test
    public void testRequestHeaderSize_defaultIs8192() {
        HttpReceiverProperties props = new HttpReceiverProperties();
        assertEquals("8192", props.getRequestHeaderSize());
    }

    /**
     * Channels saved before this feature was introduced will have
     * {@code requestHeaderSize == null} after XStream deserialises them.
     * The getter must return {@code "8192"} in that case so existing channels
     * continue to behave exactly as before without any migration step.
     */
    @Test
    public void testRequestHeaderSize_nullSafeGetterReturnsDefault() throws Exception {
        HttpReceiverProperties props = new HttpReceiverProperties();
        Field field = HttpReceiverProperties.class.getDeclaredField("requestHeaderSize");
        field.setAccessible(true);
        field.set(props, null);

        assertEquals(
            "Deserialised null must fall back to default 8192",
            "8192",
            props.getRequestHeaderSize());
    }

    /**
     * Verifies that a non-default value set by the user is round-tripped
     * correctly through the getter/setter.
     */
    @Test
    public void testRequestHeaderSize_customValueRoundTrip() {
        HttpReceiverProperties props = new HttpReceiverProperties();
        props.setRequestHeaderSize("32768");
        assertEquals("32768", props.getRequestHeaderSize());
    }
}
