/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.plugins.dynamiclookup.shared.model.AdvancedJsonFilterState;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonCondition;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonOperator;
import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

public final class AdvancedJsonFilterBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AdvancedJsonFilterBuilder() {
    }

    public static String toJson(AdvancedJsonFilterState filter) throws Exception {
        return JsonUtils.toJson(filter);
    }

    public static AdvancedJsonFilterState fromJson(String json) throws Exception {
        return JsonUtils.fromJson(json, AdvancedJsonFilterState.class);
    }

    public static AdvancedJsonFilterState fromLookupHelperInputs(String keyPattern, String filterJson) {

        Map<String, Object> map = null;

        if (filterJson != null && !filterJson.trim().isEmpty() && !filterJson.trim().equals("{}")) {
            try {
                map = MAPPER.readValue(filterJson, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid simple filter JSON: " + filterJson, e);
            }
        }

        return fromSimpleMap(keyPattern, map);
    }

    /**
     * Build AdvancedJsonFilterState from: - keyPattern (optional) - simple map field -> string with optional operator
     * prefix (>, >=, !=, etc)
     */
    public static AdvancedJsonFilterState fromSimpleMap(String keyPattern, Map<String, ?> filters) {
        AdvancedJsonFilterState state = new AdvancedJsonFilterState();

        // key pattern (simple mode always uses SQL LIKE)
        if (keyPattern != null && !keyPattern.trim().isEmpty()) {
            state.setKeyPattern(keyPattern.trim());
        }

        if (filters == null || filters.isEmpty()) {
            return state;
        }

        List<JsonCondition> conditions = new ArrayList<>();

        for (Map.Entry<String, ?> entry : filters.entrySet()) {
            String field = entry.getKey();
            Object rawValue = entry.getValue();

            if (field == null || field.trim().isEmpty())
                continue;
            if (rawValue == null)
                continue;

            String value = rawValue.toString().trim();
            if (value.isEmpty())
                continue;

            // --- Parse operator prefix ---
            JsonOperator op = JsonOperator.EQUAL;

            if (value.startsWith(">=")) {
                op = JsonOperator.GREATER_OR_EQUAL;
                value = value.substring(2).trim();
            } else if (value.startsWith("<=")) {
                op = JsonOperator.LESS_OR_EQUAL;
                value = value.substring(2).trim();
            } else if (value.startsWith("!=")) {
                op = JsonOperator.NOT_EQUAL;
                value = value.substring(2).trim();
            } else if (value.startsWith(">")) {
                op = JsonOperator.GREATER_THAN;
                value = value.substring(1).trim();
            } else if (value.startsWith("<")) {
                op = JsonOperator.LESS_THAN;
                value = value.substring(1).trim();
            } else if (value.startsWith("!~")) {
                op = JsonOperator.NOT_CONTAINS;
                value = value.substring(2).trim();
            } else if (value.startsWith("~")) {
                op = JsonOperator.CONTAINS;
                value = value.substring(1).trim();
            }

            JsonCondition c = new JsonCondition();
            c.setField(field.trim());
            c.setOp(op);
            c.setValue(value);

            conditions.add(c);
        }

        state.setConditions(conditions);
        return state;
    }

    public static String toSimpleJson(AdvancedJsonFilterState state) {
        List<JsonCondition> conditions = state.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');

        boolean first = true;
        for (JsonCondition c : conditions) {
            if (c == null) {
                continue;
            }

            String field = trim(c.getField());
            String rawValue = trim(String.valueOf(c.getValue()));

            if (field.isEmpty() || rawValue.isEmpty()) {
                continue;
            }

            // operator → prefix mapping
            JsonOperator op = c.getOp() != null ? c.getOp() : JsonOperator.EQUAL;
            String prefix = mapOperatorToPrefix(op);

            String value = prefix + rawValue;

            if (!first) {
                sb.append(',');
            }

            //@formatter:off
            sb.append('"').append(escapeJson(field)).append('"')
              .append(':')
              .append('"').append(escapeJson(value)).append('"');
            //@formatter:on

            first = false;
        }

        if (first) {
            return "{}";
        }

        sb.append('}');

        return sb.toString();
    }

    private static String mapOperatorToPrefix(JsonOperator op) {
        switch (op) {
        case GREATER_OR_EQUAL:
            return ">=";
        case LESS_OR_EQUAL:
            return "<=";
        case GREATER_THAN:
            return ">";
        case LESS_THAN:
            return "<";
        case NOT_EQUAL:
            return "!=";
        case CONTAINS:
            return "~";
        case NOT_CONTAINS:
            return "!~";
        case EQUAL:
        default:
            return "";
        }
    }

    private static String trim(String s) {
        return s != null ? s.trim() : "";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
