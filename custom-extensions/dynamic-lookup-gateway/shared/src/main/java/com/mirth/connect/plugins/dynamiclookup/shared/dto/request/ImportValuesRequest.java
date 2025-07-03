/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.dto.request;

import java.util.Map;

public class ImportValuesRequest {
    private Map<String, String> values;

    public ImportValuesRequest() {
    }

    public ImportValuesRequest(Map<String, String> values) {
        this.values = values;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values = values;
    }

    public void validate() {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("The 'values' object must not be null or empty.");
        }

        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                throw new IllegalArgumentException("Key cannot be null or empty.");
            }
            if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                throw new IllegalArgumentException("Value for key '" + entry.getKey() + "' cannot be null or empty.");
            }
            // Optional: add length, pattern, or format checks here
        }
    }
}