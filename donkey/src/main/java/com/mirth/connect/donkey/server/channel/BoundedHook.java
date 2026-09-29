/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.channel;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.mirth.connect.donkey.util.ThreadUtils;

/**
 * Runs a connector lifecycle hook (stop, halt, undeploy) on its own named thread and waits for it
 * only until a deadline (IRT-2107). A hook that blocks inside an uninterruptible call, such as a
 * JDBC close or a socket read with no timeout, used to pin the channel in STOPPING forever; here
 * the caller gets a {@link LifecycleTimeoutException} naming the hook thread instead, and the
 * thread keeps running until the far end lets go. The thread carries the channel id in its name so
 * the diagnostics endpoint finds it.
 *
 * <p>
 * With an unbounded deadline the hook runs inline on the calling thread, exactly as it did before
 * the grace period existed, so setting the grace period to zero restores the old behaviour.
 */
final class BoundedHook {

    interface Body {
        void run() throws Exception;
    }

    private BoundedHook() {}

    /**
     * @param phase
     *            the lifecycle operation, used in the timeout message ("Stop", "Halt", ...)
     * @param threadName
     *            name for the hook thread; must contain the channel id
     * @param channelId
     *            for the log context of the hook thread
     * @param channelName
     *            for the log context of the hook thread
     * @param deadlineMillis
     *            absolute deadline, or {@link Long#MAX_VALUE} for no deadline
     * @throws LifecycleTimeoutException
     *             if the deadline passes before the hook returns; the hook thread is still running
     * @throws InterruptedException
     *             if the caller is interrupted while waiting; the hook thread is interrupted too
     * @throws Throwable
     *             whatever the hook itself threw
     */
    static void run(String phase, String threadName, String channelId, String channelName, long deadlineMillis, Body body) throws Throwable {
        if (deadlineMillis == Long.MAX_VALUE) {
            body.run();
            return;
        }

        FutureTask<Void> task = new FutureTask<Void>(() -> {
            try (LogContext.Scope channelScope = LogContext.channel(channelId, channelName)) {
                body.run();
            }
            return null;
        });
        /*
         * Explicitly not a daemon (Thread inherits the flag from its creator, so say so): a hook that
         * is still running holds resources the connector owns, and a hanging stop kept the JVM alive
         * before this class existed too. Making it a daemon would change shutdown semantics, which
         * is a separate decision.
         */
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(false);
        thread.start();

        try {
            task.get(ThreadUtils.remaining(deadlineMillis), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new LifecycleTimeoutException(phase, thread, "the connector hook did not return");
        } catch (InterruptedException e) {
            // Halt interrupted the caller: pass it on to the hook so it can abort as it did before
            task.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
}
