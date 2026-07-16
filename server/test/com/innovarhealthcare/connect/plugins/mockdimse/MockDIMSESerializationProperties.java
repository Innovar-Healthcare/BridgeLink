/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Sample extension for the declarative WebAdmin dataTypes contribution kind (IRT-1442). The field
 * names below MUST exactly match the serialization-group field keys declared in
 * webadmin/webadmin.json — the engine's channel deserialization is strict and rejects unknown
 * child elements.
 */

package com.innovarhealthcare.connect.plugins.mockdimse;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.model.datatype.DataTypePropertyDescriptor;
import com.mirth.connect.model.datatype.PropertyEditorType;
import com.mirth.connect.model.datatype.SerializationProperties;

public class MockDIMSESerializationProperties extends SerializationProperties {

    private String transferSyntax = "implicitLittleEndian";
    private boolean decodePixelData = false;
    private int maxPduLength = 16384;

    @Override
    public Map<String, DataTypePropertyDescriptor> getPropertyDescriptors() {
        Map<String, DataTypePropertyDescriptor> properties = new LinkedHashMap<String, DataTypePropertyDescriptor>();

        properties.put("transferSyntax", new DataTypePropertyDescriptor(transferSyntax, "Transfer Syntax", "DICOM transfer syntax expected on inbound associations.", PropertyEditorType.STRING));
        properties.put("decodePixelData", new DataTypePropertyDescriptor(decodePixelData, "Decode Pixel Data", "Decode the PixelData element when converting to XML.", PropertyEditorType.BOOLEAN));
        properties.put("maxPduLength", new DataTypePropertyDescriptor(String.valueOf(maxPduLength), "Max PDU Length", "Maximum protocol data unit length in bytes.", PropertyEditorType.STRING));

        return properties;
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        if (properties != null) {
            if (properties.get("transferSyntax") != null) {
                transferSyntax = (String) properties.get("transferSyntax");
            }

            if (properties.get("decodePixelData") != null) {
                decodePixelData = (Boolean) properties.get("decodePixelData");
            }

            if (properties.get("maxPduLength") != null) {
                maxPduLength = Integer.parseInt(properties.get("maxPduLength").toString());
            }
        }
    }

    public String getTransferSyntax() {
        return transferSyntax;
    }

    public void setTransferSyntax(String transferSyntax) {
        this.transferSyntax = transferSyntax;
    }

    public boolean isDecodePixelData() {
        return decodePixelData;
    }

    public void setDecodePixelData(boolean decodePixelData) {
        this.decodePixelData = decodePixelData;
    }

    public int getMaxPduLength() {
        return maxPduLength;
    }

    public void setMaxPduLength(int maxPduLength) {
        this.maxPduLength = maxPduLength;
    }

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}
    @Override public void migrate3_1_0(DonkeyElement element) {} // @formatter:on

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purgedProperties = new HashMap<String, Object>();
        purgedProperties.put("transferSyntax", transferSyntax);
        purgedProperties.put("decodePixelData", decodePixelData);
        purgedProperties.put("maxPduLength", maxPduLength);
        return purgedProperties;
    }
}
