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

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

import java.util.Map;

public class ImportLookupGroupRequest {
    private LookupGroup group;
    private Map<String, String> values;

    public LookupGroup getGroup() {
        return group;
    }

    public void setGroup(LookupGroup group) {
        this.group = group;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values = values;
    }

    public void validate() {
        if (group == null) {
            throw new IllegalArgumentException("Group metadata is required.");
        }

        if (group.getName() == null || group.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Group name is required.");
        }

        if (group.getVersion() == null || group.getVersion().trim().isEmpty()) {
            throw new IllegalArgumentException("Group version is required.");
        }

        if (group.getCachePolicy() == null || group.getCachePolicy().trim().isEmpty()) {
            throw new IllegalArgumentException("Cache policy is required.");
        }

        String policy = group.getCachePolicy().toUpperCase();
        if (!policy.equals("LRU") && !policy.equals("FIFO")) {
            throw new IllegalArgumentException("Cache policy must be either 'LRU' or 'FIFO'.");
        }

        if (group.getCacheSize() <= 0) {
            throw new IllegalArgumentException("Cache size must be greater than zero.");
        }

        if (values != null && values.size() > 100_000) {
            throw new IllegalArgumentException("Too many values in import request. Limit is 100,000.");
        }
    }
}
