/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.shared.model;

import com.innovarhealthcare.channelHistory.shared.util.CommitMessageUtil;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class CommitMetaData {
    private String hash;
    private String committer;
    private long timestamp;
    private String message;

    /**
     * Default constructor for Jackson deserialization
     */
    public CommitMetaData() {
    }

    /**
     * Constructor with all fields
     */
    public CommitMetaData(String hash, String committer, long timestamp, String message) {
        this.hash = hash;
        this.committer = committer;
        this.timestamp = timestamp;
        this.message = message;
    }

    // ==================== Getters and Setters ====================

    public String getHash() {
        return hash;
    }

    public String getShortHash() {
        if (hash == null || hash.length() < 8) {
            return "(invalid)";
        }

        return hash.substring(0, 8);
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getCommitter() {
        return committer;
    }

    public void setCommitter(String committer) {
        this.committer = committer;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Gets the CommitMessage object
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the CommitMessage object
     */
    public void setMessage(String message) {
        this.message = message;
    }

    // ==================== Convenience Delegate Methods ====================
    public String getMessageContent() {
        return CommitMessageUtil.extractContent(message);
    }

    public String getServerId() {
        return CommitMessageUtil.extractServerId(message);
    }

    public String getServerName() {
        return CommitMessageUtil.extractServerName(message);
    }
}