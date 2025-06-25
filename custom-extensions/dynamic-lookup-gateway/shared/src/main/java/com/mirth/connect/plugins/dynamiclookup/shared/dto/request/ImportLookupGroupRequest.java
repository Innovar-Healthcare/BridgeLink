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
        // values is optional
//        if (values == null || values.isEmpty()) {
//            throw new IllegalArgumentException("Values must not be empty.");
//        }
    }
}
