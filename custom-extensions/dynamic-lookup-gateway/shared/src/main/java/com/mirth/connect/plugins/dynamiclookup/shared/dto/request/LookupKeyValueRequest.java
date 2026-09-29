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

import org.apache.commons.lang3.StringUtils;

/**
 * Carries both the lookup key and its value in the request body instead of the URL path, so
 * that keys containing characters Jetty 12 rejects in a path segment ({@code /}, {@code %},
 * {@code \}, control characters) can still be set or updated (IRT-1997).
 */
public class LookupKeyValueRequest {
    private String key;
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void validate() throws IllegalArgumentException {
        // Reject only null/empty for the key, NOT blank: a whitespace/control-only key is a
        // legitimate stored key and must remain editable (IRT-1997). The value keeps the blank
        // check to match the existing LookupValueRequest contract.
        if (StringUtils.isEmpty(key)) {
            throw new IllegalArgumentException("Missing required field: key");
        }
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("Missing required field: value");
        }
    }
}
