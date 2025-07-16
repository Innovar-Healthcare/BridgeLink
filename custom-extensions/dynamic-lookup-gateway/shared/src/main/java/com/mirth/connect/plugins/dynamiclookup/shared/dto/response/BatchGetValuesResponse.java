/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.dto.response;

import java.util.List;
import java.util.Map;

public class BatchGetValuesResponse {
    private Integer groupId;
    private Map<String, String> values;
    private List<String> missingKeys;

    public BatchGetValuesResponse() {
    }

    public BatchGetValuesResponse(Integer groupId, Map<String, String> values, List<String> missingKeys) {
        this.groupId = groupId;
        this.values = values;
        this.missingKeys = missingKeys;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values = values;
    }

    public List<String> getMissingKeys() {
        return missingKeys;
    }

    public void setMissingKeys(List<String> missingKeys) {
        this.missingKeys = missingKeys;
    }
}

