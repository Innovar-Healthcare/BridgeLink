/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Sample extension for the declarative WebAdmin dataTypes contribution kind (IRT-1442). The
 * constructor sets ALL FIVE property groups so every group element appears in the serialized
 * defaults — the WebAdmin contract requires each manifest-declared group element to be present
 * in the datatype-defaults response.
 */

package com.innovarhealthcare.connect.plugins.mockdimse;

import java.util.HashMap;
import java.util.Map;

import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.model.datatype.DataTypeProperties;

public class MockDIMSEDataTypeProperties extends DataTypeProperties {

    public MockDIMSEDataTypeProperties() {
        serializationProperties = new MockDIMSESerializationProperties();
        deserializationProperties = new MockDIMSEDeserializationProperties();
        batchProperties = new MockDIMSEBatchProperties();
        responseGenerationProperties = new MockDIMSEResponseGenerationProperties();
        responseValidationProperties = new MockDIMSEResponseValidationProperties();
    }

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}
    @Override public void migrate3_1_0(DonkeyElement element) {} // @formatter:on

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purgedProperties = new HashMap<String, Object>();
        purgedProperties.put("serializationProperties", serializationProperties.getPurgedProperties());
        purgedProperties.put("batchProperties", batchProperties.getPurgedProperties());
        purgedProperties.put("responseValidationProperties", responseValidationProperties.getPurgedProperties());
        return purgedProperties;
    }
}
