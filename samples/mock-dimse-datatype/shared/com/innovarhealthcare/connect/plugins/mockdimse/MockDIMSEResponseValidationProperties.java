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
 * names below MUST exactly match the responseValidation-group field keys declared in
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
import com.mirth.connect.model.datatype.ResponseValidationProperties;

public class MockDIMSEResponseValidationProperties extends ResponseValidationProperties {

    private boolean successOnAccept = true;

    @Override
    public Map<String, DataTypePropertyDescriptor> getPropertyDescriptors() {
        Map<String, DataTypePropertyDescriptor> properties = new LinkedHashMap<String, DataTypePropertyDescriptor>();

        properties.put("successOnAccept", new DataTypePropertyDescriptor(successOnAccept, "Successful on C-STORE Accept", "Treat an accepted C-STORE response as a successful message response.", PropertyEditorType.BOOLEAN));

        return properties;
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        if (properties != null) {
            if (properties.get("successOnAccept") != null) {
                successOnAccept = (Boolean) properties.get("successOnAccept");
            }
        }
    }

    public boolean isSuccessOnAccept() {
        return successOnAccept;
    }

    public void setSuccessOnAccept(boolean successOnAccept) {
        this.successOnAccept = successOnAccept;
    }

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}
    @Override public void migrate3_1_0(DonkeyElement element) {} // @formatter:on

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purgedProperties = new HashMap<String, Object>();
        purgedProperties.put("successOnAccept", successOnAccept);
        return purgedProperties;
    }
}
