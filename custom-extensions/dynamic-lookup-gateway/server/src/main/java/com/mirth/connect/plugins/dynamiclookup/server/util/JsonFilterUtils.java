/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonFilterUtils {
    private JsonFilterUtils() {
    }

    public static Map<String, String> parseFilterJson(String filterJson) {
        Map<String, String> result = new LinkedHashMap<>();

        if (filterJson == null || filterJson.trim().isEmpty()) {
            return result;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(filterJson);

            List<PathValue> paths = new ArrayList<>();
            collectLeafPaths(root, Collections.emptyList(), paths);

            for (PathValue pv : paths) {
                String key = String.join(".", pv.path); // e.g. ["address","city"] → "address.city"
                result.put(key, pv.value);
            }

            return result;

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON filter: " + e.getMessage(), e);
        }
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
