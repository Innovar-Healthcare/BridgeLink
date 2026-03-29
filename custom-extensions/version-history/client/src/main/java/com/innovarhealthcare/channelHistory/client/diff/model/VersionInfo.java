/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.diff.model;

import java.util.Date;
import java.util.Objects;

public class VersionInfo {
    private final String name;
    private final String version;
    private final String author;
    private final Date timestamp;
    private final boolean isCurrent;

    private VersionInfo(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "Name cannot be null");
        this.version = Objects.requireNonNull(builder.version, "Version cannot be null");
        this.author = builder.author;
        this.timestamp = builder.timestamp;
        this.isCurrent = builder.isCurrent;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    /**
     * Builder for flexible VersionInfo construction
     */
    public static class Builder {
        private String name;
        private String version;
        private String author;
        private Date timestamp;
        private boolean isCurrent = false;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder timestamp(Date timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder isCurrent(boolean isCurrent) {
            this.isCurrent = isCurrent;
            return this;
        }

        public VersionInfo build() {
            return new VersionInfo(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Helper factory method: Create current version info
     */
    public static VersionInfo createCurrent(String name, String author) {
        return builder().name(name).version("Current").author(author).timestamp(new Date()).isCurrent(true).build();
    }

    /**
     * Helper factory method: Create historical version info
     */
    public static VersionInfo createHistorical(String name, String versionHash, String author, Date timestamp) {
        return builder().name(name).version(versionHash).author(author).timestamp(timestamp).isCurrent(false).build();
    }

    @Override
    public String toString() {
        return String.format("VersionInfo{name='%s', version='%s', author='%s', isCurrent=%s}", name, version, author, isCurrent);
    }
}
