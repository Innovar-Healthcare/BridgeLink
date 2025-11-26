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

import java.util.LinkedHashSet;
import java.util.Set;

import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

public class JsonFieldUtils {
    /**
     * Normalize a JSON field path.
     *
     * We only allow [A-Za-z0-9_.] in field paths for two reasons:
     *
     * 1) JSON path expressions generally expect simple identifier-like segments. Keys containing special characters (@, -,
     * space, ', ", [], etc.) would require escaping or a different syntax, which we do not support.
     *
     * 2) Field paths are later embedded into SQL strings. Restricting the allowed character set makes the generated
     * expressions predictable and prevents SQL-breakage or injection risks.
     *
     * Therefore: - Trim the input. - If blank, return "". - Replace any character outside [A-Za-z0-9_.] with '_'.
     *
     * This ensures the normalized field path is safe for both JSON path expressions and SQL string concatenation.
     */
    public static String normalizeFieldPath(String fieldPath) {
        if (fieldPath == null) {
            return "";
        }

        String normalized = fieldPath.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        return normalized.replaceAll("[^A-Za-z0-9_.]", "_");
    }

    /**
     * Parse indexedJsonFields from a JSON array string. Example: ["email","address.city"]
     */
    public static Set<String> parseIndexedFieldPaths(String jsonArray) {
        Set<String> result = new LinkedHashSet<>();

        if (jsonArray == null) {
            return result;
        }

        String trimmed = jsonArray.trim();
        if (trimmed.isEmpty()) {
            return result;
        }

        // Only JSON array is supported
        for (String raw : JsonUtils.fromJsonList(trimmed, String.class)) {
            String normalized = normalizeFieldPath(raw);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }

        return result;
    }
}
