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
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonCondition;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonOperator;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonValueType;

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
            String expression = buildIndexExpression(fieldPath);
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
    public List<JsonFieldCriterion> buildCriteria(LookupGroup group, List<JsonCondition> conditions) {
        if (group == null || conditions == null || conditions.isEmpty()) {
            return Collections.emptyList();
        }

        LookupGroupExtra extra = group.getExtra();
        if (extra == null) {
            throw new IllegalStateException("Group extra missing for group: " + group.getId());
        }

        return buildFieldCriterion(group, conditions);
    }

    public List<JsonFieldCriterion> buildFieldCriterion(LookupGroup group, List<JsonCondition> conditions) {
        LookupGroupExtra extra = group.getExtra();
        String normalizedMode = LookupConstants.normalizeJsonIndexMode(extra.getJsonIndexMode());

        // Reuse the same definitions used for physical index creation.
        List<JsonFieldIndexDefinition> defs = LookupConstants.isFieldMode(normalizedMode) ? buildIndexDefinitions(group) : Collections.emptyList();
        Map<String, JsonFieldIndexDefinition> indexByField = defs.stream().collect(Collectors.toMap(JsonFieldIndexDefinition::getFieldPath, d -> d, (a, b) -> a));

        List<JsonFieldCriterion> criteria = new ArrayList<>();

        for (JsonCondition cond : conditions) {

            if (cond == null) {
                continue;
            }

            String fieldPath = cond.getField();
            JsonValueType valueType = cond.getValueType();
            // Dialect builds SQL expression, e.g.:
            JsonFieldIndexDefinition def = indexByField.get(fieldPath);
            String computedColumnName = canUseFieldIndex(normalizedMode, def) ? def.getComputedColumnName() : null;

            // Guard/type check (prevents conversion failures; invalid rows are skipped)
            String typeCheckSql = buildTypeCheckSql(fieldPath, valueType, computedColumnName);

            // expression (typed)
            String expression = buildCriterionExpression(fieldPath, valueType, computedColumnName);

            // Map JsonOperator → SQL operator string
            String operatorSql = buildSqlOperator(cond.getOp());

            JsonFieldCriterion criterion = new JsonFieldCriterion();
            criterion.setTypeCheckSql(typeCheckSql);
            criterion.setExpression(expression);
            criterion.setOperatorSql(operatorSql);
            criterion.setValue(cond.getValue());
            criterion.setValueSql(buildValueSql(valueType)); // RHS (typed)
            criteria.add(criterion);
        }

        return criteria;
    }

    private String buildIndexExpression(String fieldPath) {
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

    private String buildTypeCheckSql(String fieldPath, JsonValueType valueType, String computedColumnName) {
        if (valueType == null || valueType == JsonValueType.STRING) {
            return null;
        }

        String baseExpr = resolveBaseExpr(fieldPath, computedColumnName);

        if (valueType == JsonValueType.NUMBER) {
            return "TRY_CONVERT(DECIMAL(38,10), " + baseExpr + ") IS NOT NULL" + " AND TRY_CONVERT(DECIMAL(38,10), #{c.value}) IS NOT NULL";
        }

        if (valueType == JsonValueType.BOOLEAN) {
            // strict true/false to match your validator
            return baseExpr + " IN ('true','false') AND #{c.value} IN ('true','false')";
        }

        return null;
    }

    private String buildCriterionExpression(String fieldPath, JsonValueType type, String computedColumnName) {
        String baseExpr = resolveBaseExpr(fieldPath, computedColumnName);

        if (type == JsonValueType.NUMBER) {
            return "TRY_CONVERT(DECIMAL(38,10), " + baseExpr + ")";
        }

        if (type == JsonValueType.BOOLEAN) {
            return "CASE WHEN " + baseExpr + " = 'true' THEN CAST(1 AS BIT) " + "WHEN " + baseExpr + " = 'false' THEN CAST(0 AS BIT) " + "ELSE NULL END";
        }

        return baseExpr; // STRING
    }

    private String resolveBaseExpr(String fieldPath, String computedColumnName) {
        if (computedColumnName != null && !computedColumnName.isEmpty()) {
            return computedColumnName;
        }

        String jsonPath = "$." + fieldPath;
        return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "')";
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
            return "TRY_CONVERT(DECIMAL(38,10), #{c.value})";
        case BOOLEAN:
            return "CASE WHEN #{c.value} = 'true' THEN CAST(1 AS BIT) " + "WHEN #{c.value} = 'false' THEN CAST(0 AS BIT) " + "ELSE NULL END";
        case STRING:
        default:
            return "#{c.value}";
        }
    }

    /**
     * SQL Server: Maps a high-level JsonOperator to its SQL operator
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
            return "<>";
        case GREATER_THAN:
            return ">";
        case GREATER_OR_EQUAL:
            return ">=";
        case LESS_THAN:
            return "<";
        case LESS_OR_EQUAL:
            return "<=";
        default:
            throw new IllegalArgumentException("Unsupported JsonOperator for SQL Server: " + op);
        }
    }

}
