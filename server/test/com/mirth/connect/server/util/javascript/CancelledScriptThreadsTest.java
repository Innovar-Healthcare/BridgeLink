/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util.javascript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

/**
 * IRT-2107: a cancelled script whose thread is still running must be visible to the diagnostics
 * endpoint, and must stop being reported the moment the task finishes, because script threads are
 * pooled and the next task on that thread may belong to another channel.
 */
public class CancelledScriptThreadsTest {

    private static final String CHANNEL_ID = "irt2107-script-channel";

    private final CountDownLatch release = new CountDownLatch(1);

    /** A task that behaves like a script blocked inside a Java call: it ignores the context flag. */
    private class BlockingTask extends JavaScriptTask<Object> {
        private final CountDownLatch entered = new CountDownLatch(1);

        BlockingTask(String channelId) {
            super(null, "Test", channelId, "Script Channel");
        }

        @Override
        public Object doCall() throws Exception {
            entered.countDown();
            release.await();
            return null;
        }
    }

    @After
    public void releaseThreads() throws Exception {
        release.countDown();
    }

    @Test
    public void runningCancelledTaskIsReportedForItsChannelOnly() throws Exception {
        BlockingTask task = new BlockingTask(CHANNEL_ID);
        assertFalse(task.isRunning());
        assertNull(task.getExecutingThread());
        assertEquals(CHANNEL_ID, task.getChannelId());

        Thread thread = new Thread(() -> {
            try {
                task.call();
            } catch (Exception e) {
                // released by the test
            }
        }, "pool-1-thread-1");
        thread.setDaemon(true);
        thread.start();
        assertTrue(task.entered.await(10, TimeUnit.SECONDS));

        assertTrue(task.isRunning());
        assertSame(thread, task.getExecutingThread());
        assertTrue("the task must rename its thread with the channel id while it runs", thread.getName().contains(CHANNEL_ID));

        CancelledScriptThreads.register(task);
        List<Thread> threads = CancelledScriptThreads.runningThreads(CHANNEL_ID);
        assertEquals(1, threads.size());
        assertSame(thread, threads.get(0));
        assertTrue(CancelledScriptThreads.runningThreads("some-other-channel").isEmpty());

        release.countDown();
        thread.join(10000);
        assertFalse(thread.isAlive());

        assertFalse(task.isRunning());
        assertNull(task.getExecutingThread());
        assertTrue("a finished task must be pruned", CancelledScriptThreads.runningThreads(CHANNEL_ID).isEmpty());
        assertEquals("pool-1-thread-1", thread.getName());
    }

    @Test
    public void finishedTaskIsNeverRegistered() throws Exception {
        BlockingTask task = new BlockingTask(CHANNEL_ID);
        release.countDown();
        task.call();
        int before = CancelledScriptThreads.size();
        CancelledScriptThreads.register(task);
        assertEquals(before, CancelledScriptThreads.size());
    }
}
