/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.DonkeyConfiguration;
import com.mirth.connect.donkey.server.DonkeyConnectionPools;
import com.mirth.connect.donkey.server.StartException;
import com.mirth.connect.donkey.server.StartException;
import com.mirth.connect.donkey.server.StopException;
import com.mirth.connect.donkey.server.UndeployException;
import com.mirth.connect.donkey.server.channel.AbandonedThreadRegistry;
import com.mirth.connect.donkey.server.channel.DefaultChannelProcessLock;
import com.mirth.connect.donkey.server.channel.ChannelLifecycleTimeout;
import com.mirth.connect.donkey.test.util.TestChannel;
import com.mirth.connect.donkey.test.util.TestDestinationConnector;
import com.mirth.connect.donkey.test.util.TestSourceConnector;
import com.mirth.connect.donkey.test.util.TestUtils;

/**
 * IRT-2107: a channel that will not stop must still reach a terminal decision in bounded time.
 * These tests wedge a lifecycle step the way a real connector does (a hook or a send blocked in a
 * call that ignores interrupts) and check that the operation gives up at its grace period, names the
 * stuck thread, and leaves the channel in a state the operator can act on.
 *
 * <p>
 * Each test uses its own channel id so a thread left wedged by a regression cannot leak into the
 * next test, and every gate is released in a finally for the same reason.
 *
 * <p>
 * Message-path invariants touched here: none of these paths drop, duplicate or reorder a message.
 * A stop that times out leaves the channel STOPPING with every persisted message where it was; the
 * source keeps being told "not persisted" only for the dispatch it interrupted, exactly as before.
 */
public class ChannelLifecycleTests {
    private static final String serverId = TestUtils.DEFAULT_SERVER_ID;
    private static final String testMessage = TestUtils.TEST_HL7_MESSAGE;

    /** Short enough to keep the suite fast, long enough that a healthy stop never trips it. */
    private static final long GRACE_MILLIS = 750;
    /** Upper bound on how long a bounded operation may take before the test calls it unbounded. */
    private static final long BOUND_MILLIS = 15000;

    @BeforeClass
    final public static void beforeClass() throws StartException {
        Donkey donkey = Donkey.getInstance();
        DonkeyConfiguration config = TestUtils.getDonkeyTestConfiguration();

        TestUtils.shutdownConnectionPools();
        DonkeyConnectionPools.getInstance().init(config.getDonkeyProperties());

        donkey.startEngine(config);
    }

    @AfterClass
    final public static void afterClass() throws StartException {
        Donkey.getInstance().stopEngine();
        TestUtils.shutdownConnectionPools();
    }

    /**
     * A source connector whose onStop never returns is the monitor-blocked case: stop used to hang
     * inside the synchronized method and halt then blocked behind it. Now stop fails at the grace
     * period with a StopException naming the hook thread, and the channel stays STOPPING so the
     * operator can choose halt.
     */
    @Test(timeout = 60000)
    public final void testStopFailsAtGracePeriodWhenSourceOnStopBlocks() throws Exception {
        String channelId = "lifecyclestoponstop";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId, true, 1, 1);
        channel.setName(channelId);
        channel.setStopGracePeriodMillis(GRACE_MILLIS);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();
        CountDownLatch gate = new CountDownLatch(1);

