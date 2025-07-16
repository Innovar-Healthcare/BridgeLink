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
public class LookupValue {
    private String keyValue;
    private String valueData;
    private Date createdDate;
    private Date updatedDate;

    public LookupValue() {
        keyValue = "";
        valueData = "";
    }

    public LookupValue(String keyValue, String valueData) {
        this.keyValue = keyValue;
        this.valueData = valueData;
    }

    public LookupValue(LookupValue other) {
        keyValue = other.keyValue;
        valueData = other.valueData;
        this.createdDate = other.createdDate != null ? new Date(other.createdDate.getTime()) : null;
        this.updatedDate = other.updatedDate != null ? new Date(other.updatedDate.getTime()) : null;
    }

    public String getKeyValue() {
        return keyValue;
    }

    public void setKeyValue(String keyValue) {
        this.keyValue = keyValue;
    }

    public String getValueData() {
        return valueData;
    }

    public void setValueData(String valueData) {
        this.valueData = valueData;
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
}
