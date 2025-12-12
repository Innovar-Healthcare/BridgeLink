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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonCondition;

public class AdvancedJsonFilterState {

    private String keyPattern;
    private final List<JsonCondition> conditions = new ArrayList<>();

    public String getKeyPattern() {
        return keyPattern;
    }

    public void setKeyPattern(String keyPattern) {
        this.keyPattern = keyPattern;
    }

    public List<JsonCondition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    public void setConditions(List<JsonCondition> newConditions) {
        conditions.clear();
        if (newConditions != null) {
            conditions.addAll(newConditions);
        }
    }

    public void addCondition(JsonCondition condition) {
        if (condition != null) {
            conditions.add(condition);
        }
    }

    public void clearConditions() {
        conditions.clear();
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }
}