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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupValueDao;
import com.mirth.connect.plugins.dynamiclookup.server.util.LookupTableNaming;
import com.mirth.connect.plugins.dynamiclookup.server.util.PostgresJsonIndexNaming;
import com.mirth.connect.plugins.dynamiclookup.shared.constant.LookupConstants;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;

public class JsonIndexConfigurator {

    private final LookupValueDao valueDao;

    public JsonIndexConfigurator(LookupValueDao valueDao) {
        this.valueDao = valueDao;
    }

    /**
     * Apply initial index configuration for a newly created group/table. Treats the old mode as NONE.
     */
    public void applyInitialIndexConfig(LookupGroup newGroup) {
        apply(null, newGroup);
    }

    /**
     * Apply index configuration changes when group extra is updated. Handles transitions between NONE, GIN, FIELD modes and
     * FIELD->FIELD field list diffs.
     */
    public void apply(LookupGroup oldGroup, LookupGroup newGroup) {
        if (newGroup == null) {
            return;
        }

        String tableName = LookupTableNaming.valueTableName(newGroup);

        LookupGroupExtra oldExtra = (oldGroup != null) ? oldGroup.getExtra() : null;
        LookupGroupExtra newExtra = (newGroup != null) ? newGroup.getExtra() : null;

        // ---- Normalize modes ----
        String oldMode = (oldExtra != null) ? LookupConstants.normalizeJsonIndexMode(oldExtra.getJsonIndexMode()) : LookupConstants.JSON_INDEX_NONE;

        String newMode = (newExtra != null) ? LookupConstants.normalizeJsonIndexMode(newExtra.getJsonIndexMode()) : LookupConstants.JSON_INDEX_NONE;

        // ---- Normalize field lists ----
        JsonFieldDialect dialect = JsonFieldDialectRegistry.getDialect();
        List<JsonFieldIndexDefinition> oldIndexDefs = dialect.buildIndexDefinitions(oldGroup);
        List<JsonFieldIndexDefinition> newIndexDefs = dialect.buildIndexDefinitions(newGroup);

        // ---- Early return: mode same + field same ----
        if (oldMode.equals(newMode)) {
            if (LookupConstants.isFieldMode(oldMode)) {
                Set<String> oldPaths = oldIndexDefs.stream().map(JsonFieldIndexDefinition::getFieldPath).collect(Collectors.toSet());
                Set<String> newPaths = newIndexDefs.stream().map(JsonFieldIndexDefinition::getFieldPath).collect(Collectors.toSet());
                if (!oldPaths.equals(newPaths)) {
                    applyFieldDiff(tableName, oldIndexDefs, newIndexDefs);
                }
            }

            return;
        }

        // ---- Mode transition ----
        switch (oldMode) {
        case LookupConstants.JSON_INDEX_NONE:
            handleNoneTo(newMode, tableName, newIndexDefs);
            break;

        case LookupConstants.JSON_INDEX_GIN:
            handleGinTo(newMode, tableName, newIndexDefs);
            break;

        case LookupConstants.JSON_INDEX_FIELD:
            handleFieldTo(newMode, tableName, oldIndexDefs);
            break;
        }
    }

    // ========================================================================
    // MODE TRANSITIONS
    // ========================================================================

    private void handleNoneTo(String newMode, String tableName, List<JsonFieldIndexDefinition> newIndexDefs) {

        if (LookupConstants.isGinMode(newMode)) {
            String indexName = PostgresJsonIndexNaming.buildGinIndexName(tableName);
            valueDao.createJsonGinIndex(tableName, indexName);
            return;
        }

        if (LookupConstants.isFieldMode(newMode)) {
            if (!newIndexDefs.isEmpty()) {
                valueDao.createJsonFieldIndexes(tableName, newIndexDefs);
            }
        }
    }

    private void handleGinTo(String newMode, String tableName, List<JsonFieldIndexDefinition> newIndexDefs) {

        // Step 1: drop old mode
        String indexName = PostgresJsonIndexNaming.buildGinIndexName(tableName);
        valueDao.dropJsonGinIndex(tableName, indexName);

        // Step 2: create new mode
        if (LookupConstants.isFieldMode(newMode)) {
            if (!newIndexDefs.isEmpty()) {
                valueDao.createJsonFieldIndexes(tableName, newIndexDefs);
            }
        }
        // GIN → NONE = done
    }

    private void handleFieldTo(String newMode, String tableName, List<JsonFieldIndexDefinition> oldIndexDefs) {
        // Step 1: drop old field indexes
        if (!oldIndexDefs.isEmpty()) {
            valueDao.dropJsonFieldIndexes(tableName, oldIndexDefs);
        }

        // Step 2: create new mode
        // FIELD -> GIN
        if (LookupConstants.isGinMode(newMode)) {
            String indexName = PostgresJsonIndexNaming.buildGinIndexName(tableName);
            valueDao.createJsonGinIndex(tableName, indexName);
        }

        // FIELD → NONE = no-op
    }

    // ========================================================================
    // FIELD → FIELD
    // ========================================================================

    private void applyFieldDiff(String tableName, List<JsonFieldIndexDefinition> oldIndexDefs, List<JsonFieldIndexDefinition> newIndexDefs) {
        // ----- Extract field paths -----
        Set<String> oldPaths = oldIndexDefs.stream().map(JsonFieldIndexDefinition::getFieldPath).collect(Collectors.toSet());

        Set<String> newPaths = newIndexDefs.stream().map(JsonFieldIndexDefinition::getFieldPath).collect(Collectors.toSet());

        // ----- Compute diff -----
        Set<String> fieldsToAdd = new HashSet<>(newPaths);
        fieldsToAdd.removeAll(oldPaths);

        Set<String> fieldsToDrop = new HashSet<>(oldPaths);
        fieldsToDrop.removeAll(newPaths);

        // ----- Filter definitions -----
        List<JsonFieldIndexDefinition> defsToDrop = oldIndexDefs.stream().filter(def -> fieldsToDrop.contains(def.getFieldPath())).collect(Collectors.toList());

        List<JsonFieldIndexDefinition> defsToAdd = newIndexDefs.stream().filter(def -> fieldsToAdd.contains(def.getFieldPath())).collect(Collectors.toList());

        // ----- Apply changes -----
        if (!defsToDrop.isEmpty()) {
            valueDao.dropJsonFieldIndexes(tableName, defsToDrop);
        }

        if (!defsToAdd.isEmpty()) {
            valueDao.createJsonFieldIndexes(tableName, defsToAdd);
        }
    }

}