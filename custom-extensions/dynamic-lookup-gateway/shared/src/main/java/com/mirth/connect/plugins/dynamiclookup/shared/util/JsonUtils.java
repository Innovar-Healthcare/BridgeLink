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

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;

public class JsonUtils {

    private static final ObjectMapper mapper = createDefaultMapper();

    private static ObjectMapper createDefaultMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Handle ISO 8601 date format (e.g., 2025-05-20T15:30:00Z)
        objectMapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Optional: fail fast on unknown properties
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Optional: skip nulls in output
        // objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Optional: readable output
        // objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        return objectMapper;
    }

    public static ObjectMapper getMapper() {
        return mapper;
    }

    public static <T> T fromJson(String json, Class<T> clazz) throws Exception {
        return mapper.readValue(json, clazz);
    }

    public static String toJson(Object object) throws Exception {
        return mapper.writeValueAsString(object);
    }

    public static String toJsonPretty(Object object) throws Exception {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }

    public static <T> List<T> fromJsonList(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON list", e);
        }
    }

    public static boolean isValidJson(String value) {
        if (value == null) {
            return false;
        }

        try {
            mapper.readTree(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
