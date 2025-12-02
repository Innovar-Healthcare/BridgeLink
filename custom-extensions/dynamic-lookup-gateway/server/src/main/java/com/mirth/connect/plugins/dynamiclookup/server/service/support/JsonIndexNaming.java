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

public class JsonIndexNaming {
    private JsonIndexNaming() {
    }

    public static String buildIndexName(String tableName, String fieldPath) {
        String name = tableName.toLowerCase().replaceAll("[^a-z0-9]+", "_");
        String field = fieldPath.toLowerCase().replaceAll("[^a-z0-9]+", "_");

        return "idx_" + name + "_json_" + field;
    }

    public static String buildGinIndexName(String tableName) {
        String name = tableName.toLowerCase().replaceAll("[^a-z0-9_]+", "_");
        return "idx_" + name + "_json_gin";
    }
}
