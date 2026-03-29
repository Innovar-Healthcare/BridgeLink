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

public class RepoChanges {

    private List<String> modifiedFiles;   // status.getModified()
    private List<String> deletedFiles;    // status.getRemoved() + status.getMissing()
    private List<String> untrackedFiles;  // status.getUntracked()

    public RepoChanges() {
    }

    @JsonCreator
    public RepoChanges(@JsonProperty("modifiedFiles") List<String> modifiedFiles, @JsonProperty("deletedFiles") List<String> deletedFiles, @JsonProperty("untrackedFiles") List<String> untrackedFiles) {
        this.modifiedFiles  = modifiedFiles  != null ? modifiedFiles  : new ArrayList<>();
        this.deletedFiles   = deletedFiles   != null ? deletedFiles   : new ArrayList<>();
        this.untrackedFiles = untrackedFiles != null ? untrackedFiles : new ArrayList<>();
    }

    public List<String> getModifiedFiles() {
        return modifiedFiles;
    }

    public void setModifiedFiles(List<String> modifiedFiles) {
        this.modifiedFiles = modifiedFiles;
    }

    public List<String> getDeletedFiles() {
        return deletedFiles;
    }

    public void setDeletedFiles(List<String> deletedFiles) {
        this.deletedFiles = deletedFiles;
    }

    public List<String> getUntrackedFiles() {
        return untrackedFiles;
    }

    public void setUntrackedFiles(List<String> untrackedFiles) {
        this.untrackedFiles = untrackedFiles;
    }

    //@formatter:off
    @Override
    public String toString() {
        return "RepoChanges{" +
                "modifiedFiles="  + (modifiedFiles  != null ? modifiedFiles.size()  : 0) +
                ", deletedFiles=" + (deletedFiles    != null ? deletedFiles.size()   : 0) +
                ", untrackedFiles=" + (untrackedFiles != null ? untrackedFiles.size() : 0) +
                '}';
    }
    //@formatter:on
}
