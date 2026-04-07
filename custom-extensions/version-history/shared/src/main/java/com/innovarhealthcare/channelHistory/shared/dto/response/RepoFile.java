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

public class RepoFile {

    private String name;
    private long sizeBytes;

    public RepoFile() {
    }

    @JsonCreator
    public RepoFile(@JsonProperty("name") String name, @JsonProperty("sizeBytes") long sizeBytes) {
        this.name = name;
        this.sizeBytes = sizeBytes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    //@formatter:off
    @Override
    public String toString() {
        return "RepoFile{" +
                "name='" + name + '\'' +
                ", sizeBytes=" + sizeBytes +
                '}';
    }
    //@formatter:on
}
