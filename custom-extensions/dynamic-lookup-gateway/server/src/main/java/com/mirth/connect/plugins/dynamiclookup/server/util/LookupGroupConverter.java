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

import java.util.Map;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;

public final class LookupGroupConverter {

    private LookupGroupConverter() {
    }

    public static LookupGroup toLookupGroup(Map<String, String> map) {
        LookupGroup group = new LookupGroup();
        group.setName(trimOrNull(map.get("name")));
        group.setDescription(trimOrNull(map.get("description")));
        group.setVersion(trimOrNull(map.get("version")));

        int cacheSize = 1000; // default
        String cacheSizeStr = trimOrNull(map.get("cacheSize"));
        if (cacheSizeStr != null) {
            try {
                cacheSize = Integer.parseInt(cacheSizeStr);
            } catch (NumberFormatException ignore) {
            }
        }
        group.setCacheSize(cacheSize);

        group.setCachePolicy(trimOrNull(map.get("cachePolicy")));

        group.setValueType(trimOrNull(map.get("valueType")));

        String jsonIndexMode = trimOrNull(map.get("jsonIndexMode"));
        String indexedJsonFields = trimOrNull(map.get("indexedJsonFields"));

        if (jsonIndexMode != null || indexedJsonFields != null) {
            LookupGroupExtra extra = new LookupGroupExtra();
            extra.setJsonIndexMode(jsonIndexMode);
            extra.setIndexedJsonFields(indexedJsonFields);
            group.setExtra(extra);
        }

        return group;
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }

        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
