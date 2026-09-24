/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.ChannelLifecycleTimeout;
import com.mirth.connect.model.ChannelThreadInfo;
import com.mirth.connect.model.ChannelThreadReport;
import com.mirth.connect.server.util.javascript.CancelledScriptThreads;

/**
 * Collects the live threads that belong to a channel for GET /channels/{id}/_threads (IRT-2107).
 *
 * <p>
 * Every donkey thread already carries the channel id in its name: dispatch threads, source and
 * destination queue threads, destination chain and process threads, the recovery task, the engine's
 * own lifecycle task threads, the connector hook threads, the connectors' receiver and acceptor
 * threads and channel script threads all follow "... on <name> (<channelId>) ..." or "... on
 * <channelId>". So membership is name matching, not a registry; the one registry consulted is for
 * channel scripts whose execution was cancelled but whose thread is still running, because those are
 * the threads an operator most needs pointed out.
 *
 * <p>
 * Lock information comes from {@link ThreadMXBean}, which also supplies the stack frames. Frames name
 * classes, methods and line numbers only: nothing here can carry message content.
 */
public final class ChannelThreadDiagnostics {

    public static final int DEFAULT_MAX_FRAMES = 30;
    public static final int MAX_FRAMES_LIMIT = 500;

    private ChannelThreadDiagnostics() {}

    /**
     * @param channel
     *            the deployed channel
     * @param extraThreads
     *            threads to report even if their name does not carry the channel id; may be null
     * @param haltAbandonedThreads
     *            the subset of those a halt actually abandoned, flagged as abandoned; may be null.
     *            A thread an ordinary lifecycle timeout gave up on is reported but not flagged: the
     *            channel keeps its state and the operation still owns the thread, so calling it
     *            abandoned told the operator the channel had already been marked Stopped (IRT-2107).
     * @param maxFrames
     *            stack frames per thread, clamped to [1, {@value #MAX_FRAMES_LIMIT}]
     */
    public static ChannelThreadReport collect(Channel channel, Collection<Thread> extraThreads, Collection<Thread> haltAbandonedThreads, int maxFrames) {
        int frames = Math.max(1, Math.min(maxFrames <= 0 ? DEFAULT_MAX_FRAMES : maxFrames, MAX_FRAMES_LIMIT));
        String channelId = channel.getChannelId();

        ChannelThreadReport report = new ChannelThreadReport();
        report.setChannelId(channelId);
        report.setChannelName(channel.getName());
        report.setState(channel.getCurrentState());
        report.setStateSince(channel.getCurrentStateSince());
        report.setStopGracePeriodMillis(channel.getStopGracePeriodMillis());
        report.setLifecycleOverdue(channel.isLifecycleOverdue());
        report.setCollectedAt(System.currentTimeMillis());

        ChannelLifecycleTimeout lastTimeout = channel.getLastLifecycleTimeout();
        Long waitedOnThreadId = null;
        if (lastTimeout != null) {
            report.setLastTimeout(toModel(lastTimeout));
            waitedOnThreadId = lastTimeout.getThreadId();
        }

        // Candidate threads keyed by id, in a stable insertion order
        Map<Long, Thread> candidates = new LinkedHashMap<Long, Thread>();
        Set<Long> abandonedIds = new java.util.HashSet<Long>();
        Set<Long> cancelledScriptIds = new java.util.HashSet<Long>();

        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (belongsTo(thread, channelId)) {
                candidates.put(thread.getId(), thread);
            }
        }

        if (extraThreads != null) {
            for (Thread thread : extraThreads) {
                if (thread != null && thread.isAlive()) {
                    candidates.put(thread.getId(), thread);
                }
            }
        }

        if (haltAbandonedThreads != null) {
            for (Thread thread : haltAbandonedThreads) {
                if (thread != null && thread.isAlive()) {
                    candidates.put(thread.getId(), thread);
                    abandonedIds.add(thread.getId());
                }
            }
        }

