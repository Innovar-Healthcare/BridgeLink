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
 *
 * <p>
 * The name test has one blind spot, and it is deliberate rather than overlooked: a pooled thread that
 * leaves the channel and is later handed work on the <em>same</em> channel again is renamed to exactly
 * the string it was recorded under, so if no read happened in between the old entry is indistinguishable
 * from a live one. A halt orphan that outlives a restart and later gets stuck again in a plain stop can
 * therefore still be reported as abandoned. Closing that needs the fact the registry does not have --
 * that the thread is starting fresh work -- which is known only at the dispatch site.
 *
 * <p>
 * Each entry records <em>which kind of operation</em> gave up on it. Every tracked thread is
 * interrupted by the next halt and waited for by the next start, whatever put it here, but only a
 * thread a halt actually abandoned is reported as {@code abandoned} to an operator: a halt leaves the
 * channel STOPPED with the thread running loose, and a stop timeout does not. Conflating the two told
 * the operator the channel had been marked Stopped at the exact moment they were deciding whether to
 * halt it.
 */
public final class AbandonedThreadRegistry {

    /** Which kind of lifecycle operation gave up on a thread. */
    public enum Source {
        /**
         * A stop, start, deploy, undeploy, pause, resume or lock acquisition exceeded its grace
         * period. The channel keeps its current state and the operation is still the thread's owner.
         */
        LIFECYCLE_TIMEOUT,

        /**
         * A halt gave up and marked the channel STOPPED anyway, leaving the thread running with
         * nothing waiting for it. This is the one an operator is shown as {@code abandoned}.
         */
        HALT
    }

    private static final class Entry {
        private final String name;
        private Source source;

        private Entry(String name, Source source) {
            this.name = name;
            this.source = source;
        }
    }

    private static final Map<String, Map<Thread, Entry>> THREADS = new ConcurrentHashMap<String, Map<Thread, Entry>>();

    private AbandonedThreadRegistry() {}

    /**
     * Records a thread the given kind of operation gave up on. {@link Source#HALT} is sticky: a
     * thread a halt abandoned stays abandoned even if a later timeout records it again, because the
     * halt outcome is the one the operator needs to keep seeing.
     */
    public static void add(String channelId, Thread thread, Source source) {
        if (channelId == null || thread == null || source == null || !thread.isAlive()) {
            return;
        }
        /*
         * Both the add and the prune run inside the outer map's per-key lock rather than under a
         * monitor on the inner map. Guarding the inner map instead loses entries: a reader that prunes
         * the last entry drops the whole inner map from THREADS, and a writer that already held a
         * reference to it then writes into a map nothing can reach -- so a genuinely abandoned thread
         * is never reported, never interrupted by the next halt and never waited for by the next start.
         */
        THREADS.compute(channelId, (id, threads) -> {
            if (threads == null) {
                threads = new HashMap<Thread, Entry>();
            }
            String name = thread.getName();
            Entry existing = threads.get(thread);
            if (existing != null && existing.name.equals(name)) {
                if (source == Source.HALT) {
                    existing.source = Source.HALT;
                }
            } else {
                threads.put(thread, new Entry(name, source));
            }
            return threads;
        });
    }

    /**
     * Every tracked thread for the channel that is still alive and still carries the name it was
     * recorded under, whatever recorded it. This is the set the next halt interrupts and the next
     * start waits for. Never null.
     */
    public static Set<Thread> alive(String channelId) {
        return alive(channelId, null);
    }

    /**
     * The tracked threads a halt abandoned: still alive, still under their recorded name, and
     * recorded by {@link Source#HALT}. This is the set reported to an operator as {@code abandoned}.
     * Never null.
     */
    public static Set<Thread> abandonedByHalt(String channelId) {
        return alive(channelId, Source.HALT);
    }

    /** Prunes dead and renamed entries, returning those still alive that match {@code source} (null matches any). */
    private static Set<Thread> alive(String channelId, Source source) {
        Set<Thread> result = new HashSet<Thread>();
        if (channelId == null) {
            return result;
        }
        // Under the same per-key lock as add(), so pruning the map empty cannot race a concurrent add
        THREADS.computeIfPresent(channelId, (id, threads) -> {
            for (Iterator<Map.Entry<Thread, Entry>> it = threads.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Thread, Entry> entry = it.next();
                Thread thread = entry.getKey();
                Entry tracked = entry.getValue();
                if (thread.isAlive() && tracked.name.equals(thread.getName())) {
                    if (source == null || tracked.source == source) {
                        result.add(thread);
                    }
                } else {
                    it.remove();
                }
            }
            return threads.isEmpty() ? null : threads;
        });
        return result;
    }

    /** Forgets every thread recorded for the channel. For tests. */
    public static void clear(String channelId) {
        if (channelId != null) {
            THREADS.remove(channelId);
        }
    }
}
