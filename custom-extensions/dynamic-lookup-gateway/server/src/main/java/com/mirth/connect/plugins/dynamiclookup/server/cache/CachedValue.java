/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.cache;

import java.util.Date;

public class CachedValue {

    private final String value;
    private final Date updatedAt;

    /**
     * Creates a new CachedValue instance.
     *
     * @param value     The actual value associated with the key.
     * @param updatedAt The timestamp from the database indicating when the value was last updated.
     */
    public CachedValue(String value, Date updatedAt) {
        this.value = value;
        this.updatedAt = updatedAt;
    }

    public String getValue() {
        return value;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "CachedValue{value='" + value + "', updatedAt=" + updatedAt + '}';
    }
}

