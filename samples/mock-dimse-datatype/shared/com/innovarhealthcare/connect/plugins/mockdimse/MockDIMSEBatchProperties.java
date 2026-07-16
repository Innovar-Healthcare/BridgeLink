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
 * names below MUST exactly match the batch-group field keys declared in webadmin/webadmin.json —
 * the engine's channel deserialization is strict and rejects unknown child elements.
 */

package com.innovarhealthcare.connect.plugins.mockdimse;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.donkey.util.purge.PurgeUtil;
import com.mirth.connect.model.datatype.BatchProperties;
import com.mirth.connect.model.datatype.DataTypePropertyDescriptor;
import com.mirth.connect.model.datatype.PropertyEditorType;

public class MockDIMSEBatchProperties extends BatchProperties {

    public enum SplitType {
        Association, JavaScript;
    }

    private SplitType splitType = SplitType.JavaScript;
    private String batchScript = "";

    @Override
    public Map<String, DataTypePropertyDescriptor> getPropertyDescriptors() {
        Map<String, DataTypePropertyDescriptor> properties = new LinkedHashMap<String, DataTypePropertyDescriptor>();

        properties.put("splitType", new DataTypePropertyDescriptor(splitType, "Split Batch By", "Select the method for splitting the batch message. This option has no effect unless Process Batch is enabled in the connector.", PropertyEditorType.OPTION, SplitType.values()));
        properties.put("batchScript", new DataTypePropertyDescriptor(batchScript, "JavaScript", "Enter JavaScript that splits the batch, and returns the next message. This script has access to 'reader', a Java BufferedReader, to read the incoming data stream.", PropertyEditorType.JAVASCRIPT));

        return properties;
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        if (properties != null) {
            if (properties.get("splitType") != null) {
                splitType = (SplitType) properties.get("splitType");
            }

            if (properties.get("batchScript") != null) {
                batchScript = (String) properties.get("batchScript");
            }
        }
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public void setSplitType(SplitType splitType) {
        this.splitType = splitType;
    }

    public String getBatchScript() {
        return batchScript;
    }

    public void setBatchScript(String batchScript) {
        this.batchScript = batchScript;
    }

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}
    @Override public void migrate3_1_0(DonkeyElement element) {} // @formatter:on

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purgedProperties = new HashMap<String, Object>();
        purgedProperties.put("splitType", splitType);
        purgedProperties.put("batchScriptLines", PurgeUtil.countLines(batchScript));
        return purgedProperties;
    }
}
