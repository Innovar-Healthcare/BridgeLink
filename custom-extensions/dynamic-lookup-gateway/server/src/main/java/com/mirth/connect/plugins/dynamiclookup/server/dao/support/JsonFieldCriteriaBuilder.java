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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo.DatabaseType;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.LookupJsonCapability;

public class JsonFieldCriteriaBuilder {
    private JsonFieldCriteriaBuilder() {
    }

    public static List<JsonFieldCriterion> buildCriteria(LookupJsonCapability capability, String filterJson) {
        List<JsonFieldCriterion> list = new ArrayList<>();

        if (filterJson == null || filterJson.trim().isEmpty()) {
            return list;
        }

        if (!capability.isJsonSupported()) {
            throw new IllegalStateException("JSON FIELD search is not supported for this database.");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(filterJson);

            List<PathValue> paths = new ArrayList<>();
            collectLeafPaths(root, Collections.<String>emptyList(), paths);

            DatabaseType type = capability.getDatabaseInfo().getType();

            for (PathValue pv : paths) {
                if (pv.path.isEmpty()) {
                    continue;
                }

                String expr;

                switch (type) {
                case POSTGRESQL:
                    expr = buildPostgresExpression(pv.path);
                    break;
                case MYSQL:
                    expr = buildMysqlExpression(pv.path);
                    break;
                default:
                    throw new IllegalStateException("JSON FIELD search is not implemented for DB type: " + type);
                }

                JsonFieldCriterion c = new JsonFieldCriterion();
                c.setExpression(expr);
                c.setValue(pv.value);
                list.add(c);
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON filter for FIELD search", e);
        }

        return list;
    }

    /**
     * PostgreSQL: - top-level: VALUE_DATA->>'email' - nested: VALUE_DATA #>> '{address,city,zip}'
     */
    private static String buildPostgresExpression(List<String> path) {
        if (path.size() == 1) {
            String field = path.get(0);
            return "VALUE_DATA->>'" + field + "'";
        } else {
            String joined = String.join(",", path);
            return "VALUE_DATA #>> '{" + joined + "}'";
        }
    }

    /**
     * MySQL 8+: - top-level: JSON_UNQUOTE(JSON_EXTRACT(VALUE_DATA, '$.email')) - nested:
     * JSON_UNQUOTE(JSON_EXTRACT(VALUE_DATA, '$.address.city'))
     */
    private static String buildMysqlExpression(List<String> path) {
        String jsonPath = "$." + String.join(".", path);
        return "CAST(JSON_UNQUOTE(JSON_EXTRACT(VALUE_DATA, '" + jsonPath + "')) AS CHAR(255))";
    }

    private static void collectLeafPaths(JsonNode node, List<String> currentPath, List<PathValue> out) {
        if (node.isValueNode()) {
            if (!currentPath.isEmpty()) {
                out.add(new PathValue(new ArrayList<>(currentPath), node.asText()));
            }

            return;
        }

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String key = e.getKey();
                JsonNode child = e.getValue();

                List<String> nextPath = new ArrayList<>(currentPath.size() + 1);
                nextPath.addAll(currentPath);
                nextPath.add(key);

                collectLeafPaths(child, nextPath, out);
            }
        }
    }

    private static class PathValue {
        final List<String> path;
        final String value;

        PathValue(List<String> path, String value) {
            this.path = path;
            this.value = value;
        }
    }
}
