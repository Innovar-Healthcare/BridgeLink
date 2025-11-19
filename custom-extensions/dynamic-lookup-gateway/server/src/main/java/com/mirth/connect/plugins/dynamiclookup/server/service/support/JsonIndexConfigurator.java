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
import java.util.LinkedHashSet;
import java.util.Set;

import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupValueDao;
import com.mirth.connect.plugins.dynamiclookup.shared.constant.LookupConstants;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;
import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

public class JsonIndexConfigurator {

    private final LookupValueDao valueDao;

    public JsonIndexConfigurator(LookupValueDao valueDao) {
        this.valueDao = valueDao;
    }

    /**
     * Apply initial index configuration for a newly created group/table. Treats the old mode as NONE.
     */
    public void applyInitialIndexConfig(LookupGroupExtra newExtra, String tableName) {
        apply(null, newExtra, tableName);
    }

    /**
     * Apply index configuration changes when group extra is updated. Handles transitions between NONE, GIN, FIELD modes and
     * FIELD->FIELD field list diffs.
     */
    public void apply(LookupGroupExtra oldExtra, LookupGroupExtra newExtra, String tableName) {

        // ---- Normalize modes ----
        String oldMode = (oldExtra != null) ? LookupConstants.normalizeJsonIndexMode(oldExtra.getJsonIndexMode()) : LookupConstants.JSON_INDEX_NONE;

        String newMode = (newExtra != null) ? LookupConstants.normalizeJsonIndexMode(newExtra.getJsonIndexMode()) : LookupConstants.JSON_INDEX_NONE;

        // ---- Normalize field lists ----
        Set<String> oldFields = parseFieldSet(oldExtra != null ? oldExtra.getIndexedJsonFields() : null);
        Set<String> newFields = parseFieldSet(newExtra != null ? newExtra.getIndexedJsonFields() : null);

        // ---- Early return: mode same + field same ----
        if (oldMode.equals(newMode)) {
            if (LookupConstants.isFieldMode(oldMode) && !oldFields.equals(newFields)) {
                applyFieldDiff(tableName, oldFields, newFields);
            }
            return;
        }

        // ---- Mode transition ----
        switch (oldMode) {
        case LookupConstants.JSON_INDEX_NONE:
            handleNoneTo(newMode, tableName, newFields);
            break;

        case LookupConstants.JSON_INDEX_GIN:
            handleGinTo(newMode, tableName, newFields);
            break;

        case LookupConstants.JSON_INDEX_FIELD:
            handleFieldTo(newMode, tableName, oldFields);
            break;
        }
    }

    // ========================================================================
    // MODE TRANSITIONS
    // ========================================================================

    private void handleNoneTo(String newMode, String tableName, Set<String> newFields) {

        if (LookupConstants.isGinMode(newMode)) {
            valueDao.createJsonGinIndex(tableName);
        } else if (LookupConstants.isFieldMode(newMode)) {
            if (!newFields.isEmpty()) {
                valueDao.createJsonFieldIndexes(tableName, newFields);
            }
        }
    }

    private void handleGinTo(String newMode, String tableName, Set<String> newFields) {

        // Step 1: drop old mode
        valueDao.dropJsonGinIndex(tableName);

        // Step 2: create new mode
        if (LookupConstants.isFieldMode(newMode)) {
            if (!newFields.isEmpty()) {
                valueDao.createJsonFieldIndexes(tableName, newFields);
            }
        }
        // GIN → NONE = done
    }

    private void handleFieldTo(String newMode, String tableName, Set<String> oldFields) {
        // Step 1: drop old field indexes
        if (!oldFields.isEmpty()) {
            valueDao.dropJsonFieldIndexes(tableName, oldFields);
        }

        // Step 2: create new mode
        // FIELD -> GIN
        if (LookupConstants.isGinMode(newMode)) {
            valueDao.createJsonGinIndex(tableName);
        }

        // FIELD → NONE = no-op
    }

    // ========================================================================
    // FIELD → FIELD
    // ========================================================================

    private void applyFieldDiff(String tableName, Set<String> oldFields, Set<String> newFields) {

        Set<String> fieldsToAdd = new HashSet<>(newFields);
        fieldsToAdd.removeAll(oldFields);

        Set<String> fieldsToDrop = new HashSet<>(oldFields);
        fieldsToDrop.removeAll(newFields);

        if (!fieldsToDrop.isEmpty()) {
            valueDao.dropJsonFieldIndexes(tableName, fieldsToDrop);
        }
        if (!fieldsToAdd.isEmpty()) {
            valueDao.createJsonFieldIndexes(tableName, fieldsToAdd);
        }
    }

    // ========================================================================
    // Field list utils
    // ========================================================================

    private Set<String> parseFieldSet(String jsonArray) {
        if (jsonArray == null || jsonArray.trim().isEmpty()) {
            return new LinkedHashSet<>();
        }

        return new LinkedHashSet<>(JsonUtils.fromJsonList(jsonArray, String.class));
    }
}