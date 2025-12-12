/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.model.json;

import java.util.Objects;

public class JsonCondition {

    /**
     * JSON field path, e.g. "email", "address.city".
     */
    private String field;

    /**
     * Operator, e.g. EQUAL. See {@link JsonOperator}.
     */
    private JsonOperator op;

    /**
     * Typed value: "adt", 0, true, null, etc.
     *
     * When deserialized from JSON this will typically be a String / Number / Boolean / List / Map / null.
     */
    private Object value;

    public JsonCondition() {
    }

    public JsonCondition(String field, JsonOperator op, Object value) {
        this.field = field;
        this.op = op;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public JsonOperator getOp() {
        return op;
    }

    public void setOp(JsonOperator op) {
        this.op = op;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    //@formatter:off
    @Override
    public String toString() {
        return "JsonCondition{" +
                "field='" + field + '\'' +
                ", op=" + op +
                ", value=" + value +
                '}';
    }
    //@formatter:on

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof JsonCondition)) {
            return false;
        }

        JsonCondition that = (JsonCondition) o;
        return Objects.equals(field, that.field) && op == that.op && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, op, value);
    }
}