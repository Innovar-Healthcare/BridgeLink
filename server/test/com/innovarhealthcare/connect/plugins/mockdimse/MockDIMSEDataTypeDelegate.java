/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Sample extension for the declarative WebAdmin dataTypes contribution kind (IRT-1442). getName()
 * is the data type's plugin point name — it MUST exactly match the dataTypes[].name declared in
 * webadmin/webadmin.json and is the {dataTypeName} segment of the engine's datatype-defaults
 * endpoint.
 */

package com.innovarhealthcare.connect.plugins.mockdimse;

import com.mirth.connect.donkey.model.message.SerializationType;
import com.mirth.connect.model.converters.IMessageSerializer;
import com.mirth.connect.model.datatype.DataTypeDelegate;
import com.mirth.connect.model.datatype.DataTypeProperties;
import com.mirth.connect.model.datatype.SerializerProperties;

public class MockDIMSEDataTypeDelegate implements DataTypeDelegate {

    @Override
    public String getName() {
        return "MockDIMSE";
    }

    @Override
    public IMessageSerializer getSerializer(SerializerProperties properties) {
        return new MockDIMSESerializer(properties);
    }

    @Override
    public boolean isBinary() {
        return true;
    }

    @Override
    public SerializationType getDefaultSerializationType() {
        return SerializationType.RAW;
    }

    @Override
    public DataTypeProperties getDefaultProperties() {
        return new MockDIMSEDataTypeProperties();
    }
}
