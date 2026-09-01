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
 * Carries a lookup group name in the request body instead of the URL path, so that group
 * names containing characters Jetty 12 rejects in a path segment ({@code /}, {@code %},
 * {@code \}, control characters) can still be resolved (IRT-1997).
 */
public class LookupNameRequest {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void validate() throws IllegalArgumentException {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Missing required field: name");
        }
    }
}
