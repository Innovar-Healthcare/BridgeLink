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
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonValueType;

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

        return buildFieldCriterion(group, conditions);
    }

    public List<JsonFieldCriterion> buildFieldCriterion(LookupGroup group, List<JsonCondition> conditions) {
        List<JsonFieldCriterion> criteria = new ArrayList<>();

        for (JsonCondition cond : conditions) {

            if (cond == null) {
                continue;
            }

            String fieldPath = cond.getField();
            JsonValueType valueType = cond.getValueType();

            // Guard/type check (prevents conversion failures; invalid rows are skipped)
            String typeCheckSql = buildTypeCheckSql(fieldPath, valueType);

            // Dialect builds SQL expression, e.g.:
            String expression = buildCriterionExpression(fieldPath, valueType);

            // Map JsonOperator → SQL operator string
            String operatorSql = buildSqlOperator(cond.getOp());

            JsonFieldCriterion criterion = new JsonFieldCriterion();
            criterion.setTypeCheckSql(typeCheckSql);
            criterion.setExpression(expression);
            criterion.setOperatorSql(operatorSql);
            criterion.setValue(cond.getValue());
            criterion.setValueSql(buildValueSql(valueType));
            criteria.add(criterion);
        }

        return criteria;
    }

    private String buildIndexExpression(String fieldPath) {
        String jsonPath = normalizeJsonPath(fieldPath);
        return "CAST(JSON_UNQUOTE(JSON_EXTRACT(VALUE_DATA, '" + jsonPath + "')) AS CHAR(255))";
    }

    private String buildTypeCheckSql(String fieldPath, JsonValueType valueType) {
        String jsonPath = normalizeJsonPath(fieldPath);
        String extract = "JSON_EXTRACT(VALUE_DATA, '" + jsonPath + "')";
        String type = "JSON_TYPE(" + extract + ")";

        if (valueType == null || valueType == JsonValueType.STRING) {
            return type + " = 'STRING'";
        } else if (valueType == JsonValueType.NUMBER) {
            // MySQL may return INTEGER / DOUBLE; DECIMAL can appear depending on insert/cast
            return type + " IN ('INTEGER','DOUBLE','DECIMAL')";
        } else if (valueType == JsonValueType.BOOLEAN) {
            return type + " = 'BOOLEAN'";
        } else {
            // default safe behavior
            return type + " = 'STRING'";
        }
    }

    private String buildCriterionExpression(String fieldPath, JsonValueType valueType) {
        String jsonPath = normalizeJsonPath(fieldPath);
        String extract = "JSON_EXTRACT(VALUE_DATA, '" + jsonPath + "')";

        if (valueType == null || valueType == JsonValueType.STRING) {
            // Compare as text
            return "CAST(JSON_UNQUOTE(" + extract + ") AS CHAR(255))";
        } else if (valueType == JsonValueType.NUMBER) {
            // Compare as numeric
            return "CAST(" + extract + " AS DECIMAL(65,30))";
        } else if (valueType == JsonValueType.BOOLEAN) {
            // JSON boolean (true/false)
            return extract;
        } else {
            return "CAST(JSON_UNQUOTE(" + extract + ") AS CHAR(255))";
        }
    }

    private String buildValueSql(JsonValueType valueType) {
        // IMPORTANT: This is intended to be used inside MyBatis foreach item "c"
        // e.g. ... ${c.expression} ${c.operatorSql} ${c.valueSql}
        if (valueType == null || valueType == JsonValueType.STRING) {
            return "#{c.value}";
        } else if (valueType == JsonValueType.NUMBER) {
            return "CAST(#{c.value} AS DECIMAL(65,30))";
        } else if (valueType == JsonValueType.BOOLEAN) {
            // If you already validate cond.getValue() is "true"/"false" (case-insensitive),
            // this reliably becomes JSON true/false.
            return "CAST(LOWER(#{c.value}) AS JSON)";
        } else {
            return "#{c.value}";
        }
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

    private String normalizeJsonPath(String fieldPath) {
        if (fieldPath == null) {
            return "$";
        }
        String trimmed = fieldPath.trim();
        if (trimmed.isEmpty()) {
            return "$";
        }

        String[] parts = trimmed.split("\\.");
        StringBuilder sb = new StringBuilder("$");
        for (String p : parts) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            sb.append(".\"").append(p.replace("\"", "\\\"")).append("\"");
        }
        return sb.toString();
    }
}