        try {
            channel.deploy();
            channel.start(null);
            sourceConnector.readTestMessage(testMessage);

            sourceConnector.blockOnStop(gate);

            long started = System.currentTimeMillis();
            StopException stopException = null;
            try {
                channel.stop();
                fail("stop returned although onStop never did");
            } catch (StopException e) {
                stopException = e;
            }
            long elapsed = System.currentTimeMillis() - started;

            assertTrue("stop took " + elapsed + " ms, which is not bounded by the grace period", elapsed < BOUND_MILLIS);
            assertTrue("stop returned before the grace period elapsed", elapsed >= GRACE_MILLIS);
            assertTrue("the hook was never entered, so the timeout fired for the wrong reason", sourceConnector.getOnStopEntered().await(0, TimeUnit.SECONDS));

            String message = stopException.getMessage();
            assertTrue(message, message.contains("grace period"));
            assertTrue("the exception must name the stuck thread: " + message, message.contains("Channel Stop Hook Thread on " + channelId + " (" + channelId + "), Source (0)"));
            assertTrue("the exception must carry stack frames: " + message, message.contains("\tat "));
            assertFalse("no message content may leak into the exception", message.contains("MSH|"));

            assertEquals("channel must stay STOPPING so the operator can decide", DeployedState.STOPPING, channel.getCurrentState());
            assertTrue("dashboard signal must be raised once the grace period has passed", channel.isLifecycleOverdue());

            ChannelLifecycleTimeout timeout = channel.getLastLifecycleTimeout();
            assertNotNull(timeout);
            assertEquals("Stop", timeout.getPhase());
            assertTrue(timeout.getThreadName(), timeout.getThreadName().startsWith("Channel Stop Hook Thread on " + channelId));
            assertFalse(timeout.getStackFrames().isEmpty());

            // Release the hook; the abandoned stop finishes on its own thread and halt brings it home
            gate.countDown();
            channel.halt();
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
            assertFalse(channel.isLifecycleOverdue());
        } finally {
            gate.countDown();
            if (channel.getCurrentState() != DeployedState.STOPPED) {
                channel.halt();
            }
            channel.undeploy();
        }
    }

    /**
     * A dispatch thread parked inside a destination send is the "TCP Sender to a black hole" case.
     * Stop drains dispatch threads before it stops the destinations; that drain now gives up at the
     * grace period and names the dispatch thread it was waiting on.
     */
    @Test(timeout = 60000)
    public final void testStopFailsAtGracePeriodWhenDispatchThreadBlocksInSend() throws Exception {
        String channelId = "lifecyclestopdispatch";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId, true, 1, 1);
        channel.setName(channelId);
        channel.setStopGracePeriodMillis(GRACE_MILLIS);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();
        TestDestinationConnector destinationConnector = (TestDestinationConnector) channel.getDestinationConnector(1);
        CountDownLatch gate = new CountDownLatch(1);
        Thread dispatcher = null;

        try {
            channel.deploy();
            channel.start(null);

            destinationConnector.blockSend(gate);

            final AtomicReference<Throwable> dispatchError = new AtomicReference<Throwable>();
            dispatcher = new Thread(() -> {
                try {
                    sourceConnector.readTestMessage(testMessage);
                } catch (Throwable t) {
                    dispatchError.set(t);
                }
            }, "IRT-2107 blocked dispatcher");
            dispatcher.start();
            assertTrue("send was never entered", destinationConnector.getSendEntered().await(30, TimeUnit.SECONDS));

            long started = System.currentTimeMillis();
            try {
                channel.stop();
                fail("stop returned although a dispatch thread is still inside send");
            } catch (StopException e) {
                String message = e.getMessage();
                assertTrue(message, message.contains("grace period"));
                /*
                 * The dispatch thread renames itself as it moves through the chain ("Channel Dispatch
                 * Thread" then "<connector> Process Thread"), so assert on the channel id it always
                 * carries and on the frame that shows where it is parked.
                 */
                assertTrue("the exception must name the dispatch thread: " + message, message.contains("Thread on " + channelId + " (" + channelId + ")"));
                assertTrue(message, message.contains("dispatch thread(s) still processing"));
                assertTrue("the exception must show the blocked send: " + message, message.contains("TestDestinationConnector.send"));
            }
            long elapsed = System.currentTimeMillis() - started;
            assertTrue("stop took " + elapsed + " ms", elapsed < BOUND_MILLIS);

            assertEquals(DeployedState.STOPPING, channel.getCurrentState());
            assertNotNull(channel.getLastLifecycleTimeout());

            // Let the send finish: the message completes normally, then the channel can stop for real
            gate.countDown();
            dispatcher.join(30000);
            assertFalse("dispatcher did not finish after the gate opened", dispatcher.isAlive());
            assertNull("the released dispatch must complete normally", dispatchError.get());
            assertEquals("the message must be delivered exactly once", 1, destinationConnector.getMessageIds().size());

            channel.stop();
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
        } finally {
            gate.countDown();
            if (dispatcher != null) {
                dispatcher.join(30000);
            }
            if (channel.getCurrentState() != DeployedState.STOPPED) {
                channel.halt();
            }
            channel.undeploy();
        }
    }

    /**
     * The grace period must never trip a healthy stop, and a stop that completes clears the overdue
     * signal.
     */
    @Test(timeout = 60000)
    public final void testHealthyStopCompletesInsideGracePeriod() throws Exception {
        String channelId = "lifecyclestophealthy";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId, true, 1, 1);
        channel.setName(channelId);
        channel.setStopGracePeriodMillis(GRACE_MILLIS);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        try {
            channel.deploy();
            channel.start(null);
            for (int i = 0; i < 5; i++) {
                sourceConnector.readTestMessage(testMessage);
            }

            channel.stop();

            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
            assertNull(channel.getLastLifecycleTimeout());
            assertFalse(channel.isLifecycleOverdue());
            assertEquals(5, ((TestDestinationConnector) channel.getDestinationConnector(1)).getMessageIds().size());
        } finally {
            channel.undeploy();
        }
    }

    /**
     * A grace period of zero is the pre-IRT-2107 behaviour: stop waits without bound and the hook runs
     * on the stopping thread itself. Operators who need the old semantics can have them back.
     */
    @Test(timeout = 60000)
    public final void testGracePeriodZeroWaitsWithoutBound() throws Exception {
        String channelId = "lifecyclestopunbounded";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId, true, 1, 1);
        channel.setName(channelId);
        channel.setStopGracePeriodMillis(0);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();
        CountDownLatch gate = new CountDownLatch(1);
        Thread stopper = null;

        try {
            channel.deploy();
            channel.start(null);
            sourceConnector.blockOnStop(gate);

            final AtomicReference<Throwable> stopError = new AtomicReference<Throwable>();
            stopper = new Thread(() -> {
                try {
                    channel.stop();
                } catch (Throwable t) {
                    stopError.set(t);
                }
            }, "IRT-2107 unbounded stopper");
            stopper.start();

            assertTrue(sourceConnector.getOnStopEntered().await(30, TimeUnit.SECONDS));
            stopper.join(GRACE_MILLIS * 3);
            assertTrue("with the grace period disabled, stop must still be waiting", stopper.isAlive());
            assertEquals(DeployedState.STOPPING, channel.getCurrentState());
            assertFalse("nothing is overdue when the grace period is disabled", channel.isLifecycleOverdue());

            gate.countDown();
            stopper.join(30000);
            assertFalse(stopper.isAlive());
            assertNull(stopError.get());
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
        } finally {
            gate.countDown();
            if (stopper != null) {
                stopper.join(30000);
            }
            if (channel.getCurrentState() != DeployedState.STOPPED) {
                channel.halt();
            }
            channel.undeploy();
        }
    }

    /**
     * After a stop has timed out the channel is STOPPING with threads still running. Undeploying it
     * anyway (which is what a redeploy does first) would tear the channel down under a live dispatch
     * thread and let the fresh instance's recovery send the same message again. Undeploy therefore
     * refuses until the operator halts, and the refusal says so.
     *
     * <p>
     * Invariant: prevents a duplicate. Nothing is dropped; the in-flight message completes on its own.
     */
    @Test(timeout = 60000)
    public final void testUndeployRefusedWhileStopHasTimedOut() throws Exception {
        String channelId = "lifecycleundeployrefused";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId, true, 1, 1);
        channel.setName(channelId);
        channel.setStopGracePeriodMillis(GRACE_MILLIS);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();
        TestDestinationConnector destinationConnector = (TestDestinationConnector) channel.getDestinationConnector(1);
        CountDownLatch gate = new CountDownLatch(1);
        Thread dispatcher = null;

        try {
            channel.deploy();
            channel.start(null);
            destinationConnector.blockSend(gate);

            dispatcher = new Thread(() -> {
                try {
                    sourceConnector.readTestMessage(testMessage);
                } catch (Throwable t) {
                    // released by the test
                }
            }, "IRT-2107 blocked dispatcher");
            dispatcher.start();
            assertTrue(destinationConnector.getSendEntered().await(30, TimeUnit.SECONDS));

            try {
                channel.stop();
                fail("stop returned although a dispatch thread is still inside send");
            } catch (StopException expected) {
            }
            assertEquals(DeployedState.STOPPING, channel.getCurrentState());
            assertEquals("the dispatch thread the stop gave up on must be tracked", 1, channel.getAbandonedLifecycleThreads().size());
            // Calvin's case (IRT-2107): a stop that timed out has abandoned nothing. The channel is
            // still STOPPING and the stop still owns that thread, so the operator must not be told it
            // was left running loose -- that is what a halt does, and it is how they tell the two apart.
            assertTrue("a stop timeout must not report the thread as abandoned by a halt", channel.getHaltAbandonedThreads().isEmpty());

            try {
                channel.undeploy();
                fail("undeploy must refuse while the stop has timed out");
            } catch (UndeployException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("Halt the channel first"));
                assertTrue(e.getMessage(), e.getMessage().contains("timed out"));
            }
            assertEquals("a refused undeploy must not change the state", DeployedState.STOPPING, channel.getCurrentState());
            assertTrue("a refused undeploy must leave the connectors deployed", destinationConnector.isDeployed());

            // Halt interrupts the abandoned dispatch thread; the fake ignores interrupts, so release it too
            gate.countDown();
            channel.halt();
            dispatcher.join(30000);
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
            assertTrue(channel.getAbandonedLifecycleThreads().isEmpty());
            assertEquals("the message must be delivered exactly once", 1, destinationConnector.getMessageIds().size());

            channel.undeploy();
            assertFalse(destinationConnector.isDeployed());
        } finally {
            gate.countDown();
            if (dispatcher != null) {
                dispatcher.join(30000);
            }
            if (channel.getCurrentState() != DeployedState.STOPPED) {
                channel.halt();
            }
            if (destinationConnector.isDeployed()) {
                channel.undeploy();
            }
        }
    }

    /**
     * Halt must reach the hook thread a timed-out stop abandoned, since the stop task's own interrupt
     * no longer does. With a hook that honours interrupts the halt alone brings the thread home; the
     * next start then finds nothing to wait for.
     */
    @Test(timeout = 60000)
    public final void testHaltInterruptsAbandonedHookThreadAndStartFindsItGone() throws Exception {
        String channelId = "lifecycleabandonedhook";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId, true, 1, 1);
        channel.setName(channelId);
        channel.setStopGracePeriodMillis(GRACE_MILLIS);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();
        CountDownLatch gate = new CountDownLatch(1);

        try {
            channel.deploy();
            channel.start(null);
            sourceConnector.blockOnStopInterruptibly(gate);

            try {
                channel.stop();
                fail("stop returned although onStop never did");
            } catch (StopException expected) {
            }
            Thread hook = channel.getAbandonedLifecycleThreads().iterator().next();
            assertTrue(hook.getName(), hook.getName().startsWith("Channel Stop Hook Thread on " + channelId));

            // The gate stays closed: only halt's interrupt can free the hook
            channel.halt();
            hook.join(30000);
            assertFalse("halt must interrupt the abandoned hook thread", hook.isAlive());
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
            assertTrue(channel.getAbandonedLifecycleThreads().isEmpty());

            long started = System.currentTimeMillis();
            channel.start(null);
            assertTrue("start must not wait a grace period when no abandoned thread is alive", System.currentTimeMillis() - started < GRACE_MILLIS);
            assertEquals(DeployedState.STARTED, channel.getCurrentState());
            assertNull(channel.getLastLifecycleTimeout());

            // The fake's onStop is still armed with the gate; open it so this stop is a healthy one
            gate.countDown();
            channel.stop();
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
        } finally {
            gate.countDown();
            if (channel.getCurrentState() != DeployedState.STOPPED) {
                channel.halt();
            }
            channel.undeploy();
        }
    }

    // ---------------------------------------------------------------- IRT-2107 part 2: bounded halt

    /**
     * A source connector whose onHalt never returns used to pin halt forever. Halt now gives the hook
     * the grace period, abandons its thread, and marks the channel STOPPED; the orphan stays visible
     * as abandoned until it finishes. Redeploying afterwards works because STOPPED is not refused.
     *
     * <p>
     * Invariants: unchanged from a pre-IRT-2107 halt (no loss; a duplicate is possible and stated).
     */
    @Test(timeout = 60000)
    public final void testHaltIsBoundedWhenSourceOnHaltBlocks() throws Exception {
        String channelId = "lifecyclehaltonhalt";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId, true, 1, 1);
        channel.setName(channelId);
        channel.setStopGracePeriodMillis(GRACE_MILLIS);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();
        CountDownLatch gate = new CountDownLatch(1);

        try {
            channel.deploy();
            channel.start(null);
            sourceConnector.readTestMessage(testMessage);
            sourceConnector.blockOnHalt(gate);

            long started = System.currentTimeMillis();
            channel.halt();
            long elapsed = System.currentTimeMillis() - started;

            assertTrue("halt took " + elapsed + " ms, which is not bounded", elapsed < BOUND_MILLIS);
            assertEquals("halt must always reach STOPPED", DeployedState.STOPPED, channel.getCurrentState());
            assertTrue(sourceConnector.getOnHaltEntered().await(0, TimeUnit.SECONDS));

            ChannelLifecycleTimeout timeout = channel.getLastLifecycleTimeout();
            assertNotNull("the abandonment must be recorded for the diagnostics endpoint", timeout);
            assertEquals("Halt", timeout.getPhase());

            boolean hookAbandoned = false;
            for (Thread thread : channel.getAbandonedLifecycleThreads()) {
                hookAbandoned |= thread.getName().contains("Channel Halt Hook Thread on " + channelId) && thread.getName().contains("Source (0)");
            }
            assertTrue("the halt hook thread must be reported as abandoned", hookAbandoned);
            assertFalse(channel.isLifecycleOverdue());

            // The orphan finishes on its own once the far end lets go, and drops out of the registry
            gate.countDown();
            for (int i = 0; i < 300 && !channel.getAbandonedLifecycleThreads().isEmpty(); i++) {
                Thread.sleep(10);
            }
            assertTrue("a finished orphan must be pruned", channel.getAbandonedLifecycleThreads().isEmpty());

            channel.undeploy();
            assertFalse(sourceConnector.isDeployed());
        } finally {
            gate.countDown();
            AbandonedThreadRegistry.clear(channelId);
        }
    }

    /**
     * The monitor-blocked case from the ticket: a stop is wedged inside a connector hook that ignores
     * interrupts and, with the grace period disabled for it, holds the lifecycle lock indefinitely.
     * Halt must not queue behind it: it times out on the lock, proceeds in forced mode, interrupts the
     * holder, and marks the channel STOPPED within its own grace period.
     */
    @Test(timeout = 60000)
    public final void testHaltProceedsInForcedModeWhenStopHoldsTheLock() throws Exception {
        String channelId = "lifecycleforcedhalt";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId, true, 1, 1);
        channel.setName(channelId);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();
        CountDownLatch gate = new CountDownLatch(1);
        Thread stopper = null;

        try {
            channel.deploy();
            channel.start(null);
            sourceConnector.blockOnStop(gate);

            // An unbounded stop, so it holds the lock for as long as the hook blocks
            channel.setStopGracePeriodMillis(0);
            final AtomicReference<Throwable> stopError = new AtomicReference<Throwable>();
            stopper = new Thread(() -> {
                try {
                    channel.stop();
                } catch (Throwable t) {
                    stopError.set(t);
                }
            }, "IRT-2107 wedged stopper on " + channelId);
            stopper.start();
            assertTrue(sourceConnector.getOnStopEntered().await(30, TimeUnit.SECONDS));
            for (int i = 0; i < 300 && !channel.isLifecycleLocked(); i++) {
                Thread.sleep(10);
            }
            assertTrue("the wedged stop must be holding the lifecycle lock", channel.isLifecycleLocked());
            assertEquals(DeployedState.STOPPING, channel.getCurrentState());

            channel.setStopGracePeriodMillis(GRACE_MILLIS);
            long started = System.currentTimeMillis();
            channel.halt();
            long elapsed = System.currentTimeMillis() - started;

            assertTrue("forced halt took " + elapsed + " ms, which is not bounded", elapsed < BOUND_MILLIS);
            assertTrue("halt must have waited the grace period for the lock", elapsed >= GRACE_MILLIS);
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
            assertTrue("the wedged stop still holds the lock; halt ran without it", channel.isLifecycleLocked());

            // The last record wins (the survivors' summary); forced mode itself is evidenced by the holder being abandoned
            ChannelLifecycleTimeout timeout = channel.getLastLifecycleTimeout();
            assertNotNull(timeout);
            assertEquals("Halt", timeout.getPhase());

            boolean stopperAbandoned = false;
            for (Thread thread : channel.getAbandonedLifecycleThreads()) {
                stopperAbandoned |= thread == stopper;
            }
            assertTrue("the thread holding the lock must be reported as abandoned", stopperAbandoned);

            /*
             * Let the wedged stop finish. The fake swallowed the interrupt while blocked and re-asserts
             * it on the way out, so the stop's next interrupt check fails it with the halt-notification
             * error: that is the evidence the forced halt reached the lock holder.
             */
            gate.countDown();
            stopper.join(30000);
            assertFalse(stopper.isAlive());
            assertFalse(channel.isLifecycleLocked());
            assertTrue("the interrupted stop must fail with the halt notification: " + stopError.get(), stopError.get() instanceof StopException && stopError.get().getMessage().contains("halt notification"));
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());

            // Fresh lifecycle afterwards
            channel.start(null);
            assertEquals(DeployedState.STARTED, channel.getCurrentState());
            channel.stop();
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
        } finally {
            gate.countDown();
            if (stopper != null) {
                stopper.join(30000);
            }
            if (channel.getCurrentState() != DeployedState.STOPPED) {
                channel.halt();
            }
            channel.undeploy();
            AbandonedThreadRegistry.clear(channelId);
        }
    }

    /**
     * Stale lock release (from the ticket): a dispatch thread abandoned by a halt holds a process lock
     * permit from before the restart. When it finally completes, finishDispatch releases that permit;
     * without the generation check the restarted channel's semaphore would end up with one permit
     * more than configured and silently process two messages at once.
     *
     * <p>
     * Invariant: the release is dropped, not the message; the abandoned dispatch still completes its
     * own commit. The permit count after the orphan finishes must equal the configured count.
     */
    @Test(timeout = 60000)
    public final void testStaleProcessLockReleaseAfterRestartIsANoOp() throws Exception {
        String channelId = "lifecyclestalerelease";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId, true, 1, 1);
        channel.setName(channelId);
        channel.setStopGracePeriodMillis(GRACE_MILLIS);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();
        TestDestinationConnector destinationConnector = (TestDestinationConnector) channel.getDestinationConnector(1);
        DefaultChannelProcessLock processLock = (DefaultChannelProcessLock) channel.getProcessLock();
        CountDownLatch gate = new CountDownLatch(1);
        Thread dispatcher = null;

        try {
            channel.deploy();
            channel.start(null);
            long generation = channel.getProcessLockGeneration();
            assertEquals(1, processLock.getPermits());
            assertEquals(1, processLock.availablePermits());

            destinationConnector.blockSend(gate);
            dispatcher = new Thread(() -> {
                try {
                    sourceConnector.readTestMessage(testMessage);
                } catch (Throwable t) {
                    // the halt interrupt is expected here
                }
            }, "IRT-2107 orphan dispatcher");
            dispatcher.start();
            assertTrue(destinationConnector.getSendEntered().await(30, TimeUnit.SECONDS));
            assertEquals("the dispatch must be holding the permit", 0, processLock.availablePermits());

            // Halt abandons the dispatcher (its send ignores the interrupt); restart resets the permits
            channel.halt();
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
            assertTrue(channel.getAbandonedLifecycleThreads().contains(dispatcher));
            // A halt really did abandon it, so this is the case the flag exists for. This also pins the
            // stickiness end to end: halt records the survivor and then records the timeout naming it,
            // which arrives as an ordinary lifecycle timeout and must not downgrade the halt outcome.
            assertTrue("a halt survivor must be reported as abandoned", channel.getHaltAbandonedThreads().contains(dispatcher));

            /*
             * Recovery re-sends the wedged message on restart (the send gate is one-shot, so that copy
             * goes through): the documented possible duplicate after a halt, and not a loss.
             */
            channel.start(null);
            assertEquals(DeployedState.STARTED, channel.getCurrentState());
            assertEquals("start must move to a new generation", generation + 1, channel.getProcessLockGeneration());
            assertEquals(1, processLock.availablePermits());

            // The orphan completes and releases into the old generation: dropped
            gate.countDown();
            dispatcher.join(30000);
            assertFalse(dispatcher.isAlive());
            assertEquals("a stale release must not add a permit", 1, processLock.availablePermits());

            // The restarted channel still works with exactly one permit
            destinationConnector.blockSend(null);
            sourceConnector.readTestMessage(testMessage);
            assertEquals(1, processLock.availablePermits());

            channel.stop();
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
        } finally {
            gate.countDown();
            if (dispatcher != null) {
                dispatcher.join(30000);
            }
            if (channel.getCurrentState() != DeployedState.STOPPED) {
                channel.halt();
            }
            channel.undeploy();
            AbandonedThreadRegistry.clear(channelId);
        }
    }

    /**
     * Forced-mode undeploy: after a forced halt the lifecycle lock may still be held by the wedged
     * stop. Undeploy must not queue behind it either; it runs the connector undeploy hooks bounded,
     * fires the undeployed event, and returns, so a redeploy can build a fresh Channel.
     */
    @Test(timeout = 60000)
    public final void testUndeployProceedsInForcedModeAfterForcedHalt() throws Exception {
        String channelId = "lifecycleforcedundeploy";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId, true, 1, 1);
        channel.setName(channelId);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();
        TestDestinationConnector destinationConnector = (TestDestinationConnector) channel.getDestinationConnector(1);
        CountDownLatch gate = new CountDownLatch(1);
        Thread stopper = null;

        try {
            channel.deploy();
            channel.start(null);
            sourceConnector.blockOnStop(gate);
            channel.setStopGracePeriodMillis(0);
            stopper = new Thread(() -> {
                try {
                    channel.stop();
                } catch (Throwable t) {
                    // released by the test
                }
            }, "IRT-2107 wedged stopper on " + channelId);
            stopper.start();
            assertTrue(sourceConnector.getOnStopEntered().await(30, TimeUnit.SECONDS));
            for (int i = 0; i < 300 && !channel.isLifecycleLocked(); i++) {
                Thread.sleep(10);
            }

            channel.setStopGracePeriodMillis(GRACE_MILLIS);
            channel.halt();
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
            assertTrue(channel.isLifecycleLocked());

            long started = System.currentTimeMillis();
            channel.undeploy();
            long elapsed = System.currentTimeMillis() - started;
            assertTrue("forced undeploy took " + elapsed + " ms, which is not bounded", elapsed < BOUND_MILLIS);
            assertFalse("the destination undeploy hook must have run", destinationConnector.isDeployed());
            assertFalse("the source undeploy hook must have run", sourceConnector.isDeployed());
            assertEquals("Undeploy", channel.getLastLifecycleTimeout().getPhase());
        } finally {
            gate.countDown();
            if (stopper != null) {
                stopper.join(30000);
            }
            AbandonedThreadRegistry.clear(channelId);
        }
    }

    /**
     * A start that is slow rather than dead: the source connector's onStart takes longer than the grace
     * period and swallows the interrupt forced mode sends. Halt runs underneath it and marks STOPPED;
     * when onStart finally returns, the start must notice the forced halt and roll back rather than
     * flip the channel to STARTED behind the operator's back or leave the source listening under a
     * Stopped channel.
     */
    @Test(timeout = 60000)
    public final void testSlowStartRollsBackWhenAHaltWasForcedUnderneathIt() throws Exception {
        String channelId = "lifecycleslowstart";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId, true, 1, 1);
        channel.setName(channelId);
        channel.setStopGracePeriodMillis(GRACE_MILLIS);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();
        CountDownLatch gate = new CountDownLatch(1);
        Thread starter = null;

        try {
            channel.deploy();
            sourceConnector.blockOnStart(gate);

            final AtomicReference<Throwable> startError = new AtomicReference<Throwable>();
            starter = new Thread(() -> {
                try {
                    channel.start(null);
                } catch (Throwable t) {
                    startError.set(t);
                }
            }, "IRT-2107 slow starter on " + channelId);
            starter.start();
            assertTrue(sourceConnector.getOnStartEntered().await(30, TimeUnit.SECONDS));
            assertEquals(DeployedState.STARTING, channel.getCurrentState());

            channel.halt();
            assertEquals(DeployedState.STOPPED, channel.getCurrentState());
            assertTrue("the slow start still holds the lock", channel.isLifecycleLocked());

            // onStart returns now; the start must roll back, not complete
            gate.countDown();
            starter.join(30000);
            assertFalse(starter.isAlive());
            assertTrue("start must fail: " + startError.get(), startError.get() instanceof StartException);
            assertEquals("the channel must not flip to STARTED after a forced halt", DeployedState.STOPPED, channel.getCurrentState());
            assertEquals("the rollback must stop the source connector the slow start brought up", DeployedState.STOPPED, sourceConnector.getCurrentState());
            assertFalse(channel.isLifecycleLocked());

            // And a clean start afterwards works
            sourceConnector.blockOnStart(null);
            channel.start(null);
            assertEquals(DeployedState.STARTED, channel.getCurrentState());
            channel.stop();
        } finally {
            gate.countDown();
            if (starter != null) {
                starter.join(30000);
            }
            if (channel.getCurrentState() != DeployedState.STOPPED) {
                channel.halt();
            }
            channel.undeploy();
            AbandonedThreadRegistry.clear(channelId);
        }
    }
}
