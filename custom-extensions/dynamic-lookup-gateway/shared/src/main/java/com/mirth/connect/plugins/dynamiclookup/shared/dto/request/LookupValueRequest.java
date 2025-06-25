package com.mirth.connect.plugins.dynamiclookup.shared.dto.request;

import org.apache.commons.lang3.StringUtils;

public class LookupValueRequest {
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void validate() throws IllegalArgumentException {
        if (value == null || StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("Missing required field: value");
        }
    }
}
