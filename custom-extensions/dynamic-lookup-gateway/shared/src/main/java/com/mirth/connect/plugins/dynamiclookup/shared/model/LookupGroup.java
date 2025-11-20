/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.model;

import java.util.Date;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-05-13 10:25 AM
 */
public class LookupGroup {
    private int id;
    private String name;
    private String description;
    private String version;
    private int cacheSize; // Default: 1000
    private String cachePolicy; // "LRU" or "FIFO"
    private Date createdDate;
    private Date updatedDate;
    private String valueType; // "TEXT" or "JSON"
    private LookupGroupExtra extra;

    public LookupGroup() {
        id = 0;
        name = "";
        description = "";
        version = "";
        cacheSize = 1000;
        cachePolicy = "LRU";
        valueType = "TEXT";
    }

    public LookupGroup(LookupGroup other) {
        this.id = other.id;
        this.name = other.name;
        this.description = other.description;
        this.version = other.version;
        this.cacheSize = other.cacheSize;
        this.cachePolicy = other.cachePolicy;
        this.createdDate = other.createdDate != null ? new Date(other.createdDate.getTime()) : null;
        this.updatedDate = other.updatedDate != null ? new Date(other.updatedDate.getTime()) : null;
        this.valueType = other.valueType;
        this.extra = other.extra;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

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

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public String getCachePolicy() {
        return cachePolicy;
    }

    public void setCachePolicy(String cachePolicy) {
        this.cachePolicy = cachePolicy;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(Date updatedDate) {
        this.updatedDate = updatedDate;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public LookupGroupExtra getExtra() {
        return extra;
    }

    public void setExtra(LookupGroupExtra extra) {
        this.extra = extra;
    }
}
