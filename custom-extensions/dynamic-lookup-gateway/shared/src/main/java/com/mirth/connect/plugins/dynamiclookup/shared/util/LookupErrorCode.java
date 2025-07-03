/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.util;

public final class LookupErrorCode {

    private LookupErrorCode() {
        // prevent instantiation
    }

    public static final String INVALID_REQUEST = "INVALID_REQUEST"; // Malformed request
    public static final String GROUP_NOT_FOUND = "GROUP_NOT_FOUND"; // Lookup group not found
    public static final String VALUE_NOT_FOUND = "VALUE_NOT_FOUND"; // Lookup value not found
    public static final String DUPLICATE_GROUP_NAME = "DUPLICATE_GROUP_NAME"; // Group name already exists
    public static final String INVALID_KEY = "INVALID_KEY"; // Invalid key format or length
    public static final String DATABASE_ERROR = "DATABASE_ERROR"; // DB operation failed
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED"; // Insufficient permissions
}