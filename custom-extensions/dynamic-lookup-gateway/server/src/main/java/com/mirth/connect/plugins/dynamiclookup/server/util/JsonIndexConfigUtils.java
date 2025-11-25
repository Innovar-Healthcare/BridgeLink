/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.util;

import java.util.LinkedHashSet;
import java.util.Set;

import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

public final class JsonIndexConfigUtils {

    private JsonIndexConfigUtils() {
    }

    public static Set<String> parseIndexedFieldPaths(String jsonArray) {
        if (jsonArray == null || jsonArray.trim().isEmpty()) {
            return new LinkedHashSet<>();
        }

        return new LinkedHashSet<>(JsonUtils.fromJsonList(jsonArray, String.class));
    }
}
