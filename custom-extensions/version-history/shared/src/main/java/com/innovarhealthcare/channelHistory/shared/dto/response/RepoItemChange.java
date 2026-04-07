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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Represents a single file change within a commit.
 * {@code changeType} is one of {@code "MODIFIED"}, {@code "ADDED"}, {@code "DELETED"}.
 */
public class RepoItemChange {

    private String path;
    private String changeType;

    public RepoItemChange() {
    }

    @JsonCreator
    public RepoItemChange(
            @JsonProperty("path")       String path,
            @JsonProperty("changeType") String changeType) {
        this.path       = path;
        this.changeType = changeType;
    }

    @JsonProperty("path")
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @JsonProperty("changeType")
    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    //@formatter:off
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        RepoItemChange that = (RepoItemChange) obj;
        return new EqualsBuilder()
                .append(path,       that.path)
                .append(changeType, that.changeType)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(path)
                .append(changeType)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("path",       path)
                .append("changeType", changeType)
                .toString();
    }
    //@formatter:on
}
