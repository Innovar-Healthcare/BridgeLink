/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.mirth.connect.connectors.vm.VmDispatcherProperties;

/**
 * Covers the shared serialization path backing both the extension-scoped webadmin connector
 * defaults endpoint and the built-in {@code GET /connectors/{type}/defaults} endpoint (IRT-1516).
 * Uses {@link VmDispatcherProperties} (Channel Writer) as a simple, dependency-free
 * {@code ConnectorProperties} fixture.
 */
public class ConnectorPropertiesUtilTest {

    @Test
    public void testToConnectorPropertiesXmlRootTagAndClassAttribute() throws Exception {
        VmDispatcherProperties properties = new VmDispatcherProperties();
        String xml = ConnectorPropertiesUtil.toConnectorPropertiesXml(properties);

        assertNotNull(xml);
        assertTrue(xml.contains("<properties"));
        assertTrue(xml.contains("class=\""));
        assertTrue(xml.contains("VmDispatcherProperties"));
    }

    @Test
    public void testToConnectorPropertiesXmlDefaultsNullPluginProperties() throws Exception {
        VmDispatcherProperties properties = new VmDispatcherProperties();
        assertNull(properties.getPluginProperties());

        ConnectorPropertiesUtil.toConnectorPropertiesXml(properties);

        // The Swing client always carries a (possibly empty) pluginProperties element; the util
        // defaults it the same way so served defaults match saved channel XML.
        assertNotNull(properties.getPluginProperties());
        assertTrue(properties.getPluginProperties().isEmpty());
    }

    @Test
    public void testToConnectorPropertiesXmlDefaultsZeroQueueBufferSize() throws Exception {
        VmDispatcherProperties properties = new VmDispatcherProperties();
        assertEquals(0, properties.getDestinationConnectorProperties().getQueueBufferSize());

        ConnectorPropertiesUtil.toConnectorPropertiesXml(properties);

        // No ConfigurationController is wired up in this plain unit test, so
        // getDefaultQueueBufferSize() falls back to Constants.DEFAULT_QUEUE_BUFFER_SIZE (1000)
        // rather than throwing.
        assertEquals(1000, properties.getDestinationConnectorProperties().getQueueBufferSize());
    }

    @Test
    public void testToConnectorPropertiesXmlPreservesPositiveQueueBufferSize() throws Exception {
        VmDispatcherProperties properties = new VmDispatcherProperties();
        properties.getDestinationConnectorProperties().setQueueBufferSize(500);

        ConnectorPropertiesUtil.toConnectorPropertiesXml(properties);

        assertEquals(500, properties.getDestinationConnectorProperties().getQueueBufferSize());
    }

    @Test
    public void testToConnectorPropertiesXmlContainsFieldValues() throws Exception {
        VmDispatcherProperties properties = new VmDispatcherProperties();
        properties.setChannelId("test-channel-id");

        String xml = ConnectorPropertiesUtil.toConnectorPropertiesXml(properties);
        assertTrue(xml.contains("test-channel-id"));
    }
}
