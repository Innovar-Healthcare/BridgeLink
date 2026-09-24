/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.channel;

import java.util.Collections;
import java.util.List;

/**
 * Immutable record of the most recent lifecycle timeout on a channel: what the operation was
 * waiting on when its grace period ran out (IRT-2107). Kept on the {@link Channel} so the thread
 * diagnostics endpoint can show an operator what a channel stuck in STOPPING is stuck on. Holds
 * only thread metadata and stack frames, never message content.
 */
public class ChannelLifecycleTimeout {

    private final String phase;
    private final long timestamp;
    private final Long threadId;
    private final String threadName;
    private final String threadState;
    private final List<String> stackFrames;
    private final String message;

    public ChannelLifecycleTimeout(LifecycleTimeoutException e) {
        this.phase = e.getPhase();
        this.timestamp = System.currentTimeMillis();
        Thread thread = e.getThread();
        this.threadId = thread != null ? Long.valueOf(thread.getId()) : null;
        this.threadName = e.getThreadName();
        this.threadState = e.getThreadState();
        this.stackFrames = e.getStackFrames() != null ? e.getStackFrames() : Collections.<String> emptyList();
        this.message = e.getMessage();
    }

    public String getPhase() {
        return phase;
    }

    /** Epoch milliseconds when the timeout was detected. */
    public long getTimestamp() {
        return timestamp;
    }

    public Long getThreadId() {
        return threadId;
    }

    public String getThreadName() {
        return threadName;
    }

    public String getThreadState() {
        return threadState;
    }

    public List<String> getStackFrames() {
        return stackFrames;
    }

    public String getMessage() {
        return message;
    }
}
