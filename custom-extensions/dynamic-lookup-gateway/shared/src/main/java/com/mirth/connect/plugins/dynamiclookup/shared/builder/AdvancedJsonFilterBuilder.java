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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.plugins.dynamiclookup.shared.model.AdvancedJsonFilterState;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonCondition;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonOperator;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonValueType;
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

    public static String toConditionsJsonArray(AdvancedJsonFilterState state) {
        if (state == null || state.getConditions() == null || state.getConditions().isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        boolean first = true;

        for (JsonCondition c : state.getConditions()) {
            if (c == null) {
                continue;
            }

            String field = trim(c.getField());
            String value = c.getValue() != null ? c.getValue().toString().trim() : "";

            if (field.isEmpty() || value.isEmpty()) {
                continue;
            }

            if (!first) {
                sb.append(",\n");
            }

            sb.append("  {\n");
            sb.append("    \"field\": \"").append(escapeJson(field)).append("\",\n");
            sb.append("    \"op\": \"").append(mapOperatorToSymbol(c.getOp())).append("\",\n");
            sb.append("    \"valueType\": \"").append(c.getValueType() != null ? c.getValueType().name() : JsonValueType.STRING.name()).append("\",\n");
            sb.append("    \"value\": \"").append(escapeJson(value)).append("\"\n");
            sb.append("  }");

            first = false;
        }

        sb.append("\n]");
        return sb.toString();
    }

    //@formatter:off
    /**
     * Contract:
     * filterJson is a JSON array of objects:
     * [
     *   {"field":"email","op":"=","valueType":"STRING","value":"a@b.com"},
     *   {"field":"age","op":">=","valueType":"NUMBER","value":"18"}
     * ]
     *
     * Notes:
     * - op is a symbol: =, !=, >=, <=, >, <
     * - valueType is enum name: STRING / NUMBER / BOOLEAN
     * - value is kept as string
     */
    //@formatter:on
    public static AdvancedJsonFilterState fromLookupHelperInputs(String keyPattern, String filterJson) {

        AdvancedJsonFilterState state = new AdvancedJsonFilterState();

        if (keyPattern != null && !keyPattern.trim().isEmpty()) {
            state.setKeyPattern(keyPattern.trim());
        }

        if (filterJson == null) {
            return state;
        }

        String json = filterJson.trim();
        if (json.isEmpty() || "[]".equals(json)) {
            return state;
        }

        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isArray()) {
                throw new IllegalArgumentException("Invalid filter JSON (expected array): " + filterJson);
            }

            List<JsonCondition> conds = new ArrayList<>();

            for (JsonNode n : root) {
                if (n == null || !n.isObject()) {
                    continue;
                }

                String field = trim(text(n, "field"));
                String opSymbol = trim(text(n, "op"));
                String valueTypeName = trim(text(n, "valueType"));

                JsonNode valueNode = n.get("value");
                String value = valueNode == null || valueNode.isNull() ? "" : valueNode.asText("").trim();

                if (field.isEmpty() || value.isEmpty()) {
                    continue;
                }

                JsonCondition c = new JsonCondition();
                c.setField(field);
                c.setOp(mapSymbolToOperator(opSymbol)); // "=" -> EQUAL
                c.setValueType(valueTypeName.isEmpty() ? JsonValueType.STRING : JsonValueType.valueOf(valueTypeName));
                c.setValue(value);

                conds.add(c);
            }

            state.setConditions(conds);
            return state;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid filter JSON: " + filterJson, e);
        }
    }

    private static String text(JsonNode obj, String field) {
        JsonNode n = obj.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private static String trim(String s) {
        return s != null ? s.trim() : "";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String mapOperatorToSymbol(JsonOperator op) {
        if (op == null) {
            throw new IllegalArgumentException("Operator cannot be null");
        }

        switch (op) {
        case EQUAL:
            return "=";
        case NOT_EQUAL:
            return "!=";
        case GREATER_OR_EQUAL:
            return ">=";
        case LESS_OR_EQUAL:
            return "<=";
        case GREATER_THAN:
            return ">";
        case LESS_THAN:
            return "<";
//        case CONTAINS:
//            return "~";
//        case NOT_CONTAINS:
//            return "!~";
        default:
            throw new IllegalArgumentException("Unsupported operator: " + op);
        }
    }

    private static JsonOperator mapSymbolToOperator(String op) {
        String s = trim(op);
        if ("=".equals(s)) {
            return JsonOperator.EQUAL;
        }

        if ("!=".equals(s)) {
            return JsonOperator.NOT_EQUAL;
        }

        if (">=".equals(s)) {
            return JsonOperator.GREATER_OR_EQUAL;
        }

        if ("<=".equals(s)) {
            return JsonOperator.LESS_OR_EQUAL;
        }

        if (">".equals(s)) {
            return JsonOperator.GREATER_THAN;
        }

        if ("<".equals(s)) {
            return JsonOperator.LESS_THAN;
        }

//        if ("~".equals(s)) {
//            return JsonOperator.CONTAINS;
//        }
//
//        if ("!~".equals(s)) {
//            return JsonOperator.NOT_CONTAINS;
//        }

        throw new IllegalArgumentException("Unsupported operator: " + op);
    }

}
