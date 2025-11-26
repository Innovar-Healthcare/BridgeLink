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

import com.mirth.connect.plugins.dynamiclookup.server.dao.support.JsonFieldCriterion;
import com.mirth.connect.plugins.dynamiclookup.server.dao.support.JsonFieldIndexDefinition;
import com.mirth.connect.plugins.dynamiclookup.server.util.JsonFieldUtils;
import com.mirth.connect.plugins.dynamiclookup.server.util.LookupTableNaming;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;

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

    private String buildExpression(String fieldPath) {
        String jsonPath = "$." + fieldPath; // "address.city"
        return "CAST(JSON_UNQUOTE(JSON_EXTRACT(VALUE_DATA, '" + jsonPath + "')) AS CHAR(255))";
    }

    /**
     * Builds an index name for MySQL, based on table name and field path.
     *
     * Example: tableName = LOOKUP_VALUE_1008, fieldPath = "email" -> idx_LOOKUP_VALUE_1008_json_email
     */
    private String buildIndexName(String tableName, String fieldPath) {
        String sanitizedField = fieldPath.toLowerCase().replaceAll("[^a-z0-9]+", "_");

        return "idx_" + tableName + "_json_" + sanitizedField;
    }
}
