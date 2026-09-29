/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.model.ChannelThreadInfo;
import com.mirth.connect.model.ChannelThreadReport;

/**
 * IRT-2107: the thread collector behind GET /channels/{id}/_threads. Membership is by channel id in
 * the thread name; lock and frame data come from the JVM. The tests park real threads and check
 * that the report says what they are blocked on and nothing more.
 */
public class ChannelThreadDiagnosticsTest {

    private static final String CHANNEL_ID = "3f1e2d4c-irt2107-diag-0001";
    private static final String OTHER_CHANNEL_ID = "3f1e2d4c-irt2107-diag-0002";

    private final CountDownLatch release = new CountDownLatch(1);
    private final Object monitor = new Object();
    private final java.util.List<Thread> started = new java.util.ArrayList<Thread>();

    @After
    public void releaseThreads() throws InterruptedException {
        release.countDown();
        // Join, so a thread from this test cannot still be alive when the next test counts threads
        for (Thread thread : started) {
            thread.join(10000);
        }
    }

    private Channel channel(String channelId) {
        Channel channel = new Channel();
        channel.setChannelId(channelId);
        channel.setName("Diagnostics Channel");
        return channel;
    }

    private Thread park(String name) throws InterruptedException {
        CountDownLatch parked = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            parked.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, name);
        thread.setDaemon(true);
        started.add(thread);
        thread.start();
        assertTrue(parked.await(10, TimeUnit.SECONDS));
        // Give the thread a moment to reach WAITING inside await
        for (int i = 0; i < 100 && thread.getState() != Thread.State.WAITING; i++) {
            Thread.sleep(10);
        }
        return thread;
    }

    @Test
    public void reportsThreadsWhoseNameCarriesTheChannelIdAndNoOthers() throws Exception {
        Thread dispatch = park("Channel Dispatch Thread on Diagnostics Channel (" + CHANNEL_ID + ") < worker-1");
        Thread chain = park("Destination Chain Thread on " + CHANNEL_ID + " < pool-3-thread-2");
        Thread other = park("Source Queue Thread 1 on Other (" + OTHER_CHANNEL_ID + ")");

        ChannelThreadReport report = ChannelThreadDiagnostics.collect(channel(CHANNEL_ID), null, null, 10);

        assertEquals(CHANNEL_ID, report.getChannelId());
        assertEquals("Diagnostics Channel", report.getChannelName());
        assertEquals(DeployedState.STOPPED, report.getState());
        assertFalse(report.isLifecycleOverdue());
        assertNull(report.getLastTimeout());
        assertTrue(report.getCollectedAt() > 0);

        assertEquals(2, report.getThreads().size());
        // Sorted by name: "Channel Dispatch..." before "Destination Chain..."
        ChannelThreadInfo first = report.getThreads().get(0);
        ChannelThreadInfo second = report.getThreads().get(1);
        assertEquals(dispatch.getName(), first.getName());
        assertEquals(dispatch.getId(), first.getId());
        assertEquals(chain.getName(), second.getName());

        assertEquals("WAITING", first.getState());
        assertNotNull("a WAITING thread must say what it waits on", first.getLockName());
        assertFalse(first.getStackTrace().isEmpty());
        assertTrue(first.getStackTrace().size() <= 10);
        assertTrue(first.getStackTrace().get(0), first.getStackTrace().get(0).startsWith("at "));
        assertFalse(first.isAbandoned());
        assertFalse(first.isCancelledScript());
        assertFalse(first.isWaitedOnByLifecycle());

        for (ChannelThreadInfo info : report.getThreads()) {
            assertFalse("thread from another channel leaked into the report", info.getName().contains(OTHER_CHANNEL_ID));
        }
        assertTrue(other.isAlive());
    }

    @Test
    public void reportsMonitorOwnerForABlockedThread() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        Thread owner = new Thread(() -> {
            synchronized (monitor) {
                holding.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "TCP Receiver Thread on Diagnostics Channel (" + CHANNEL_ID + ") < acceptor");
        owner.setDaemon(true);
        started.add(owner);
        owner.start();
        assertTrue(holding.await(10, TimeUnit.SECONDS));

        Thread blocked = new Thread(() -> {
            synchronized (monitor) {
                // never reached until the owner lets go
            }
        }, "Channel Stop Hook Thread on Diagnostics Channel (" + CHANNEL_ID + "), Source (0)");
        blocked.setDaemon(true);
        started.add(blocked);
        blocked.start();
        for (int i = 0; i < 200 && blocked.getState() != Thread.State.BLOCKED; i++) {
            Thread.sleep(10);
        }
        assertEquals(Thread.State.BLOCKED, blocked.getState());

        ChannelThreadReport report = ChannelThreadDiagnostics.collect(channel(CHANNEL_ID), null, null, 5);

        ChannelThreadInfo blockedInfo = null;
        for (ChannelThreadInfo info : report.getThreads()) {
            if (info.getId() == blocked.getId()) {
                blockedInfo = info;
            }
        }
        assertNotNull(blockedInfo);
        assertEquals("BLOCKED", blockedInfo.getState());
        assertNotNull(blockedInfo.getLockName());
        assertEquals(owner.getName(), blockedInfo.getLockOwnerName());
        assertEquals(Long.valueOf(owner.getId()), blockedInfo.getLockOwnerId());
        assertTrue(blockedInfo.getStackTrace().size() <= 5);
    }

    @Test
    public void haltAbandonedThreadsAreReportedAsAbandonedEvenWithoutTheChannelIdInTheirName() throws Exception {
        Thread orphan = park("pool-9-thread-4");

        ChannelThreadReport report = ChannelThreadDiagnostics.collect(channel(CHANNEL_ID), Collections.singleton(orphan), Collections.singleton(orphan), 10);

        assertEquals(1, report.getThreads().size());
        ChannelThreadInfo info = report.getThreads().get(0);
        assertEquals(orphan.getId(), info.getId());
        assertTrue(info.isAbandoned());
    }

    /**
     * The distinction the flag exists to carry (IRT-2107). A stop that ran past its grace period
     * tracks the thread it gave up on, and the endpoint must still show it, but the channel is still
     * STOPPING and the stop still owns that thread. Flagging it abandoned told the operator the
     * channel had already been marked Stopped, at the exact moment they were deciding whether to halt.
     */
    @Test
    public void aThreadTrackedByALifecycleTimeoutIsReportedButNotAbandoned() throws Exception {
        Thread orphan = park("pool-9-thread-5");

        ChannelThreadReport report = ChannelThreadDiagnostics.collect(channel(CHANNEL_ID), Collections.singleton(orphan), null, 10);

        assertEquals(1, report.getThreads().size());
        ChannelThreadInfo info = report.getThreads().get(0);
        assertEquals(orphan.getId(), info.getId());
        assertFalse("a stop timeout must not claim the thread was abandoned", info.isAbandoned());
    }

    /** Only the halt-abandoned thread carries the flag when both kinds are tracked at once. */
    @Test
    public void onlyHaltAbandonedThreadsCarryTheFlagWhenBothAreTracked() throws Exception {
        Thread abandoned = park("pool-9-thread-6");
        Thread waitedOn = park("pool-9-thread-7");

        ChannelThreadReport report = ChannelThreadDiagnostics.collect(channel(CHANNEL_ID), Arrays.asList(abandoned, waitedOn), Collections.singleton(abandoned), 10);

        assertEquals(2, report.getThreads().size());
        for (ChannelThreadInfo info : report.getThreads()) {
            if (info.getId() == abandoned.getId()) {
                assertTrue(info.isAbandoned());
            } else {
                assertFalse(info.isAbandoned());
            }
        }
    }

    @Test
    public void deadExtraThreadsAreSkipped() throws Exception {
        Thread finished = new Thread(() -> {}, "Channel Dispatch Thread on X (" + CHANNEL_ID + ")");
        finished.start();
        finished.join(10000);
        assertFalse(finished.isAlive());

        ChannelThreadReport report = ChannelThreadDiagnostics.collect(channel(CHANNEL_ID), Collections.singleton(finished), Collections.singleton(finished), 10);
        assertTrue(report.getThreads().isEmpty());
    }

    @Test
    public void frameCountIsClampedToTheLimit() throws Exception {
        park("Channel Dispatch Thread on Diagnostics Channel (" + CHANNEL_ID + ")");
        ChannelThreadReport report = ChannelThreadDiagnostics.collect(channel(CHANNEL_ID), null, null, Integer.MAX_VALUE);
        assertEquals(1, report.getThreads().size());
        assertTrue(report.getThreads().get(0).getStackTrace().size() <= ChannelThreadDiagnostics.MAX_FRAMES_LIMIT);

        report = ChannelThreadDiagnostics.collect(channel(CHANNEL_ID), null, null, 0);
        assertTrue(report.getThreads().get(0).getStackTrace().size() <= ChannelThreadDiagnostics.DEFAULT_MAX_FRAMES);
    }

    /**
     * The report is what the Web UI and blctl parse, so its JSON shape is part of the contract: plain
     * fields and a plain array of threads, no serialized-envelope wrapping and no XStream aliases.
     */
    @Test
    public void reportSerializesAsPlainJson() throws Exception {
        park("Channel Dispatch Thread on Diagnostics Channel (" + CHANNEL_ID + ")");
        ChannelThreadReport report = ChannelThreadDiagnostics.collect(channel(CHANNEL_ID), null, null, 3);

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(report));
        assertEquals(CHANNEL_ID, json.get("channelId").asText());
        assertEquals("STOPPED", json.get("state").asText());
        assertTrue(json.get("threads").isArray());
        assertEquals(1, json.get("threads").size());
        JsonNode thread = json.get("threads").get(0);
        assertTrue(thread.get("stackTrace").isArray());
        assertTrue(thread.has("lockName"));
        assertTrue(thread.has("waitedOnByLifecycle"));
        assertTrue(json.get("lastTimeout").isNull());
    }
}
