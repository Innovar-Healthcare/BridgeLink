/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Sample extension for the declarative WebAdmin dataTypes contribution kind (IRT-1442). Pass-through
 * serializer: the mock data type performs no real serialization, mirroring the built-in Raw type.
 */

package com.innovarhealthcare.connect.plugins.mockdimse;

import java.util.Map;

import com.mirth.connect.donkey.model.message.MessageSerializer;
import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.model.converters.IMessageSerializer;
import com.mirth.connect.model.datatype.SerializerProperties;

public class MockDIMSESerializer implements IMessageSerializer {

    public MockDIMSESerializer(SerializerProperties properties) {}

    @Override
    public boolean isSerializationRequired(boolean toXml) {
        return false;
    }

    @Override
    public String transformWithoutSerializing(String message, MessageSerializer outboundSerializer) {
        return null;
    }

    @Override
    public String toXML(String source) throws MessageSerializerException {
        return null;
    }

    @Override
    public String fromXML(String source) throws MessageSerializerException {
        return null;
    }

    @Override
    public Map<String, Object> getMetaDataFromMessage(String message) {
        return null;
    }

    @Override
    public void populateMetaData(String message, Map<String, Object> map) {}

    @Override
    public String toJSON(String message) throws MessageSerializerException {
        return null;
    }

    @Override
    public String fromJSON(String message) throws MessageSerializerException {
        return null;
    }
}
