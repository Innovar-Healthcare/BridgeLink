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

public class JsonFieldCriteriaBuilder {
    private JsonFieldCriteriaBuilder() {
    }

    public static List<JsonFieldCriterion> buildCriteria(String filterJson) {
        List<JsonFieldCriterion> list = new ArrayList<>();

        if (filterJson == null || filterJson.trim().isEmpty()) {
            return list;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(filterJson);

            List<PathValue> paths = new ArrayList<>();
            collectLeafPaths(root, Collections.<String>emptyList(), paths);

            for (PathValue pv : paths) {
                if (pv.path.isEmpty()) {
                    continue;
                }

                String expr;
                if (pv.path.size() == 1) {
                    // Top-level: VALUE_DATA->>'email'
                    String field = pv.path.get(0);
                    expr = "VALUE_DATA->>'" + field + "'";
                } else {
                    // Nested: VALUE_DATA #>> '{address,city,zip}'
                    String joined = String.join(",", pv.path);
                    expr = "VALUE_DATA #>> '{" + joined + "}'";
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
