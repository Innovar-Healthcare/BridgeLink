/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Test;

import com.mirth.connect.server.channel.ChannelFuture;
import com.mirth.connect.server.channel.ErrorTaskHandler;

/**
 * IRT-2107: the REST thread waiting on a halt must give up after a bound and tell the caller, instead
 * of pinning a Jetty thread for as long as the halt task is wedged. Only waitForTasks is exercised,
 * through a subclass, so no engine or database is needed.
 */
public class DonkeyEngineControllerWaitForTasksTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CountDownLatch release = new CountDownLatch(1);

    private static class TestController extends DonkeyEngineController {
        TestController() {
            super();
        }

        void waitBounded(List<ChannelFuture> futures, long timeoutMillis) {
            waitForTasks(futures, timeoutMillis);
        }
    }

    @After
    public void tearDown() {
        release.countDown();
        executor.shutdownNow();
    }

    private ChannelFuture wedgedTask(String channelId, ErrorTaskHandler handler) {
        Future<?> future = executor.submit(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return new ChannelFuture(channelId, null, future, handler);
    }

    @Test(timeout = 30000)
    public void boundedWaitReportsTimeoutAndReturns() throws Exception {
        ErrorTaskHandler handler = new ErrorTaskHandler();
        List<ChannelFuture> futures = new ArrayList<ChannelFuture>();
        futures.add(wedgedTask("wedged-channel", handler));

        long started = System.currentTimeMillis();
        new TestController().waitBounded(futures, 300);
        long elapsed = System.currentTimeMillis() - started;

        assertTrue("wait took " + elapsed + " ms", elapsed < 10000);
        assertTrue("the caller must be told the task did not finish", handler.isErrored());
        assertTrue(handler.getError() instanceof TimeoutException);
        assertTrue(handler.getError().getMessage(), handler.getError().getMessage().contains("wedged-channel"));
        assertTrue(handler.getError().getMessage().contains("_threads"));
        assertFalse("the task itself is left running, not cancelled", release.getCount() == 0);
    }

    @Test(timeout = 30000)
    public void boundedWaitReturnsPromptlyWhenTasksFinish() throws Exception {
        ErrorTaskHandler handler = new ErrorTaskHandler();
        List<ChannelFuture> futures = new ArrayList<ChannelFuture>();
        futures.add(wedgedTask("quick-channel", handler));
        release.countDown();

        new TestController().waitBounded(futures, 30000);
        assertFalse(handler.isErrored());
        assertEquals(null, handler.getError());
    }
}
