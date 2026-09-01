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
 * Carries a single lookup key in the request body instead of the URL path, so that keys
 * containing characters Jetty 12 rejects in a path segment ({@code /}, {@code %}, {@code \},
 * control characters) can still be fetched or deleted (IRT-1997).
 */
public class LookupKeyRequest {
    private String key;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void validate() throws IllegalArgumentException {
        // Reject only null/empty, NOT blank: a key that is only whitespace or control characters
        // is a legitimate (if unusual) stored key — rejecting it here would reintroduce the very
        // "cannot delete this value" symptom IRT-1997 fixes.
        if (StringUtils.isEmpty(key)) {
            throw new IllegalArgumentException("Missing required field: key");
        }
    }
}
