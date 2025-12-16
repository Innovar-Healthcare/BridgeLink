/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.service.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mirth.connect.plugins.dynamiclookup.server.util.JsonFieldUtils;
import com.mirth.connect.plugins.dynamiclookup.server.util.LookupTableNaming;
import com.mirth.connect.plugins.dynamiclookup.shared.constant.LookupConstants;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonCondition;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonOperator;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonValueType;
import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

public class PostgresJsonFieldDialect implements JsonFieldDialect {
    @Override
    public List<JsonFieldIndexDefinition> buildIndexDefinitions(LookupGroup group) {
        if (group == null) {
            return Collections.emptyList();
        }

        LookupGroupExtra extra = group.getExtra();
        if (extra == null) {
            return Collections.emptyList();
        }

        Set<String> fieldPaths = JsonFieldUtils.parseIndexedFieldPaths(extra.getIndexedJsonFields());
        if (fieldPaths.isEmpty()) {
            return Collections.emptyList();
        }

        String tableName = LookupTableNaming.valueTableName(group);

        List<JsonFieldIndexDefinition> definitions = new ArrayList<>();
        for (String fieldPath : fieldPaths) {
            String expression = buildIndexExpression(fieldPath);
            String indexName = JsonIndexNaming.buildIndexName(tableName, fieldPath);

            JsonFieldIndexDefinition def = new JsonFieldIndexDefinition();
            def.setFieldPath(fieldPath);
            def.setIndexName(indexName);
            def.setExpression(expression);
            def.setTableName(tableName);

            definitions.add(def);
        }

        return definitions;
    }

    @Override
    public List<JsonFieldCriterion> buildCriteria(LookupGroup group, List<JsonCondition> conditions) {
        if (group == null || conditions == null || conditions.isEmpty()) {
            return Collections.emptyList();
        }

        LookupGroupExtra extra = group.getExtra();
        if (extra == null) {
            throw new IllegalStateException("Group extra missing for group: " + group.getId());
        }

        // Get index mode
        String mode = LookupConstants.normalizeJsonIndexMode(extra.getJsonIndexMode());

        if (LookupConstants.isGinMode(mode)) {
            return buildGinCriterion(group, conditions);
        }

        return buildFieldCriterion(group, conditions);

    }

    private List<JsonFieldCriterion> buildFieldCriterion(LookupGroup group, List<JsonCondition> conditions) {
        List<JsonFieldCriterion> criteria = new ArrayList<>();

        for (JsonCondition cond : conditions) {

            if (cond == null) {
                continue;
            }

            validateCondition(cond);

            String fieldPath = cond.getField().trim();
            JsonValueType valueType = cond.getValueType();

            // JSONB extraction for jsonb_typeof(...) checks
            String typeCheckSql = buildTypeCheckSql(fieldPath, valueType);

            // Always TEXT extraction (safe for casting/comparison)
            String textExpr = buildCriterionExpression(fieldPath, valueType);

            JsonFieldCriterion criterion = new JsonFieldCriterion();

            if (valueType == JsonValueType.NUMBER) {
                criterion.setTypeCheckSql(typeCheckSql);
                criterion.setExpression("(" + textExpr + ")::numeric");
            } else if (valueType == JsonValueType.BOOLEAN) {
                criterion.setTypeCheckSql(typeCheckSql);
                criterion.setExpression("(" + textExpr + ")::boolean");
            } else {
                // STRING (or null -> default STRING)
                criterion.setExpression(textExpr);
            }

            criterion.setOperatorSql(buildOperatorSql(cond.getOp()));
            criterion.setValue(cond.getValue());
            criterion.setValueSql(buildValueSql(valueType));
            criteria.add(criterion);
        }

        return criteria;
    }

    private List<JsonFieldCriterion> buildGinCriterion(LookupGroup group, List<JsonCondition> conditions) {
        List<JsonFieldCriterion> criteria = new ArrayList<>();

        Map<String, Object> root = new LinkedHashMap<>();

        for (JsonCondition cond : conditions) {
            if (cond == null) {
                continue;
            }

            if (cond.getOp() != JsonOperator.EQUAL) {
                throw new UnsupportedOperationException("PostgreSQL GIN search supports only EQUAL for now. op=" + cond.getOp());
            }

            String rawFieldPath = cond.getField();
            if (rawFieldPath == null || rawFieldPath.trim().isEmpty()) {
                continue;
            }

            String fieldPath = JsonFieldUtils.normalizeFieldPath(rawFieldPath);
            if (fieldPath.isEmpty()) {
                continue;
            }

            mergePath(root, fieldPath.split("\\."), cond.getValue());
        }

        if (root.isEmpty()) {
            return Collections.emptyList();
        }

        final String mergedJson;
        try {
            mergedJson = JsonUtils.toJson(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build merged GIN filter JSON", e);
        }

        JsonFieldCriterion c = new JsonFieldCriterion();
        c.setExpression("VALUE_DATA");
        c.setOperatorSql("@>");
        c.setValue(mergedJson);

        criteria.add(c);

        return criteria;
    }

    @SuppressWarnings("unchecked")
    private static void mergePath(Map<String, Object> root, String[] parts, Object value) {
        Map<String, Object> current = root;

        for (int i = 0; i < parts.length; i++) {
            String key = parts[i] == null ? "" : parts[i].trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Invalid JSON field path part");
            }

            if (i == parts.length - 1) {
                current.put(key, value);
                return;
            }

            Object existing = current.get(key);
            if (existing == null) {
                Map<String, Object> child = new LinkedHashMap<>();
                current.put(key, child);
                current = child;
            } else if (existing instanceof Map) {
                current = (Map<String, Object>) existing;
            } else {
                throw new IllegalArgumentException("Conflicting JSON path: cannot descend into non-object at '" + key + "'");
            }
        }
    }

