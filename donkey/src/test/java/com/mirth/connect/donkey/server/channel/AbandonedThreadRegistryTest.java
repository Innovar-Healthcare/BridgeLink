/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

/**
 * IRT-2107: an abandoned thread is tracked only while it is still inside the channel. Dispatch threads
 * are frequently pooled or belong to another channel; once the thread restores its original name it
 * has left, and the channel must stop reporting, interrupting or waiting for it.
 */
public class AbandonedThreadRegistryTest {

    private static final String CHANNEL_ID = "irt2107-registry";
    private final CountDownLatch release = new CountDownLatch(1);

    @After
    public void cleanup() {
        release.countDown();
        AbandonedThreadRegistry.clear(CHANNEL_ID);
    }

    private Thread parked(String name) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        return thread;
    }

    @Test
    public void threadIsDroppedOnceItChangesItsName() throws Exception {
        Thread pooled = parked("Channel Dispatch Thread on X (" + CHANNEL_ID + ") < qtp-17");
        AbandonedThreadRegistry.add(CHANNEL_ID, pooled);
        assertEquals(1, AbandonedThreadRegistry.alive(CHANNEL_ID).size());

        // The dispatch finished and the pool thread took its original name back: it has left the channel
        pooled.setName("qtp-17");
        assertTrue(AbandonedThreadRegistry.alive(CHANNEL_ID).isEmpty());
        assertTrue("a pruned thread stays pruned", AbandonedThreadRegistry.alive(CHANNEL_ID).isEmpty());
    }

    @Test
    public void deadThreadIsDroppedAndNeverAdded() throws Exception {
        Thread hook = parked("Channel Halt Hook Thread on X (" + CHANNEL_ID + "), Source (0)");
        AbandonedThreadRegistry.add(CHANNEL_ID, hook);
        assertEquals(1, AbandonedThreadRegistry.alive(CHANNEL_ID).size());

        release.countDown();
        hook.join(10000);
        assertTrue(AbandonedThreadRegistry.alive(CHANNEL_ID).isEmpty());

        AbandonedThreadRegistry.add(CHANNEL_ID, hook);
        assertTrue(AbandonedThreadRegistry.alive(CHANNEL_ID).isEmpty());
    }

    @Test
    public void channelsAreIndependent() throws Exception {
        Thread thread = parked("Source Queue Thread 1 on X (" + CHANNEL_ID + ")");
        AbandonedThreadRegistry.add(CHANNEL_ID, thread);
        assertTrue(AbandonedThreadRegistry.alive("some-other-channel").isEmpty());
        assertEquals(1, AbandonedThreadRegistry.alive(CHANNEL_ID).size());
    }
}
