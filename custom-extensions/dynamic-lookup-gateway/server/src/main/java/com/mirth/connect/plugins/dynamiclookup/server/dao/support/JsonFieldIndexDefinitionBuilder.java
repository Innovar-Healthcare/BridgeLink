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
import java.util.Set;

public class JsonFieldIndexDefinitionBuilder {

    public static List<JsonFieldIndexDefinition> build(String tableName, Set<String> fields) {
        List<JsonFieldIndexDefinition> list = new ArrayList<>();
        if (fields == null || fields.isEmpty()) {
            return list;
        }

        for (String fieldPath : fields) {
            if (fieldPath == null || fieldPath.isEmpty()) {
                continue;
            }

            String sanitized = sanitizeForIdentifier(fieldPath);
            String indexName = "idx_" + tableName + "_json_" + sanitized;
            String expression;

            if (!fieldPath.contains(".")) {
                expression = "VALUE_DATA->>'" + fieldPath + "'";
            } else {
                String path = "{" + fieldPath.replace(".", ",") + "}";
                expression = "VALUE_DATA #>> '" + path + "'";
            }

            JsonFieldIndexDefinition def = new JsonFieldIndexDefinition();
            def.setIndexName(indexName);
            def.setExpression(expression);
            list.add(def);
        }

        return list;
    }

    private static String sanitizeForIdentifier(String fieldPath) {
        // "address.city" -> "address_city"
        return fieldPath.replaceAll("[^a-zA-Z0-9]+", "_").toLowerCase();
    }
}
