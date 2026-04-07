/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.server.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.innovarhealthcare.channelHistory.shared.dto.response.ErrorResponseFactory;
import com.innovarhealthcare.channelHistory.shared.util.JsonUtils;

public class VersionHistoryApiException extends WebApplicationException {
    // Basic constructor: 500 without message
    public VersionHistoryApiException() {
        super(buildJsonResponse(Status.INTERNAL_SERVER_ERROR, "UNKNOWN_ERROR", "An unexpected error occurred."));
    }

    // Status only (use with caution — no code/message)
    public VersionHistoryApiException(Status status) {
        super(buildJsonResponse(status, status.name(), status.getReasonPhrase()));
    }

    // Full detail
    public VersionHistoryApiException(Status status, String code, String message) {
        super(buildJsonResponse(status, code, message));
    }

    // Custom HTTP status code
    public VersionHistoryApiException(int statusCode, String code, String message) {
        super(buildJsonResponse(Status.fromStatusCode(statusCode), code, message));
    }

    // Wrap another exception
    public VersionHistoryApiException(Throwable cause) {
        super(buildJsonResponse(Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", cause.getMessage()));
    }

    // Accept prebuilt response (for advanced use)
    public VersionHistoryApiException(Response response) {
        super(response);
    }

    private static Response buildJsonResponse(Status status, String code, String message) {
        try {
            return Response.status(status).type(MediaType.APPLICATION_JSON).entity(JsonUtils.toJson(ErrorResponseFactory.build(code, message))).build();

        } catch (Exception e) {
            // Fallback: plain JSON string in case serialization fails
            String fallbackJson = "{\"status\":\"error\",\"code\":\"INTERNAL_ERROR\",\"message\":\"Failed to serialize error response\",\"timestamp\":\"" + ZonedDateTime.now(ZoneOffset.UTC).toString() + "\"}";
            return Response.status(status).type(MediaType.APPLICATION_JSON).entity(fallbackJson).build();
        }
    }
}
