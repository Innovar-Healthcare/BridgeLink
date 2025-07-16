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

import java.util.List;

public class BatchGetValuesRequest {
    private List<String> keys;

    public BatchGetValuesRequest() {
    }

    public BatchGetValuesRequest(List<String> keys) {
        this.keys = keys;
    }

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    public void validate() {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("The 'keys' list must not be null or empty.");
        }
    }
}

