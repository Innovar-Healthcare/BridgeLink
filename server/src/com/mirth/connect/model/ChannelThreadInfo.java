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

/**
 * One live thread that belongs to a deployed channel, as served by GET /channels/{id}/_threads
 * (IRT-2107). Carries the thread's identity, its state, the lock it is blocked on and its top stack
 * frames. Stack frames name classes, methods and line numbers only: never message content, never
 * connector settings.
 */
public class ChannelThreadInfo implements Serializable {

    private long id;
    private String name;
    private String state;
    private boolean daemon;
    private String lockName;
    private String lockOwnerName;
    private Long lockOwnerId;
    private boolean waitedOnByLifecycle;
    private boolean cancelledScript;
    private boolean abandoned;
    private List<String> stackTrace = new ArrayList<String>();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** {@link Thread.State} name: RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, ... */
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean isDaemon() {
        return daemon;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    /** The monitor or synchronizer the thread is blocked on or waiting for, or null when it is running. */
    public String getLockName() {
        return lockName;
    }

    public void setLockName(String lockName) {
        this.lockName = lockName;
    }

    /** The thread that owns that lock, when the JVM knows it. */
    public String getLockOwnerName() {
        return lockOwnerName;
    }

    public void setLockOwnerName(String lockOwnerName) {
        this.lockOwnerName = lockOwnerName;
    }

    public Long getLockOwnerId() {
        return lockOwnerId;
    }

    public void setLockOwnerId(Long lockOwnerId) {
        this.lockOwnerId = lockOwnerId;
    }

    /** True when the channel's most recent stop or halt timed out waiting on this very thread. */
    public boolean isWaitedOnByLifecycle() {
        return waitedOnByLifecycle;
    }

    public void setWaitedOnByLifecycle(boolean waitedOnByLifecycle) {
        this.waitedOnByLifecycle = waitedOnByLifecycle;
    }

    /**
     * True for a channel script whose execution was cancelled (the channel was halted or the script
     * was interrupted) but whose thread is still running because it is blocked inside a Java call the
     * script engine cannot reach.
     */
    public boolean isCancelledScript() {
        return cancelledScript;
    }

    public void setCancelledScript(boolean cancelledScript) {
        this.cancelledScript = cancelledScript;
    }

    /** True when a forced halt gave up waiting on this thread and marked the channel stopped anyway. */
    public boolean isAbandoned() {
        return abandoned;
    }

    public void setAbandoned(boolean abandoned) {
        this.abandoned = abandoned;
    }

    /** Top stack frames, innermost first, each as "at class.method(File.java:line)". */
    public List<String> getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(List<String> stackTrace) {
        this.stackTrace = stackTrace;
    }
}
