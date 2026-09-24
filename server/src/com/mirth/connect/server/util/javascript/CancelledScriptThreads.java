/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util.javascript;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;

/**
 * Channel script tasks whose caller gave up on them but whose thread is still running (IRT-2107).
 *
 * <p>
 * When {@link JavaScriptUtil#execute} is interrupted it cancels the task and tells the Rhino context
 * to stop, which kills a looping script at its next instruction count. A script blocked inside a Java
 * call (a JDBC query, a socket read, a sleep in a library) is not reachable that way: its thread keeps
 * running, and nobody used to know about it. This registry records those tasks so the thread
 * diagnostics endpoint can flag them. It does not try to stop them; that is the follow-up the ticket
 * leaves open.
 *
 * <p>
 * Script threads are pooled, so a registered thread is only reported while the cancelled task is
 * still the thing running on it. Entries prune themselves on every read.
 */
public final class CancelledScriptThreads {

    private static final Set<JavaScriptTask<?>> TASKS = ConcurrentHashMap.newKeySet();

    private CancelledScriptThreads() {}

    /** Records a task that was cancelled while still running. A task that has already finished is ignored. */
    public static void register(JavaScriptTask<?> task) {
        prune();
        if (task != null && task.isRunning()) {
            TASKS.add(task);
        }
    }

    /** Threads still running a cancelled script for the channel. Never null. */
    public static List<Thread> runningThreads(String channelId) {
        prune();
        List<Thread> threads = new ArrayList<Thread>();
        for (JavaScriptTask<?> task : TASKS) {
            if (task.isRunning() && StringUtils.equals(task.getChannelId(), channelId)) {
                Thread thread = task.getExecutingThread();
                if (thread != null && thread.isAlive()) {
                    threads.add(thread);
                }
            }
        }
        return threads;
    }

    /** Number of cancelled tasks still running across all channels. */
    public static int size() {
        prune();
        return TASKS.size();
    }

    private static void prune() {
        TASKS.removeIf(task -> !task.isRunning());
    }
}
