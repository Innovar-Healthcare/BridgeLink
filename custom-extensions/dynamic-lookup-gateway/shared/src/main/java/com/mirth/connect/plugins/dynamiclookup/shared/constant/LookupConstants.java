/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.constant;

import java.util.Locale;

/**
 * Shared constants and helper methods for Dynamic Lookup value types and JSON index modes.
 *
 * This avoids scattering raw string literals ("TEXT"/"JSON", "GIN"/"NONE") across the system.
 */
public final class LookupConstants {

    private LookupConstants() {
        // Utility class; do not instantiate
    }

    // ----------------------------------------------------------------------
    // Value type constants
    // ----------------------------------------------------------------------
    public static final String VALUE_TYPE_TEXT = "TEXT";
    public static final String VALUE_TYPE_JSON = "JSON";

    // ----------------------------------------------------------------------
    // JSON index mode constants
    // ----------------------------------------------------------------------
    public static final String JSON_INDEX_NONE = "NONE";
    public static final String JSON_INDEX_GIN = "GIN";
    public static final String JSON_INDEX_FIELD = "FIELD";

    // ----------------------------------------------------------------------
    // Normalizers
    // ----------------------------------------------------------------------

    /**
     * Normalize a value type string to either "TEXT" or "JSON".
     */
    public static String normalizeValueType(String valueType) {
        if (valueType == null) {
            return VALUE_TYPE_TEXT;
        }

        switch (valueType.toUpperCase(Locale.ROOT)) {
        case VALUE_TYPE_JSON:
            return VALUE_TYPE_JSON;
        default:
            return VALUE_TYPE_TEXT;
        }
    }

    /**
     * Normalize JSON index mode to "NONE", "GIN", or "FIELD".
     */
    public static String normalizeJsonIndexMode(String mode) {
        if (mode == null) {
            return JSON_INDEX_NONE;
        }
        switch (mode.toUpperCase(Locale.ROOT)) {
        case JSON_INDEX_GIN:
            return JSON_INDEX_GIN;
        case JSON_INDEX_FIELD:
            return JSON_INDEX_FIELD;
        default:
            return JSON_INDEX_NONE;
        }
    }

    // ----------------------------------------------------------------------
    // Value type checks
    // ----------------------------------------------------------------------

    public static boolean isJsonValueType(String valueType) {
        return VALUE_TYPE_JSON.equalsIgnoreCase(valueType);
    }

    public static boolean isTextValueType(String valueType) {
        return VALUE_TYPE_TEXT.equalsIgnoreCase(valueType);
    }

    // ----------------------------------------------------------------------
    // JSON index mode checks
    // ----------------------------------------------------------------------

    public static boolean isGinMode(String mode) {
        return JSON_INDEX_GIN.equalsIgnoreCase(mode);
    }

    public static boolean isFieldMode(String mode) {
        return JSON_INDEX_FIELD.equalsIgnoreCase(mode);
    }

    public static boolean isNoneMode(String mode) {
        return JSON_INDEX_NONE.equalsIgnoreCase(mode);
    }
}
