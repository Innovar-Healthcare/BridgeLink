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

import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo.DatabaseType;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.LookupJsonCapability;
import com.mirth.connect.plugins.dynamiclookup.shared.constant.LookupConstants;

public class JsonFieldIndexDefinitionBuilder {

    public static List<JsonFieldIndexDefinition> build(LookupJsonCapability capacity, String tableName, Set<String> fields) {
        List<JsonFieldIndexDefinition> list = new ArrayList<>();
        if (fields == null || fields.isEmpty()) {
            return list;
        }

        if (!capacity.isJsonIndexModeSupported(LookupConstants.JSON_INDEX_FIELD)) {
            return list;
        }

        DatabaseType type = capacity.getDatabaseInfo().getType();

        for (String fieldPath : fields) {
            if (fieldPath == null || fieldPath.isEmpty()) {
                continue;
            }

            String sanitized = sanitizeForIdentifier(fieldPath);
            String indexName = "idx_" + tableName + "_json_" + sanitized;
            String expression;

            switch (type) {
            case POSTGRESQL:
                expression = buildPostgresExpression(fieldPath);
                break;
            case MYSQL:
                expression = buildMysqlExpression(fieldPath);
                break;
            case SQLSERVER:
            case ORACLE:
            default:
                continue;
            }

            JsonFieldIndexDefinition def = new JsonFieldIndexDefinition();
            def.setIndexName(indexName);
            def.setExpression(expression);
            list.add(def);
        }

        return list;
    }

    /**
     * PostgreSQL expression: - top-level: VALUE_DATA->>'email' - nested: VALUE_DATA #>> '{address,city}'
     */
    private static String buildPostgresExpression(String fieldPath) {
        if (!fieldPath.contains(".")) {
            return "VALUE_DATA->>'" + fieldPath + "'";
        }

        String path = "{" + fieldPath.replace(".", ",") + "}";
        return "VALUE_DATA #>> '" + path + "'";
    }

    /**
     * MySQL 8+ expression: - top-level: JSON_UNQUOTE(JSON_EXTRACT(VALUE_DATA, '$.email')) - nested:
     * JSON_UNQUOTE(JSON_EXTRACT(VALUE_DATA, '$.address.city'))
     */
    private static String buildMysqlExpression(String fieldPath) {
        String jsonPath = "$." + fieldPath; // "address.city"
        return "CAST(JSON_UNQUOTE(JSON_EXTRACT(VALUE_DATA, '" + jsonPath + "')) AS CHAR(255))";
    }

    private static String sanitizeForIdentifier(String fieldPath) {
        // "address.city" -> "address_city"
        return fieldPath.replaceAll("[^a-zA-Z0-9]+", "_").toLowerCase();
    }
}
