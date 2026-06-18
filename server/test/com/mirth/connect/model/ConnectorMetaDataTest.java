/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 model POJO coverage — ConnectorMetaData getter/setter/equals/getPurgedProperties.
 */

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class ConnectorMetaDataTest {

    // ------------------------------------------------------------------
    // Default constructor: all fields null
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_allFieldsNull() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        assertNull(cmd.getServerClassName());
        assertNull(cmd.getSharedClassName());
        assertNull(cmd.getClientClassName());
        assertNull(cmd.getTransformers());
        assertNull(cmd.getProtocol());
        assertNull(cmd.getType());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setServerClassName_roundTrip() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        cmd.setServerClassName("com.mirth.connect.connectors.file.FileConnector");
        assertEquals("com.mirth.connect.connectors.file.FileConnector", cmd.getServerClassName());
    }

    @Test
    public void setSharedClassName_roundTrip() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        cmd.setSharedClassName("com.mirth.connect.connectors.file.FileShared");
        assertEquals("com.mirth.connect.connectors.file.FileShared", cmd.getSharedClassName());
    }

    @Test
    public void setClientClassName_roundTrip() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        cmd.setClientClassName("com.mirth.connect.connectors.file.FileClient");
        assertEquals("com.mirth.connect.connectors.file.FileClient", cmd.getClientClassName());
    }

    @Test
    public void setTransformers_roundTrip() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        cmd.setTransformers("HL7,DICOM");
        assertEquals("HL7,DICOM", cmd.getTransformers());
    }

    @Test
    public void setProtocol_roundTrip() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        cmd.setProtocol("FILE");
        assertEquals("FILE", cmd.getProtocol());
    }

    @Test
    public void setType_source_roundTrip() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        cmd.setType(ConnectorMetaData.Type.SOURCE);
        assertEquals(ConnectorMetaData.Type.SOURCE, cmd.getType());
    }

    @Test
    public void setType_destination_roundTrip() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        cmd.setType(ConnectorMetaData.Type.DESTINATION);
        assertEquals(ConnectorMetaData.Type.DESTINATION, cmd.getType());
    }

    // ------------------------------------------------------------------
    // equals: EqualsBuilder.reflectionEquals
    // ------------------------------------------------------------------

    @Test
    public void equals_sameFieldValues_returnsTrue() {
        ConnectorMetaData c1 = new ConnectorMetaData();
        c1.setServerClassName("com.example.Server");
        c1.setProtocol("TCP");
        c1.setType(ConnectorMetaData.Type.SOURCE);

        ConnectorMetaData c2 = new ConnectorMetaData();
        c2.setServerClassName("com.example.Server");
        c2.setProtocol("TCP");
        c2.setType(ConnectorMetaData.Type.SOURCE);

        assertTrue(c1.equals(c2));
    }

    @Test
    public void equals_differentProtocol_returnsFalse() {
        ConnectorMetaData c1 = new ConnectorMetaData();
        c1.setProtocol("TCP");

        ConnectorMetaData c2 = new ConnectorMetaData();
        c2.setProtocol("HTTP");

        assertFalse(c1.equals(c2));
    }

    @Test
    public void equals_sameInstance_returnsTrue() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        cmd.setServerClassName("com.example.Server");
        assertTrue(cmd.equals(cmd));
    }

    // ------------------------------------------------------------------
    // toString: non-null, non-empty
    // ------------------------------------------------------------------

    @Test
    public void toString_returnsNonEmpty() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        cmd.setServerClassName("com.example.TestServer");
        cmd.setProtocol("VM");
        String str = cmd.toString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
    }

    // ------------------------------------------------------------------
    // getPurgedProperties: contains expected keys
    // ------------------------------------------------------------------

    @Test
    public void getPurgedProperties_containsAllKeys() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        cmd.setServerClassName("com.example.Server");
        cmd.setSharedClassName("com.example.Shared");
        cmd.setClientClassName("com.example.Client");
        cmd.setTransformers("HL7");
        cmd.setProtocol("HTTP");
        cmd.setType(ConnectorMetaData.Type.DESTINATION);

        Map<String, Object> purged = cmd.getPurgedProperties();
        assertNotNull(purged);
        assertTrue(purged.containsKey("serverClassName"));
        assertTrue(purged.containsKey("sharedClassName"));
        assertTrue(purged.containsKey("clientClassName"));
        assertTrue(purged.containsKey("transformers"));
        assertTrue(purged.containsKey("protocol"));
        assertTrue(purged.containsKey("type"));
        assertEquals("com.example.Server", purged.get("serverClassName"));
        assertEquals("HTTP", purged.get("protocol"));
        assertEquals(ConnectorMetaData.Type.DESTINATION, purged.get("type"));
    }

    @Test
    public void getPurgedProperties_withNullFields_emptyStringValues() {
        ConnectorMetaData cmd = new ConnectorMetaData();
        Map<String, Object> purged = cmd.getPurgedProperties();
        assertNotNull(purged);
        assertNull(purged.get("serverClassName"));
        assertNull(purged.get("protocol"));
    }
}
