package com.mirth.connect.plugins.dynamiclookup.server.exception;

import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.ErrorResponseFactory;
import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;

public class LookupApiException extends WebApplicationException {

    // Basic constructor: 500 without message
    public LookupApiException() {
        super(buildJsonResponse(Status.INTERNAL_SERVER_ERROR, "UNKNOWN_ERROR", "An unexpected error occurred."));
    }

    // Status only (use with caution — no code/message)
    public LookupApiException(Status status) {
        super(buildJsonResponse(status, status.name(), status.getReasonPhrase()));
    }

    // Full detail
    public LookupApiException(Status status, String code, String message) {
        super(buildJsonResponse(status, code, message));
    }

    // Custom HTTP status code
    public LookupApiException(int statusCode, String code, String message) {
        super(buildJsonResponse(Status.fromStatusCode(statusCode), code, message));
    }

    // Wrap another exception
    public LookupApiException(Throwable cause) {
        super(buildJsonResponse(Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", cause.getMessage()));
    }

    // Accept prebuilt response (for advanced use)
    public LookupApiException(Response response) {
        super(response);
    }

    private static Response buildJsonResponse(Status status, String code, String message) {
        try {
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(JsonUtils.toJson(ErrorResponseFactory.build(code, message)))
                    .build();

        } catch (Exception e) {
            // Fallback: plain JSON string in case serialization fails
            String fallbackJson = "{\"status\":\"error\",\"code\":\"INTERNAL_ERROR\",\"message\":\"Failed to serialize error response\",\"timestamp\":\"" +
                    ZonedDateTime.now(ZoneOffset.UTC).toString() + "\"}";
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(fallbackJson)
                    .build();
        }
    }
}

