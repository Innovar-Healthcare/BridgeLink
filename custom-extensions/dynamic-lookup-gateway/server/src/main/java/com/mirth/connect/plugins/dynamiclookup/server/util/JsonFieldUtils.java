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
     * Normalize a JSON field path. (trim, remove blank)
     */
    public static String normalizeFieldPath(String fieldPath) {
        if (fieldPath == null) {
            return "";
        }
        String normalized = fieldPath.trim();
        return normalized.isEmpty() ? "" : normalized;
    }

    /**
     * Parse indexedJsonFields (JSON array or comma-separated list). Example: ["email","address.city"]
     */
    public static Set<String> parseIndexedFieldPaths(String jsonArray) {
        if (jsonArray == null || jsonArray.trim().isEmpty()) {
            return new LinkedHashSet<>();
        }

        // Uses your existing JSON parser
        return new LinkedHashSet<>(JsonUtils.fromJsonList(jsonArray, String.class));
    }
}
