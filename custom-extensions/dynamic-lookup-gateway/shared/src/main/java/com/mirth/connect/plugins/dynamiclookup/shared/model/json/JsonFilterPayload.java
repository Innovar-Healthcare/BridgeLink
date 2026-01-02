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

import java.util.ArrayList;
import java.util.List;

//@formatter:off
/**
 * Top-level JSON filter payload that will be serialized/deserialized
 * between client and server.
 *
 * Example JSON (version 1):
 *
 * {
 *   "version": 1,
 *   "logic": "AND",
 *   "conditions": [
 *     { "field": "outputEntity", "op": "EQUAL", "value": "adt" },
 *     { "field": "validationErrors", "op": "EQUAL", "value": 0 }
 *   ]
 * }
 */
//@formatter:on
public class JsonFilterPayload {

    /**
     * Payload version for future-proofing. Current version: 1.
     */
    private int version = 1;

    /**
     * Top-level logic between conditions. Phase 1: only "AND" is used. Later: could support "OR", grouping, etc.
     */
    private String logic = "AND";

    /**
     * List of simple field conditions. All are combined according to {@link #logic}.
     */
    private List<JsonCondition> conditions = new ArrayList<>();

    public JsonFilterPayload() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getLogic() {
        return logic;
    }

    public void setLogic(String logic) {
        this.logic = logic;
    }

    public List<JsonCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<JsonCondition> conditions) {
        this.conditions = (conditions != null) ? conditions : new ArrayList<JsonCondition>();
    }

    public void addCondition(JsonCondition condition) {
        if (condition == null) {
            return;
        }

        if (conditions == null) {
            conditions = new ArrayList<>();
        }

        conditions.add(condition);
    }

    public boolean isEmpty() {
        return conditions == null || conditions.isEmpty();
    }

    //@formatter:off
    @Override
    public String toString() {
        return "JsonFilterPayload{" +
                "version=" + version +
                ", logic='" + logic + '\'' +
                ", conditions=" + conditions +
                '}';
    }
    //@formatter:on
}
