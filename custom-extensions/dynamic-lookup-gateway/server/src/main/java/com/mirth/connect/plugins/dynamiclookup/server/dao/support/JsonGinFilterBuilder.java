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

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonGinFilterBuilder {
    private JsonGinFilterBuilder() {
    }

    public static String buildFilter(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }

        Map<String, Object> root = new LinkedHashMap<>();

        for (Map.Entry<String, String> e : filters.entrySet()) {
            String fieldPath = e.getKey(); // "email" or "address.city"
            String value = e.getValue();

            if (fieldPath == null || fieldPath.isEmpty()) {
                continue;
            }

            String[] parts = fieldPath.split("\\.");
            Map<String, Object> current = root;

            // walk nested objects except last
            for (int i = 0; i < parts.length - 1; i++) {
                String p = parts[i].trim();
                if (p.isEmpty()) {
                    continue;
                }

                Object existing = current.get(p);
                if (!(existing instanceof Map)) {
                    Map<String, Object> child = new LinkedHashMap<>();
                    current.put(p, child);
                    current = child;
                } else {
                    // noinspection unchecked
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) existing;
                    current = map;
                }
            }

            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                current.put(last, value);
            }
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to build JSON filter for GIN search", ex);
        }
    }
}
