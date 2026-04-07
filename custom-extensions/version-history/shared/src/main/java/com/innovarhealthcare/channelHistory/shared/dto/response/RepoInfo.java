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

public class RepoInfo {

    private String localRepoPath;
    private String remoteUrl;
    private String branch;
    private long totalSizeBytes;
    private List<RepoFolder> folders;

    public RepoInfo() {
    }

    @JsonCreator
    public RepoInfo(@JsonProperty("localRepoPath") String localRepoPath, @JsonProperty("remoteUrl") String remoteUrl, @JsonProperty("branch") String branch, @JsonProperty("totalSizeBytes") long totalSizeBytes, @JsonProperty("folders") List<RepoFolder> folders) {
        this.localRepoPath = localRepoPath;
        this.remoteUrl = remoteUrl;
        this.branch = branch;
        this.totalSizeBytes = totalSizeBytes;
        this.folders = folders != null ? folders : new ArrayList<>();
    }

    public String getLocalRepoPath() {
        return localRepoPath;
    }

    public void setLocalRepoPath(String localRepoPath) {
        this.localRepoPath = localRepoPath;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public long getTotalSizeBytes() {
        return totalSizeBytes;
    }

    public void setTotalSizeBytes(long totalSizeBytes) {
        this.totalSizeBytes = totalSizeBytes;
    }

    public List<RepoFolder> getFolders() {
        return folders;
    }

    public void setFolders(List<RepoFolder> folders) {
        this.folders = folders;
    }

    //@formatter:off
    @Override
    public String toString() {
        return "RepoInfo{" +
                "localRepoPath='" + localRepoPath + '\'' +
                ", remoteUrl='" + remoteUrl + '\'' +
                ", branch='" + branch + '\'' +
                ", totalSizeBytes=" + totalSizeBytes +
                ", folderCount=" + (folders != null ? folders.size() : 0) +
                '}';
    }
    //@formatter:on
}
