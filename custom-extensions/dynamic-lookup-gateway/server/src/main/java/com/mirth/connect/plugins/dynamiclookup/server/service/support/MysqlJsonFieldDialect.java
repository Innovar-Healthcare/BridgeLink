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
import java.util.Set;

import com.mirth.connect.plugins.dynamiclookup.server.util.JsonFieldUtils;
import com.mirth.connect.plugins.dynamiclookup.server.util.LookupTableNaming;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonCondition;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonOperator;

public class MysqlJsonFieldDialect implements JsonFieldDialect {
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

    public List<JsonFieldCriterion> buildCriteria(LookupGroup group, List<JsonCondition> conditions) {
        if (group == null || conditions == null || conditions.isEmpty()) {
            return Collections.emptyList();
        }

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

    private String buildExpression(String fieldPath) {
        String jsonPath = "$." + fieldPath; // "address.city"
        return "CAST(JSON_UNQUOTE(JSON_EXTRACT(VALUE_DATA, '" + jsonPath + "')) AS CHAR(255))";
    }

    /**
     * MySQL: Maps a high-level JsonOperator to its SQL operator
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
            throw new IllegalArgumentException("Unsupported JsonOperator for MySQL: " + op);
        }
    }

}
