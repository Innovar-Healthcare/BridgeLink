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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.mirth.connect.plugins.dynamiclookup.server.util.JsonFieldUtils;
import com.mirth.connect.plugins.dynamiclookup.server.util.LookupTableNaming;
import com.mirth.connect.plugins.dynamiclookup.shared.constant.LookupConstants;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;

public class SqlServerJsonFieldDialect implements JsonFieldDialect {

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
            String computedColumnName = buildComputedColumnName(fieldPath);
            String indexName = JsonIndexNaming.buildIndexName(tableName, fieldPath);

            JsonFieldIndexDefinition def = new JsonFieldIndexDefinition();
            def.setFieldPath(fieldPath);
            def.setIndexName(indexName);
            def.setExpression(expression);
            def.setComputedColumnName(computedColumnName);
            def.setTableName(tableName);

            definitions.add(def);
        }

        return definitions;
    }

    @Override
    public List<JsonFieldCriterion> buildCriteria(LookupGroup group, Map<String, String> filters) {
        if (group == null || filters == null || filters.isEmpty()) {
            return Collections.emptyList();
        }

        LookupGroupExtra extra = group.getExtra();
        String normalizedMode = LookupConstants.normalizeJsonIndexMode(extra != null ? extra.getJsonIndexMode() : null);

        // Reuse the same definitions used for physical index creation.
        List<JsonFieldIndexDefinition> defs = LookupConstants.isFieldMode(normalizedMode) ? buildIndexDefinitions(group) : Collections.emptyList();
        Map<String, JsonFieldIndexDefinition> indexByField = defs.stream().collect(Collectors.toMap(JsonFieldIndexDefinition::getFieldPath, d -> d, (a, b) -> a));

        List<JsonFieldCriterion> criteria = new ArrayList<>();

        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String rawFieldPath = entry.getKey();
            String value = entry.getValue();

            if (rawFieldPath == null || rawFieldPath.trim().isEmpty()) {
                continue;
            }

            String fieldPath = JsonFieldUtils.normalizeFieldPath(rawFieldPath);
            if (fieldPath.isEmpty()) {
                continue;
            }

            String expression;

            JsonFieldIndexDefinition def = indexByField.get(fieldPath);
            boolean canUseIndex = canUseFieldIndex(normalizedMode, def);

            if (canUseIndex) {
                // Use the persisted computed column so SQL Server can leverage the index.
                expression = def.getComputedColumnName();
            } else {
                // No index (or mode != FIELD) -> fall back to JSON_VALUE expression.
                expression = buildExpression(fieldPath);
            }

            JsonFieldCriterion criterion = new JsonFieldCriterion();
            criterion.setExpression(expression);
            criterion.setValue(value);

            criteria.add(criterion);
        }

        return criteria;
    }

    private String buildExpression(String fieldPath) {
        String jsonPath = "$." + fieldPath; // e.g. address.city
        return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "')";
    }

    private boolean canUseFieldIndex(String normalizedMode, JsonFieldIndexDefinition def) {
        if (!LookupConstants.isFieldMode(normalizedMode)) {
            return false;
        }

        if (def == null) {
            return false;
        }

        String col = def.getComputedColumnName();
        return col != null && !col.isEmpty();
    }

    /**
     * Builds a safe computed column name for SQL Server from a JSON field path.
     *
     * Rules: - Split field path by "." - Uppercase each segment - Replace non-alphanumeric characters with "_" - Join with
     * "_" - Prefix with "JSON_" to avoid conflicts with real columns
     *
     * Examples: "email" -> JSON_EMAIL "address.city" -> JSON_ADDRESS_CITY "user-name" -> JSON_USER_NAME
     */
    private String buildComputedColumnName(String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        StringBuilder sb = new StringBuilder("JSON_");

        for (int i = 0; i < parts.length; i++) {
            String segment = parts[i].toUpperCase().replaceAll("[^A-Z0-9]", "_");

            sb.append(segment);
            if (i < parts.length - 1) {
                sb.append("_");
            }
        }

        return sb.toString();
    }
}