    /**
     * PostgreSQL: - top-level: VALUE_DATA->>'email' - nested: VALUE_DATA #>> '{address,city,zip}'
     */
    private String buildIndexExpression(String fieldPath) {
        if (!fieldPath.contains(".")) {
            return "VALUE_DATA->>'" + fieldPath + "'";
        }

        String path = "{" + fieldPath.replace(".", ",") + "}";
        return "VALUE_DATA #>> '" + path + "'";
    }

    //@formatter:off
    /**
     * Build JSON type check SQL for Postgres JSONB fields.
     *
     * Rules:
     * - NUMBER  : accept ONLY real JSON numbers (e.g. 18), reject numeric strings ("18")
     * - BOOLEAN : accept ONLY real JSON booleans (true/false)
     * - STRING  : no type check (always treated as text)
     *
     * Notes:
     * - Uses JSONB extraction (-> / #>) so jsonb_typeof() works correctly
     * - Casting is done separately on text expression (->> / #>>) to avoid exceptions
     */
  //@formatter:on
    private String buildTypeCheckSql(String fieldPath, JsonValueType valueType) {
        if (valueType == null || valueType == JsonValueType.STRING) {
            return null;
        }

        boolean nested = fieldPath.contains(".");
        String jsonbExpr;
        if (!nested) {
            jsonbExpr = "VALUE_DATA->'" + fieldPath + "'";
        } else {
            String path = "{" + fieldPath.replace(".", ",") + "}";
            jsonbExpr = "VALUE_DATA #> '" + path + "'";
        }

        if (valueType == JsonValueType.NUMBER) {
            return "jsonb_typeof(" + jsonbExpr + ") = 'number'";
        }
        if (valueType == JsonValueType.BOOLEAN) {
            return "jsonb_typeof(" + jsonbExpr + ") = 'boolean'";
        }

        return null;
    }

    //@formatter:off
    /**
     * Build PostgreSQL JSON text extraction expression for filtering/comparison.
     *
     * Rules:
     * - Always extract JSON value as TEXT using ->> / #>>
     * - Used for STRING comparison and for casting NUMBER / BOOLEAN
     * - JSON type validation (NUMBER / BOOLEAN) is handled separately
     *
     * Examples:
     * - top-level field : VALUE_DATA->>'field'
     * - nested field    : VALUE_DATA #>> '{a,b}'
     */
    //@formatter:on
    private String buildCriterionExpression(String fieldPath, JsonValueType type) {
        boolean nested = fieldPath.contains(".");
        if (!nested) {
            return "VALUE_DATA->>'" + fieldPath + "'";
        }

        String path = "{" + fieldPath.replace(".", ",") + "}";
        return "VALUE_DATA #>> '" + path + "'";
    }

    //@formatter:off
    /**
     * Build RHS SQL for value binding based on value type.
     *
     * Note:
     * - value is not interpolated here (still bound via MyBatis)
     * - type controls only SQL casting behavior
     */
    //@formatter:on
    private String buildValueSql(JsonValueType type) {
        JsonValueType valueType = type != null ? type : JsonValueType.STRING;

        switch (valueType) {
        case NUMBER:
            return "CAST(#{c.value} AS numeric)";
        case BOOLEAN:
            return "CAST(#{c.value} AS boolean)";
        case STRING:
        default:
            return "#{c.value}";
        }
    }

    /**
     * PostgreSQL: Maps a high-level JsonOperator to its SQL operator
     */
    private String buildOperatorSql(JsonOperator op) {
        if (op == null) {
            // Default defensive behavior
            return "=";
        }

        switch (op) {
        case EQUAL:
            return "=";
        case NOT_EQUAL:
            return "!=";
        case GREATER_THAN:
            return ">";
        case GREATER_OR_EQUAL:
            return ">=";
        case LESS_THAN:
            return "<";
        case LESS_OR_EQUAL:
            return "<=";
        default:
            throw new IllegalArgumentException("Unsupported JsonOperator for PostgreSQL: " + op);
        }
    }

    private void validateCondition(JsonCondition c) {
        // Service already validated: field/op/valueType not null

        String rawField = c.getField().trim();

        // --- Validate raw field path (fail fast, no silent mutation) ---
        if (!rawField.matches("[A-Za-z0-9_.]+")) {
            throw new IllegalArgumentException("Invalid JSON field path: '" + rawField + "'. Only letters, digits, underscore (_), and dot (.) are allowed.");
        }
        if (rawField.startsWith(".") || rawField.endsWith(".")) {
            throw new IllegalArgumentException("Invalid JSON field path: '" + rawField + "'. Path cannot start or end with '.'.");
        }
        if (rawField.contains("..")) {
            throw new IllegalArgumentException("Invalid JSON field path: '" + rawField + "'. Consecutive dots are not allowed.");
        }

        // --- Validate value (dialect responsibility) ---
        String valueText = c.getValue() != null ? c.getValue().toString().trim() : "";
        if (valueText.isEmpty()) {
            throw new IllegalArgumentException("Value cannot be empty for field: " + rawField);
        }

        JsonValueType vt = c.getValueType();

        if (vt == JsonValueType.NUMBER) {
            try {
                new BigDecimal(valueText);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid NUMBER value for field '" + rawField + "': " + valueText);
            }
        } else if (vt == JsonValueType.BOOLEAN) {
            String v = valueText.toLowerCase();
            if (!"true".equals(v) && !"false".equals(v)) {
                throw new IllegalArgumentException("Invalid BOOLEAN value for field '" + rawField + "': " + valueText);
            }
        }
    }

}
