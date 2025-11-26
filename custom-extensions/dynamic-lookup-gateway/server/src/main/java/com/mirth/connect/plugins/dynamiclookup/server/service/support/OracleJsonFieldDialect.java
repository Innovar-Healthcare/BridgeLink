package com.mirth.connect.plugins.dynamiclookup.server.service.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mirth.connect.plugins.dynamiclookup.server.util.JsonFieldUtils;
import com.mirth.connect.plugins.dynamiclookup.server.util.LookupTableNaming;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;

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
            String expression = buildExpression(fieldPath);
            String indexName = buildIndexName(tableName, fieldPath);

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
        if (group == null || filters == null || filters.isEmpty()) {
            return Collections.emptyList();
        }

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

            String expression = buildExpression(fieldPath);

            JsonFieldCriterion criterion = new JsonFieldCriterion();
            criterion.setExpression(expression);
            criterion.setValue(value);

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
    private String buildExpression(String fieldPath) {
        String jsonPath = "$." + fieldPath;
        return "JSON_VALUE(VALUE_DATA, '" + jsonPath + "' RETURNING VARCHAR2(4000))";
    }

    /**
     * Builds an index name for Oracle, based on table name and field path.
     *
     * Example: tableName = LOOKUP_VALUE_1008, fieldPath = "email" -> idx_LOOKUP_VALUE_1008_json_email
     */
    private String buildIndexName(String tableName, String fieldPath) {
        String sanitizedField = fieldPath.toLowerCase().replaceAll("[^a-z0-9]+", "_");

        return "idx_" + tableName + "_json_" + sanitizedField;
    }
}
