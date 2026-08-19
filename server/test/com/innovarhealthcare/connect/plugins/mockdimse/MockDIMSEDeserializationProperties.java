/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Sample extension for the declarative WebAdmin dataTypes contribution kind (IRT-1442). This group
 * declares no fields; it exists so the deserializationProperties element appears (empty) in the
 * serialized defaults.
 */

package com.innovarhealthcare.connect.plugins.mockdimse;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.model.datatype.DataTypePropertyDescriptor;
import com.mirth.connect.model.datatype.DeserializationProperties;

public class MockDIMSEDeserializationProperties extends DeserializationProperties {

    @Override
    public Map<String, DataTypePropertyDescriptor> getPropertyDescriptors() {
        return new LinkedHashMap<String, DataTypePropertyDescriptor>();
    }

    @Override
    public void setProperties(Map<String, Object> properties) {}

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}
    @Override public void migrate3_1_0(DonkeyElement element) {} // @formatter:on

    @Override
    public Map<String, Object> getPurgedProperties() {
        return new HashMap<String, Object>();
    }
}
