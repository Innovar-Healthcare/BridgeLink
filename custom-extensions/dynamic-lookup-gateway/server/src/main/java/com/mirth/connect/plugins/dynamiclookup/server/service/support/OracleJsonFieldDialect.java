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

public class OracleJsonFieldDialect implements JsonFieldDialect {
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

    /**
     * Oracle native JSON (FIELD mode):
     *
     * - "email" -> JSON_VALUE(VALUE_DATA, '$.email' RETURN VARCHAR2(n)) - "address.city" -> JSON_VALUE(VALUE_DATA,
     * '$.address.city' RETURN VARCHAR2(n))
     */
    private String buildIndexExpression(String fieldPath) {
        String jsonPath = normalizeJsonPath(fieldPath);
        return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "' RETURNING VARCHAR2(4000))";
    }

    private String buildTypeCheckSql(String fieldPath, JsonValueType valueType) {
        String jsonPath = normalizeJsonPath(fieldPath);

        if (valueType == null || valueType == JsonValueType.STRING) {
            return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "' RETURNING VARCHAR2(4000) NULL ON ERROR NULL ON EMPTY) IS NOT NULL";
        } else if (valueType == JsonValueType.NUMBER) {
            return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "' RETURNING NUMBER NULL ON ERROR NULL ON EMPTY) IS NOT NULL";
        } else if (valueType == JsonValueType.BOOLEAN) {
            // accept only true/false strings
            String v = "JSON_VALUE(VALUE_DATA, '" + jsonPath + "' RETURNING VARCHAR2(5) NULL ON ERROR NULL ON EMPTY)";
            return v + " IN ('true','false')";
        } else {
            return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "' RETURNING VARCHAR2(4000) NULL ON ERROR NULL ON EMPTY) IS NOT NULL";
        }
    }

    private String buildCriterionExpression(String fieldPath, JsonValueType valueType) {
        String jsonPath = normalizeJsonPath(fieldPath);

        if (valueType == null || valueType == JsonValueType.STRING) {
            // text compare
            return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "' RETURNING VARCHAR2(4000) NULL ON ERROR NULL ON EMPTY)";
        } else if (valueType == JsonValueType.NUMBER) {
            // numeric compare; invalid conversions become NULL => predicate false
            return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "' RETURNING NUMBER NULL ON ERROR NULL ON EMPTY)";
        } else if (valueType == JsonValueType.BOOLEAN) {
            // Oracle SQL has no boolean type; JSON booleans usually come out as 'true'/'false'
            return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "' RETURNING VARCHAR2(5) NULL ON ERROR NULL ON EMPTY)";
        } else {
            return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "' RETURNING VARCHAR2(4000) NULL ON ERROR NULL ON EMPTY)";
        }
    }

    private String buildValueSql(JsonValueType valueType) {
        if (valueType == null || valueType == JsonValueType.STRING) {
            return "#{c.value}";
        } else if (valueType == JsonValueType.NUMBER) {
            // bind as string is fine; Oracle will convert to NUMBER on compare
            return "TO_NUMBER(#{c.value})";
        } else if (valueType == JsonValueType.BOOLEAN) {
            // compare to 'true'/'false'
            return "LOWER(#{c.value})";
        } else {
            return "#{c.value}";
        }
    }

    /**
     * Oracle: Maps a high-level JsonOperator to its SQL operator
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
            throw new IllegalArgumentException("Unsupported JsonOperator for Oracle: " + op);
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
