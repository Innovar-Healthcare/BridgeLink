/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.channel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Threads a channel lifecycle operation gave up waiting on, keyed by channel id (IRT-2107). A forced
 * halt marks the channel STOPPED while these are still running, and a redeploy then builds a fresh
 * {@link Channel} instance; the registry outlives the instance so the diagnostics endpoint keeps
 * showing the orphans and the next halt keeps interrupting them. Threads are strong references on
 * purpose: one that never finishes is leaked whether or not it is listed here, and listing it is the
 * point.
 *
 * <p>
 * A dispatch thread is often not the channel's own: a Jetty pool thread, a JMS session thread, or the
 * upstream channel's queue thread inside a Channel Reader. Every channel-owned rename restores the
 * original name on the way out, so the name a thread carried when it was abandoned is the test of
 * whether it is still inside this channel: an entry whose thread has died or has changed its name is
 * pruned on every read, and the thread is never interrupted or waited for again.
 */
public final class AbandonedThreadRegistry {

    private static final Map<String, Map<Thread, String>> THREADS = new ConcurrentHashMap<String, Map<Thread, String>>();

    private AbandonedThreadRegistry() {}

    public static void add(String channelId, Thread thread) {
        if (channelId == null || thread == null || !thread.isAlive()) {
            return;
        }
        Map<Thread, String> threads = THREADS.computeIfAbsent(channelId, id -> new HashMap<Thread, String>());
        synchronized (threads) {
            threads.put(thread, thread.getName());
        }
    }

    /**
     * The abandoned threads for the channel that are still alive and still carry the name they were
     * abandoned under. Never null.
     */
    public static Set<Thread> alive(String channelId) {
        Set<Thread> result = new HashSet<Thread>();
        if (channelId == null) {
            return result;
        }
        Map<Thread, String> threads = THREADS.get(channelId);
        if (threads == null) {
            return result;
        }
        synchronized (threads) {
            for (Iterator<Map.Entry<Thread, String>> it = threads.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Thread, String> entry = it.next();
                Thread thread = entry.getKey();
                if (thread.isAlive() && entry.getValue().equals(thread.getName())) {
                    result.add(thread);
                } else {
                    it.remove();
                }
            }
            if (threads.isEmpty()) {
                THREADS.remove(channelId, threads);
            }
        }
        return result;
    }

    /** Forgets every thread recorded for the channel. For tests. */
    public static void clear(String channelId) {
        if (channelId != null) {
            THREADS.remove(channelId);
        }
    }
}
