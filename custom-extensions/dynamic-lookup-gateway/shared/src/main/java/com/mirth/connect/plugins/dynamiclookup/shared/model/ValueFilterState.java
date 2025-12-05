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

import java.util.Objects;

import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

public class ValueFilterState {
    private String keyFilter;
    private String valueFilter;

    public static ValueFilterState empty() {
        return new ValueFilterState("", "");
    }

    // Constructors
    public ValueFilterState() {
    }

    public ValueFilterState(String keyFilter, String valueFilter) {
        this.keyFilter = normalize(keyFilter);
        this.valueFilter = normalize(valueFilter);
    }

    // Getters and setters
    public String getKeyFilter() {
        return keyFilter;
    }

    public void setKeyFilter(String keyFilter) {
        this.keyFilter = normalize(keyFilter);
    }

    public String getValueFilter() {
        return valueFilter;
    }

    public void setValueFilter(String valueFilter) {
        this.valueFilter = normalize(valueFilter);
    }

    // Utility: normalize string (null → "", trim → trim)
    private String normalize(String s) {
        return (s == null) ? "" : s.trim();
    }

    // Check if all fields are empty
    public boolean isEmpty() {
        return isNullOrEmpty(keyFilter) && isNullOrEmpty(valueFilter);
    }

    private boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    // Delegate JSON methods to JsonUtils
    public String toJson() throws Exception {
        return JsonUtils.toJson(this);
    }

    public static ValueFilterState fromJson(String json) throws Exception {
        return JsonUtils.fromJson(json, ValueFilterState.class);
    }

    //@formatter:off
    @Override
    public String toString() {
        return "ValueFilterState{" +
                "keyFilter='" + keyFilter + '\'' +
                ", valueFilter='" + valueFilter + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValueFilterState)) return false;
        ValueFilterState that = (ValueFilterState) o;
        return Objects.equals(keyFilter, that.keyFilter) &&
               Objects.equals(valueFilter, that.valueFilter);
    }
    //@formatter:on

    @Override
    public int hashCode() {
        return Objects.hash(keyFilter, valueFilter);
    }
}
