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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class JsonIndexNaming {
    private JsonIndexNaming() {
    }

    public static String buildIndexName(String tableName, String fieldPath) {
        String name = tableName.toLowerCase().replaceAll("[^a-z0-9]+", "_");
        String field = fieldPath != null ? fieldPath.trim().toLowerCase() : "";

        String fieldHash = shortHash(field);

        return "idx_" + name + "_json_" + fieldHash;
    }

    private static String shortHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));

            // 64-bit hex (16 chars) is enough and short
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute index name hash", e);
        }
    }
}
