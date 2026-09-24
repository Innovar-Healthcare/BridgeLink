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

import com.mirth.connect.donkey.server.Constants;
import com.mirth.connect.donkey.util.ThreadUtils;

/**
 * A channel lifecycle operation (stop, halt, undeploy) ran past its deadline while waiting for a
 * thread that did not finish (IRT-2107). Carries the thread it was waiting on, when one is known,
 * so the caller can name it and record it for the diagnostics endpoint. The thread reference is
 * transient: the exception may be serialized into an event or an API error, and a Thread is not.
 */
public class LifecycleTimeoutException extends Exception {

    private final String phase;
    private final transient Thread thread;
    private final String threadName;
    private final String threadState;
    private final List<String> stackFrames;

    public LifecycleTimeoutException(String phase, Thread thread, String detail) {
        super(buildMessage(phase, thread, detail));
        this.phase = phase;
        this.thread = thread;
        this.threadName = thread != null ? thread.getName() : null;
        this.threadState = thread != null ? thread.getState().toString() : null;
        this.stackFrames = Collections.unmodifiableList(ThreadUtils.stackFrames(thread, Constants.LIFECYCLE_TIMEOUT_STACK_FRAMES));
    }

    /*
     * "gave up waiting" rather than "exceeded its grace period": only stop has a grace period. Halt
     * uses its own short wind-down interval, and an operation that finds the lock held by a thread a
     * halt abandoned gives up at once with no timer at all (IRT-2107).
     */
    private static String buildMessage(String phase, Thread thread, String detail) {
        StringBuilder builder = new StringBuilder(phase).append(" gave up waiting");
        if (detail != null) {
            builder.append(": ").append(detail);
        }
        if (thread != null) {
            builder.append(". Waiting on thread ").append(ThreadUtils.describe(thread, Constants.LIFECYCLE_TIMEOUT_STACK_FRAMES));
        }
        return builder.toString();
    }

    /** Which lifecycle operation timed out, for example "Stop" or "Halt". */
    public String getPhase() {
        return phase;
    }

    /** The thread the operation was waiting on, or null when no single thread could be named. */
    public Thread getThread() {
        return thread;
    }

    public String getThreadName() {
        return threadName;
    }

    public String getThreadState() {
        return threadState;
    }

    /** Top stack frames of the stuck thread at the moment of the timeout. Never message content. */
    public List<String> getStackFrames() {
        return stackFrames;
    }
}
