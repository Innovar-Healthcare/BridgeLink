/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.shared.dto.response;

import java.util.Map;

public class ErrorResponse {
    private String status;
    private String code;
    private String message;
    private String timestamp;
    /** Non-null only for GIT_CONFLICT responses — maps relative file paths to their pre-reset content. */
    private Map<String, String> backedUpContent;

    public ErrorResponse() {
    }

    public ErrorResponse(String code, String message) {
        this.status = "error";
        this.code = code;
        this.message = message;
        this.timestamp = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).toString();
    }

    public String getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getBackedUpContent() {
        return backedUpContent;
    }

    public void setBackedUpContent(Map<String, String> backedUpContent) {
        this.backedUpContent = backedUpContent;
    }
}