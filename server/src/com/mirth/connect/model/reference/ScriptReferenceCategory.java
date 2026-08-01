/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.model.reference;

public enum ScriptReferenceCategory {
    // @formatter:off
    CONVERSION("Conversion Functions"),
    LOGGING_AND_ALERTS("Logging and Alerts"),
    DATABASE("Database Functions"),
    UTILITY("Utility Functions"),
    DATE("Date Functions"),
    MESSAGE("Message Functions"),
    RESPONSE("Response Transformer"),
    MAP("Map Functions"),
    CHANNEL("Channel Functions"),
    POSTPROCESSOR("Postprocessor Functions");
    // @formatter:on

    private String value;

    private ScriptReferenceCategory(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
