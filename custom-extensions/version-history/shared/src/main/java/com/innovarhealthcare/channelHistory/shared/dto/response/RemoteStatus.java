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

/**
 * Sync status between local branch and remote tracking branch.
 * Computed after a real {@code git fetch}, so values are accurate at query time.
 */
public class RemoteStatus {

    private int aheadCount;
    private int behindCount;

    public RemoteStatus() {
    }

    @JsonCreator
    public RemoteStatus(@JsonProperty("aheadCount") int aheadCount,
                        @JsonProperty("behindCount") int behindCount) {
        this.aheadCount = aheadCount;
        this.behindCount = behindCount;
    }

    public int getAheadCount() {
        return aheadCount;
    }

    public void setAheadCount(int aheadCount) {
        this.aheadCount = aheadCount;
    }

    public int getBehindCount() {
        return behindCount;
    }

    public void setBehindCount(int behindCount) {
        this.behindCount = behindCount;
    }

    @Override
    public String toString() {
        return "RemoteStatus{aheadCount=" + aheadCount + ", behindCount=" + behindCount + '}';
    }
}
