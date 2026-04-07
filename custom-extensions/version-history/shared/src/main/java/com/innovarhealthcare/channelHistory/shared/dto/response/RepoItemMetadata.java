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

/* Metadata for repository items (Channels, CodeTemplates, etc.)
 * containing summary information without full content.
 */
public class RepoItemMetadata {

    private String id;
    private String name;
    private String path;
    private String lastCommitId;

    /**
     * Default constructor for serialization frameworks
     */
    public RepoItemMetadata() {
    }

    /**
     * Full constructor
     */
    @JsonCreator
    public RepoItemMetadata(@JsonProperty("id") String id, @JsonProperty("name") String name, @JsonProperty("path") String path, @JsonProperty("lastCommitId") String lastCommitId) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.lastCommitId = lastCommitId;
    }

    // Getters and Setters

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("path")
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @JsonProperty("lastCommitId")
    public String getLastCommitId() {
        return lastCommitId;
    }

    public void setLastCommitId(String lastCommitId) {
        this.lastCommitId = lastCommitId;
    }

    //@formatter:off
    // equals, hashCode, toString
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        RepoItemMetadata that = (RepoItemMetadata) obj;
        return new EqualsBuilder()
                .append(id, that.id)
                .append(name, that.name)
                .append(path, that.path)
                .append(lastCommitId, that.lastCommitId)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(id)
                .append(name)
                .append(path)
                .append(lastCommitId)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("name", name)
                .append("path", path)
                .append("lastCommitId", lastCommitId)
                .toString();
    }
    //@formatter:on
}
