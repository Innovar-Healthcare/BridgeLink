/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.mirth.connect.donkey.model.channel.DeployedState;

/**
 * The response body of GET /channels/{id}/_threads (IRT-2107): the channel's lifecycle state, how
 * long it has been in it, whether that is overdue against the stop grace period, what the most recent
 * stop or halt timed out waiting on, and every live thread that belongs to the channel. Served as
 * plain JSON, not the serialized envelope, so the Web UI and blctl can consume it directly.
 */
public class ChannelThreadReport implements Serializable {

    /**
     * What the channel's most recent lifecycle operation was waiting on when its grace period ran
     * out. Null until a stop or halt has timed out; cleared by the next start.
     */
    public static class LifecycleTimeout implements Serializable {
        private String phase;
        private long timestamp;
        private Long threadId;
        private String threadName;
        private String threadState;
        private List<String> stackTrace = new ArrayList<String>();
        private String message;

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        /** Epoch milliseconds when the timeout was detected. */
        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public Long getThreadId() {
            return threadId;
        }

        public void setThreadId(Long threadId) {
            this.threadId = threadId;
        }

        public String getThreadName() {
            return threadName;
        }

        public void setThreadName(String threadName) {
            this.threadName = threadName;
        }

        public String getThreadState() {
            return threadState;
        }

        public void setThreadState(String threadState) {
            this.threadState = threadState;
        }

        public List<String> getStackTrace() {
            return stackTrace;
        }

        public void setStackTrace(List<String> stackTrace) {
            this.stackTrace = stackTrace;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    private String channelId;
    private String channelName;
    private DeployedState state;
    private long stateSince;
    private long stopGracePeriodMillis;
    private boolean lifecycleOverdue;
    private LifecycleTimeout lastTimeout;
    private long collectedAt;
    private List<ChannelThreadInfo> threads = new ArrayList<ChannelThreadInfo>();

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public DeployedState getState() {
        return state;
    }

    public void setState(DeployedState state) {
        this.state = state;
    }

    /** Epoch milliseconds of the channel's last state change. */
    public long getStateSince() {
        return stateSince;
    }

    public void setStateSince(long stateSince) {
        this.stateSince = stateSince;
    }

    /** The stop grace period in force for this channel; zero means unbounded. */
    public long getStopGracePeriodMillis() {
        return stopGracePeriodMillis;
    }

    public void setStopGracePeriodMillis(long stopGracePeriodMillis) {
        this.stopGracePeriodMillis = stopGracePeriodMillis;
    }

    /** True when the channel has been STOPPING or STARTING longer than the grace period. */
    public boolean isLifecycleOverdue() {
        return lifecycleOverdue;
    }

    public void setLifecycleOverdue(boolean lifecycleOverdue) {
        this.lifecycleOverdue = lifecycleOverdue;
    }

    public LifecycleTimeout getLastTimeout() {
        return lastTimeout;
    }

    public void setLastTimeout(LifecycleTimeout lastTimeout) {
        this.lastTimeout = lastTimeout;
    }

    /** Epoch milliseconds when this report was collected. */
    public long getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(long collectedAt) {
        this.collectedAt = collectedAt;
    }

    public List<ChannelThreadInfo> getThreads() {
        return threads;
    }

    public void setThreads(List<ChannelThreadInfo> threads) {
        this.threads = threads;
    }
}
