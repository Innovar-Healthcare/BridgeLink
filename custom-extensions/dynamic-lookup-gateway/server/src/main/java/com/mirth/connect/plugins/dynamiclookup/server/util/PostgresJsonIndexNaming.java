package com.mirth.connect.plugins.dynamiclookup.server.util;

public class PostgresJsonIndexNaming {
    private PostgresJsonIndexNaming() {
    }

    public static String buildGinIndexName(String tableName) {
        // Example: dl_group_12_values → idx_dl_group_12_values_json_gin
        String sanitized = tableName.toLowerCase().replaceAll("[^a-z0-9_]+", "_");
        return "idx_" + sanitized + "_json_gin";
    }
}
