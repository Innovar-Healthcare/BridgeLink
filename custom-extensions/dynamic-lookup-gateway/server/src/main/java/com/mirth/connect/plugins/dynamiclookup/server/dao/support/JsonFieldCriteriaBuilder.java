/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.dao.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonFieldCriteriaBuilder {
    private JsonFieldCriteriaBuilder() {
    }

    public static List<JsonFieldCriterion> buildCriteria(Map<String, String> filters) {
        List<JsonFieldCriterion> list = new ArrayList<>();

        if (filters == null || filters.isEmpty()) {
            return list;
        }

        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String fieldPath = entry.getKey();
            String value = entry.getValue();

            if (fieldPath == null || fieldPath.isEmpty()) {
                continue;
            }

            // Build SQL expression from field path
            String expr;
            if (!fieldPath.contains(".")) {
                expr = "VALUE_DATA->>'" + fieldPath + "'";
            } else {
                String path = "{" + fieldPath.replace(".", ",") + "}";
                expr = "VALUE_DATA #>> '" + path + "'";
            }

            JsonFieldCriterion c = new JsonFieldCriterion();
            c.setExpression(expr);
            c.setValue(value);

            list.add(c);
        }

        return list;
    }
}
