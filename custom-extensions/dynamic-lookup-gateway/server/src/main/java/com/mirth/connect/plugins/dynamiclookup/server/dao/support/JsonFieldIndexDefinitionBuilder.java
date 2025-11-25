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
            String computedColumnName = "";

            switch (type) {
            case POSTGRESQL:
                expression = buildPostgresExpression(fieldPath);
                break;
            case MYSQL:
                expression = buildMysqlExpression(fieldPath);
                break;
            case SQLSERVER:
                expression = buildSqlServerExpression(fieldPath);
                computedColumnName = buildSqlServerComputedColumnName(fieldPath);
                break;
            case ORACLE:
            default:
                continue;
            }

            JsonFieldIndexDefinition def = new JsonFieldIndexDefinition();
            def.setIndexName(indexName);
            def.setExpression(expression);
            def.setComputedColumnName(computedColumnName);
            list.add(def);
        }

        return list;
    }

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

    /**
     * SQL Server JSON expression: - top-level: JSON_VALUE(VALUE_DATA, '$.email') - nested: JSON_VALUE(VALUE_DATA,
     * '$.address.city')
     */
    private static String buildSqlServerExpression(String fieldPath) {
        // SQL Server
        String jsonPath = "$." + fieldPath; // VD: address.city
        return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "')";
    }

    /**
     * Builds a safe SQL Server computed column name from a JSON field path.
     *
     * Rules: - Split field path by "." - Normalize each segment: uppercase and replace non-alphanumeric chars with "_" -
     * Join with "_" - Prefix with "JSON_" to avoid collisions with real table columns
     *
     * Examples: "email" -> JSON_EMAIL "address.city" -> JSON_ADDRESS_CITY "user-name" -> JSON_USER_NAME
     */
    private static String buildSqlServerComputedColumnName(String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty()) {
            throw new IllegalArgumentException("fieldPath must not be null or empty");
        }

        // Split by dot for nested JSON keys
        String[] parts = fieldPath.split("\\.");

        // Normalize each segment
        StringBuilder sb = new StringBuilder("JSON_");
        for (int i = 0; i < parts.length; i++) {
            String segment = parts[i].toUpperCase().replaceAll("[^A-Z0-9]", "_"); // replace invalid chars

            sb.append(segment);
            if (i < parts.length - 1) {
                sb.append("_");
            }
        }

        return sb.toString();
    }

    private static String sanitizeForIdentifier(String fieldPath) {
        // "address.city" -> "address_city"
        return fieldPath.replaceAll("[^a-zA-Z0-9]+", "_").toLowerCase();
    }
}
