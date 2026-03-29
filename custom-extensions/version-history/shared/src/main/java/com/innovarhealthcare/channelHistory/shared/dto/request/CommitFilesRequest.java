/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.shared.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.List;

/**
 * Request DTO for committing and pushing a specific set of files.
 * Used by the {@code POST /commitAndPushFiles} endpoint.
 */
public class CommitFilesRequest {

    private final List<String> filePaths;
    private final String message;
    private final String userId;

    @JsonCreator
    public CommitFilesRequest(
            @JsonProperty("filePaths") List<String> filePaths,
            @JsonProperty("message") String message,
            @JsonProperty("userId") String userId) {
        this.filePaths = filePaths;
        this.message = message;
        this.userId = userId;
    }

    @JsonProperty("filePaths")
    public List<String> getFilePaths() {
        return filePaths;
    }

    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    @JsonProperty("userId")
    public String getUserId() {
        return userId;
    }

    //@formatter:off
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CommitFilesRequest that = (CommitFilesRequest) obj;
        return new EqualsBuilder()
                .append(filePaths, that.filePaths)
                .append(message, that.message)
                .append(userId, that.userId)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(filePaths)
                .append(message)
                .append(userId)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("filePaths", filePaths)
                .append("message", message)
                .append("userId", userId)
                .toString();
    }
    //@formatter:on
}
