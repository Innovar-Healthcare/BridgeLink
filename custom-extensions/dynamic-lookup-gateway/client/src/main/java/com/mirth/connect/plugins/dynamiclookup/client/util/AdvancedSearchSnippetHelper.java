/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.client.util;

import com.mirth.connect.plugins.dynamiclookup.shared.builder.AdvancedJsonFilterBuilder;
import com.mirth.connect.plugins.dynamiclookup.shared.model.AdvancedJsonFilterState;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

public final class AdvancedSearchSnippetHelper {

    private AdvancedSearchSnippetHelper() {
    }

    /**
     * Builds a JavaScript Transformer snippet from LookupGroup + AdvancedJsonFilterState.
     */
    public static String buildJavaScriptSnippetForAdvancedState(LookupGroup group, AdvancedJsonFilterState state) {
        if (group == null) {
            throw new IllegalArgumentException("LookupGroup cannot be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("AdvancedJsonFilterState cannot be null");
        }

        // NEW: Build filter as JSON array literal for JS (conditions form)
        String filterArrayJson = AdvancedJsonFilterBuilder.toConditionsJsonArray(state);
        if (filterArrayJson == null || filterArrayJson.trim().isEmpty()) {
            filterArrayJson = "[]";
        }

        // Key pattern (Advanced normally uses SQL LIKE)
        String keyPattern = state.getKeyPattern();

        return buildJavaScriptSnippet(group.getName(), keyPattern, filterArrayJson);
    }

    /**
     * Build final JavaScript snippet.
     */
    private static String buildJavaScriptSnippet(String groupName, String keyPattern, String filterArrayJson) {

        String escapedGroupName = escapeJs(groupName);
        String escapedKey = keyPattern != null ? escapeJs(keyPattern) : null;

        StringBuilder sb = new StringBuilder(1024);

        sb.append("// JavaScript snippet for Dynamic Lookup (Advanced Search)\n");
        sb.append("var groupName = \"").append(escapedGroupName).append("\";\n\n");

        // --- KEY PATTERN FILTER ---
        if (escapedKey != null && !escapedKey.isEmpty()) {
            sb.append("// Optional KEY pattern filter (SQL LIKE)\n");
            sb.append("var keyPattern = \"").append(escapedKey).append("\";\n\n");
        } else {
            sb.append("// No KEY pattern filter\n");
            sb.append("var keyPattern = null;\n\n");
        }

        // --- JSON FILTER (ARRAY FORM) ---
        sb.append("// JSON field filters (array form; easy to edit)\n");
        sb.append("// NOTE: The server normalizes \"value\" to text; valueType controls validation and casting (STRING/NUMBER/BOOLEAN).\n");
        sb.append("var filterObj = ").append(filterArrayJson).append(";\n");
        sb.append("var filterJson = JSON.stringify(filterObj);\n\n");

        // --- RESULT LIMIT NOTE ---
        sb.append("// NOTE:\n");
        sb.append("// The lookup returns only the FIRST 1000 matching entries.\n");
        sb.append("// This limit is applied to protect performance.\n\n");

        // --- EXECUTE LOOKUP ---
        sb.append("var start = new Date().getTime();\n");
        sb.append("var results = LookupHelper.searchValuesByJsonFields(\n");
        sb.append("    groupName,\n");
        sb.append("    keyPattern,\n");
        sb.append("    filterJson\n");
        sb.append(");\n");
        sb.append("var elapsed = new Date().getTime() - start;\n\n");

        // --- DEBUG OUTPUT ---
        sb.append("// DEBUG OUTPUT (remove or comment out in production)\n");
        sb.append("if (results == null) {\n");
        sb.append("    logger.error(\"Lookup failed for group: \" + groupName);\n");
        sb.append("} else if (results.isEmpty()) {\n");
        sb.append("    logger.info(\"No matching entries (elapsed=\" + elapsed + \" ms) in group=\" + groupName);\n");
        sb.append("} else {\n");
        sb.append("    logger.info(\"Sample results (showing up to 2 entries):\");\n");
        sb.append("    var iter = results.keySet().iterator();\n");
        sb.append("    var count = 0;\n\n");
        sb.append("    while (iter.hasNext() && count < 2) {\n");
        sb.append("        var key = iter.next();\n");
        sb.append("        var value = results.get(key);\n");
        sb.append("        logger.info(\"  key=\" + key + \", value=\" + value);\n");
        sb.append("        count++;\n");
        sb.append("    }\n\n");
        sb.append("    logger.info(\n");
        sb.append("        \"Found \" + results.size() + \" matching entries (elapsed=\" + elapsed + \" ms) in group=\" + groupName\n");
        sb.append("    );\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static String escapeJs(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
            case '\\':
                sb.append("\\\\");
                break;
            case '"':
                sb.append("\\\"");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            default:
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
