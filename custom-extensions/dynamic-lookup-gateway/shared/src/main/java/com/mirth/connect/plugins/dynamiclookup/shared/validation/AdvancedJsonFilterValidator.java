/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.validation;

import java.math.BigDecimal;
import java.util.List;

import com.mirth.connect.plugins.dynamiclookup.shared.capability.LookupJsonCapability;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonCondition;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonOperator;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonValueType;

public final class AdvancedJsonFilterValidator {

    private AdvancedJsonFilterValidator() {
    }

    /**
     * Core validation (database-agnostic).
     *
     * What this validates: - field must be present and non-empty - operator must be present - valueType must be present -
     * value must be present and non-empty - JSON field path syntax (letters/digits/_/. and dot rules) - NUMBER value must
     * be parseable as BigDecimal - BOOLEAN value must be "true" or "false" - BOOLEAN only supports EQUAL / NOT_EQUAL
     * (universal rule)
     *
     * This method: - DOES NOT mutate JsonCondition - DOES NOT apply database-specific rules
     */
    public static void validateCore(List<JsonCondition> conditions) {
        if (conditions == null) {
            return;
        }

        for (int i = 0; i < conditions.size(); i++) {
            JsonCondition c = conditions.get(i);
            if (c == null) {
                continue;
            }

            int idx = i + 1;

            String field = c.getField() != null ? c.getField().trim() : "";
            String valueText = c.getValue() != null ? c.getValue().toString().trim() : "";

            // ---- required checks ----
            if (field.isEmpty()) {
                throw new IllegalArgumentException("Condition " + idx + ": Field cannot be empty.");
            }
            if (c.getOp() == null) {
                throw new IllegalArgumentException("Condition " + idx + ": Operator is required.");
            }
            if (c.getValueType() == null) {
                throw new IllegalArgumentException("Condition " + idx + ": Value type is required.");
            }
            if (valueText.isEmpty()) {
                throw new IllegalArgumentException("Condition " + idx + ": Value cannot be empty.");
            }

            // ---- JSON field path validation ----
            // Allowed: letters, digits, underscore (_), dot (.)
            if (!field.matches("[A-Za-z0-9_.]+")) {
                throw new IllegalArgumentException("Condition " + idx + ": Invalid JSON field path '" + field + "'. Only letters, digits, underscore (_), and dot (.) are allowed.");
            }
            if (field.startsWith(".") || field.endsWith(".") || field.contains("..")) {
                throw new IllegalArgumentException("Condition " + idx + ": Invalid JSON field path '" + field + "'.");
            }

            JsonValueType type = c.getValueType();
            JsonOperator op = c.getOp();

            // ---- universal operator rules ----
            if (type == JsonValueType.STRING && !isEqOrNe(op)) {
                throw new IllegalArgumentException("Condition " + idx + ": Operator " + op + " is not supported for type STRING (field='" + field + "').");
            }

            // BOOLEAN is never ordered in any DB
            if (type == JsonValueType.BOOLEAN && !isEqOrNe(op)) {
                throw new IllegalArgumentException("Condition " + idx + ": Operator " + op + " is not supported for type BOOLEAN (field='" + field + "').");
            }

            // ---- value format validation ----
            if (type == JsonValueType.NUMBER) {
                try {
                    new BigDecimal(valueText);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Condition " + idx + ": Invalid NUMBER value for field '" + field + "': " + valueText);
                }
            } else if (type == JsonValueType.BOOLEAN) {
                String v = valueText.toLowerCase();
                // Strict by design: only true/false (no 1/0)
                if (!"true".equals(v) && !"false".equals(v)) {
                    throw new IllegalArgumentException("Condition " + idx + ": Invalid BOOLEAN value for field '" + field + "': " + valueText);
                }
            }
        }
    }

    /**
     * Dialect-aware validation.
     *
     * What this validates: - runs validateCore(...) first - checks whether the current database supports: - the given
     * JsonValueType - the given operator for that value type
     *
     * Uses LookupJsonCapability as the single source of truth for database-specific behavior.
     *
     * This method: - DOES NOT mutate JsonCondition - MUST be called on the server side before building SQL
     */
    public static void validateForDialect(List<JsonCondition> conditions, LookupJsonCapability capability) {

        validateCore(conditions);

        if (conditions == null || conditions.isEmpty()) {
            return;
        }

        if (capability == null) {
            // Client UI / transformer may not know DB capability.
            // Server side should always provide it.
            return;
        }

        if (!capability.isJsonSupported()) {
            throw new IllegalArgumentException("JSON filtering is not supported for database " + capability.getDatabaseInfo().getType());
        }

        for (int i = 0; i < conditions.size(); i++) {
            JsonCondition c = conditions.get(i);
            if (c == null) {
                continue;
            }

            int idx = i + 1;
            String field = c.getField();

            JsonValueType type = c.getValueType();
            JsonOperator op = c.getOp();

            if (!capability.supportsValueType(type)) {
                throw new IllegalArgumentException("Condition " + idx + ": Value type " + type + " is not supported by database " + capability.getDatabaseInfo().getType() + " (field='" + field + "').");
            }

            if (!capability.supportsOperator(type, op)) {
                throw new IllegalArgumentException("Condition " + idx + ": Operator " + op + " is not supported for type " + type + " on database " + capability.getDatabaseInfo().getType() + " (field='" + field + "').");
            }
        }
    }

    private static boolean isEqOrNe(JsonOperator op) {
        return op == JsonOperator.EQUAL || op == JsonOperator.NOT_EQUAL;
    }
}