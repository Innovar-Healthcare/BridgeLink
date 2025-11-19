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

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;

public class LookupGroupRequest {
    private String name; // Required
    private String description; // Optional
    private String version; // Required
    private Integer cacheSize; // Required
    private String cachePolicy; // Required
    private LookupGroupExtra extra; // Option

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        this.cacheSize = cacheSize;
    }

    public String getCachePolicy() {
        return cachePolicy;
    }

    public void setCachePolicy(String cachePolicy) {
        this.cachePolicy = cachePolicy;
    }

    public LookupGroupExtra getExtra() {
        return extra;
    }

    public void setExtra(LookupGroupExtra extra) {
        this.extra = extra;
    }

    public void validate() throws IllegalArgumentException {
        if (name == null || StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Missing required field: name");
        }
        if (version == null || StringUtils.isBlank(version)) {
            throw new IllegalArgumentException("Missing required field: version");
        }

        if (cachePolicy == null || StringUtils.isBlank(cachePolicy)) {
            throw new IllegalArgumentException("Missing required field: cachePolicy");
        }

        if (!cachePolicy.equalsIgnoreCase("LRU") && !cachePolicy.equalsIgnoreCase("FIFO")) {
            throw new IllegalArgumentException("Invalid cachePolicy. Allowed values are: LRU, FIFO.");
        }

        if (cacheSize == null || cacheSize < 0) {
            throw new IllegalArgumentException("Missing or invalid field: cacheSize");
        }
    }
}
