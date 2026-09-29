/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.util;

import java.util.ArrayList;
import java.util.List;

public class ThreadUtils {
    public static void checkInterruptedStatus() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
    }

    public static void checkInterruptedException(Throwable t) throws InterruptedException {
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            throw (InterruptedException) t;
        }
    }

    /**
     * Converts a grace period into an absolute deadline. A non-positive grace period means "no
     * deadline" and maps to {@link Long#MAX_VALUE}, which every wait helper treats as unbounded.
     */
    public static long deadline(long gracePeriodMillis) {
        if (gracePeriodMillis <= 0) {
            return Long.MAX_VALUE;
        }
        long deadline = System.currentTimeMillis() + gracePeriodMillis;
        // Overflow guard: an absurd grace period must not wrap into the past
        return deadline < 0 ? Long.MAX_VALUE : deadline;
    }

    /**
     * Milliseconds left until the deadline, never negative. An unbounded deadline returns
     * {@link Long#MAX_VALUE}.
     */
    public static long remaining(long deadlineMillis) {
        if (deadlineMillis == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, deadlineMillis - System.currentTimeMillis());
    }

    public static boolean isExpired(long deadlineMillis) {
        return deadlineMillis != Long.MAX_VALUE && System.currentTimeMillis() >= deadlineMillis;
    }

    /**
     * Joins the thread until it dies or the deadline passes. Returns true if the thread is no
     * longer alive. An unbounded deadline joins without a timeout.
     */
    public static boolean joinUntil(Thread thread, long deadlineMillis) throws InterruptedException {
        if (deadlineMillis == Long.MAX_VALUE) {
            thread.join();
            return true;
        }
        long remaining = remaining(deadlineMillis);
        if (remaining > 0) {
            thread.join(remaining);
        }
        return !thread.isAlive();
    }

    /**
     * The top stack frames of a thread as "at ..." strings, at most maxFrames of them. Frames name
     * classes, methods and line numbers only; they never carry message content.
     */
    public static List<String> stackFrames(Thread thread, int maxFrames) {
        List<String> frames = new ArrayList<String>();
        if (thread == null) {
            return frames;
        }
        StackTraceElement[] stackTrace = thread.getStackTrace();
        for (int i = 0; i < stackTrace.length && i < maxFrames; i++) {
            frames.add("at " + stackTrace[i].toString());
        }
        return frames;
    }

    /**
     * A one-line-per-frame description of a thread for log lines and exception messages: the
     * thread name and state, then its top frames.
     */
    public static String describe(Thread thread, int maxFrames) {
        if (thread == null) {
            return "(no thread)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append('"').append(thread.getName()).append("\" [").append(thread.getState()).append(']');
        for (String frame : stackFrames(thread, maxFrames)) {
            builder.append("\n\t").append(frame);
        }
        return builder.toString();
    }
}