        for (Thread thread : CancelledScriptThreads.runningThreads(channelId)) {
            candidates.put(thread.getId(), thread);
            cancelledScriptIds.add(thread.getId());
        }

        if (candidates.isEmpty()) {
            return report;
        }

        long[] ids = new long[candidates.size()];
        int i = 0;
        for (Long id : candidates.keySet()) {
            ids[i++] = id;
        }

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = threadMXBean.getThreadInfo(ids, frames);

        List<ChannelThreadInfo> threads = new ArrayList<ChannelThreadInfo>();
        for (int j = 0; j < ids.length; j++) {
            Thread thread = candidates.get(ids[j]);
            ThreadInfo info = infos != null && j < infos.length ? infos[j] : null;
            if (info == null && !thread.isAlive()) {
                // Died between the snapshot and the query
                continue;
            }
            threads.add(toModel(thread, info, frames, waitedOnThreadId, abandonedIds, cancelledScriptIds));
        }

        Collections.sort(threads, new Comparator<ChannelThreadInfo>() {
            @Override
            public int compare(ChannelThreadInfo a, ChannelThreadInfo b) {
                int byName = StringUtils.defaultString(a.getName()).compareTo(StringUtils.defaultString(b.getName()));
                return byName != 0 ? byName : Long.compare(a.getId(), b.getId());
            }
        });
        report.setThreads(threads);
        return report;
    }

    /**
     * A thread belongs to a channel when its name carries the channel id. Channel ids are UUIDs, so
     * a plain substring match is unambiguous; the matching is deliberately not anchored to a role
     * prefix so that connector-specific thread names are picked up too.
     */
    static boolean belongsTo(Thread thread, String channelId) {
        return thread != null && StringUtils.isNotEmpty(channelId) && StringUtils.contains(thread.getName(), channelId);
    }

    private static ChannelThreadInfo toModel(Thread thread, ThreadInfo info, int frames, Long waitedOnThreadId, Set<Long> abandonedIds, Set<Long> cancelledScriptIds) {
        ChannelThreadInfo model = new ChannelThreadInfo();
        model.setId(thread.getId());
        model.setName(thread.getName());
        model.setDaemon(thread.isDaemon());
        model.setWaitedOnByLifecycle(waitedOnThreadId != null && waitedOnThreadId.longValue() == thread.getId());
        model.setAbandoned(abandonedIds.contains(thread.getId()));
        model.setCancelledScript(cancelledScriptIds.contains(thread.getId()));

        StackTraceElement[] stackTrace;
        if (info != null) {
            model.setState(info.getThreadState().toString());
            model.setLockName(info.getLockName());
            model.setLockOwnerName(info.getLockOwnerName());
            model.setLockOwnerId(info.getLockOwnerId() >= 0 ? Long.valueOf(info.getLockOwnerId()) : null);
            stackTrace = info.getStackTrace();
        } else {
            model.setState(thread.getState().toString());
            stackTrace = thread.getStackTrace();
        }

        List<String> lines = new ArrayList<String>();
        for (int i = 0; i < stackTrace.length && i < frames; i++) {
            lines.add("at " + stackTrace[i].toString());
        }
        model.setStackTrace(lines);
        return model;
    }

    private static ChannelThreadReport.LifecycleTimeout toModel(ChannelLifecycleTimeout timeout) {
        ChannelThreadReport.LifecycleTimeout model = new ChannelThreadReport.LifecycleTimeout();
        model.setPhase(timeout.getPhase());
        model.setTimestamp(timeout.getTimestamp());
        model.setThreadId(timeout.getThreadId());
        model.setThreadName(timeout.getThreadName());
        model.setThreadState(timeout.getThreadState());
        model.setStackTrace(new ArrayList<String>(timeout.getStackFrames()));
        model.setMessage(timeout.getMessage());
        return model;
    }
}
