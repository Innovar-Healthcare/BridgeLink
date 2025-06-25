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

