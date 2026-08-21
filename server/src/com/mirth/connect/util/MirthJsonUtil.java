/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class MirthJsonUtil {

    private static Logger logger = LogManager.getLogger(MirthJsonUtil.class);
    /*
     * Shared mapper for plain POJO <-> JSON conversion (e.g. REST endpoint DTOs). Returning the
     * resulting String directly from a JAX-RS resource method causes Jersey to select its built-in
     * String writer (exact type match) over the API-wide XStream-backed JsonMessageBodyWriter
     * (generic Object match), so the JSON is written as-is with no XStream envelope.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Serializes a plain object (e.g. a REST endpoint DTO) directly to a JSON string via Jackson,
     * bypassing the XStream-based serialization used elsewhere in the API.
     */
    public static String toJson(Object object) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(object);
    }

    /**
     * Deserializes a plain JSON string directly into a value via Jackson, the read-side counterpart
     * to {@link #toJson(Object)} for REST endpoints that accept a raw JSON request body instead of
     * going through the API-wide XStream-based deserialization. Takes a {@link TypeReference} so
     * generic targets such as {@code Map<String, String>} can be deserialized.
     */
    public static <T> T fromJson(String json, TypeReference<T> type) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(json, type);
    }

    public static String prettyPrint(String input) {
        ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            // Modified Jackson's default pretty printer to separate each array element onto its own line
            DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
            prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
            JsonNode json = mapper.readTree(input);

            return mapper.writer(prettyPrinter).writeValueAsString(json);
        } catch (Exception e) {
            logger.warn("Error pretty printing json.", e);
        }

        return input;
    }

    public static String escape(String input) {
        return StringEscapeUtils.escapeJson(input);
    }
}
