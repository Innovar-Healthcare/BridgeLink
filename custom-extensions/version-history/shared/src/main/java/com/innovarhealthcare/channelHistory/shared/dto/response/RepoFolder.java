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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RepoFolder {

    private String name;
    private int fileCount;
    private List<RepoFile> files;

    public RepoFolder() {
    }

    @JsonCreator
    public RepoFolder(@JsonProperty("name") String name, @JsonProperty("fileCount") int fileCount, @JsonProperty("files") List<RepoFile> files) {
        this.name = name;
        this.fileCount = fileCount;
        this.files = files != null ? files : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public List<RepoFile> getFiles() {
        return files;
    }

    public void setFiles(List<RepoFile> files) {
        this.files = files;
    }

    //@formatter:off
    @Override
    public String toString() {
        return "RepoFolder{" +
                "name='" + name + '\'' +
                ", fileCount=" + fileCount +
                '}';
    }
    //@formatter:on
}
