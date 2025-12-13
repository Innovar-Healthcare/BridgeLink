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
            String expression = buildExpression(fieldPath);
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
    public List<JsonFieldCriterion> buildCriteria(LookupGroup group, Map<String, String> filters) {
        throw new IllegalArgumentException("Unsupported for PostgreSQL");
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

            String rawFieldPath = cond.getField();
            if (rawFieldPath == null || rawFieldPath.trim().isEmpty()) {
                continue;
            }

            // Normalize field path: "address.city" → "address.city"
            String fieldPath = JsonFieldUtils.normalizeFieldPath(rawFieldPath);
            if (fieldPath.isEmpty()) {
                continue;
            }

            // Dialect builds SQL expression, e.g.:
            String expression = buildExpression(fieldPath);

            // Map JsonOperator → SQL operator string
            String operatorSql = buildSqlOperator(cond.getOp());

            JsonFieldCriterion criterion = new JsonFieldCriterion();
            criterion.setExpression(expression);
            criterion.setOperatorSql(operatorSql);
            criterion.setValue(cond.getValue());

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
    private String buildExpression(String fieldPath) {
        if (!fieldPath.contains(".")) {
            return "VALUE_DATA->>'" + fieldPath + "'";
        }

        String path = "{" + fieldPath.replace(".", ",") + "}";
        return "VALUE_DATA #>> '" + path + "'";
    }

    /**
     * PostgreSQL: Maps a high-level JsonOperator to its SQL operator
     */
    private String buildSqlOperator(JsonOperator op) {
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
}
