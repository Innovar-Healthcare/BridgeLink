/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.DonkeyConfiguration;
import com.mirth.connect.donkey.server.DonkeyConnectionPools;
import com.mirth.connect.donkey.server.StartException;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.test.util.TestChannel;
import com.mirth.connect.donkey.test.util.TestSourceConnector;
import com.mirth.connect.donkey.test.util.TestUtils;

/**
 * IRT-1655: overlapping "Reprocess and overwrite" passes over the same message on a source-queued
 * channel used to collide on the connector message primary key. An overwrite reuses the original
 * message id, so a source queue thread mid-process on the old copy committed its destination
 * connector messages after the next pass had already deleted them, and that pass's own insert then
 * violated {@code d_mm<id>_pkey}. The losing transaction rolled back, leaving the source row RECEIVED
 * so it re-collided on every poll and across restarts.
 *
 * <p>
 * The fix quiesces the source queue for the message id before any of its rows are deleted, so
 * overlapping overwrites serialize per message and the last one wins.
 */
public class OverwriteRaceTests {
    private static String serverId = TestUtils.DEFAULT_SERVER_ID;
    private static String testMessage = TestUtils.TEST_HL7_MESSAGE;

    /*
     * Each test gets its own channel so that a queue thread left wedged by a regression cannot leak
     * into the next test. Set per test rather than passed around, so the row-count helpers stay terse.
     */
    private static volatile String channelId;

    @BeforeClass
    final public static void beforeClass() throws StartException {
        Donkey donkey = Donkey.getInstance();
        DonkeyConfiguration config = TestUtils.getDonkeyTestConfiguration();

        // Close any leaked connection pools from a previously-run test class
        TestUtils.shutdownConnectionPools();
        // Initialize connection pools before starting the engine
        DonkeyConnectionPools.getInstance().init(config.getDonkeyProperties());

        donkey.startEngine(config);
    }

    @AfterClass
    final public static void afterClass() throws StartException {
        Donkey.getInstance().stopEngine();
        TestUtils.shutdownConnectionPools();
    }

    /**
     * An overwrite issued while a source queue thread is still in flight on the previous copy must
     * wait for that copy to finish before deleting its rows, so its own insert cannot collide.
     *
     * <p>
     * Before the fix this test failed by <i>not</i> blocking: the second pass ran straight through the
     * (destination-only) overwrite guard while the queue thread was parked, and the resulting
     * duplicate-key error left the message stuck RECEIVED.
     */
    @Test(timeout = 120000)
    public final void testOverwriteWaitsForInFlightSourceQueueMessage() throws Exception {
        channelId = "overwriteraceinflight";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId);
        // Source queue enabled
        channel.getSourceConnector().setRespondAfterProcessing(false);

        try {
            channel.deploy();
            channel.start(null);

            TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

            // Get a message through the channel normally so we have an id to overwrite
            DispatchResult initial = sourceConnector.readTestMessage(testMessage);
            assertNotNull(initial);
            Long messageId = initial.getMessageId();
            awaitProcessed(channel, messageId);

            /*
             * Park the queue thread inside process() on the first overwrite's copy, which leaves the
             * message checked out of the source queue with its destination rows not yet committed.
             */
            CountDownLatch inFlight = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            channel.blockProcessing(messageId, inFlight, release);

            sourceConnector.readTestMessageWithOverwrite(testMessage, messageId);
            assertTrue("the source queue thread never picked up the first overwrite", inFlight.await(30, TimeUnit.SECONDS));

            // Second overwrite pass over the same message, from another thread
            final AtomicReference<Throwable> secondPassError = new AtomicReference<Throwable>();
            final CountDownLatch secondPassDone = new CountDownLatch(1);
            Thread secondPass = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ((TestSourceConnector) channel.getSourceConnector()).readTestMessageWithOverwrite(testMessage, messageId);
                    } catch (Throwable t) {
                        secondPassError.set(t);
                    } finally {
                        secondPassDone.countDown();
                    }
                }
            });
            secondPass.setName("IRT-1655 second overwrite pass");
            secondPass.start();

            // It must block: deleting this message's rows while the old copy is in flight is the bug
            assertFalse("the second overwrite deleted rows while the previous copy was still in flight", secondPassDone.await(3, TimeUnit.SECONDS));

            // Let the in-flight copy finish; the second pass supersedes it
            release.countDown();
            assertTrue("the second overwrite never completed", secondPassDone.await(30, TimeUnit.SECONDS));
            secondPass.join();

            if (secondPassError.get() != null) {
                throw new AssertionError("the second overwrite pass failed", secondPassError.get());
            }

            awaitSourceQueueDrained(channel);

            // Exactly one row per connector: no duplicate insert, and nothing left stuck RECEIVED
            awaitSettled(messageId);
        } finally {
            stopAndUndeploy(channel);
        }
    }

    /**
     * Two threads issuing overwrite passes over an overlapping id range must not produce duplicate-key
     * errors or leave any message stuck RECEIVED.
     *
     * <p>
     * This also covers a second, independent defect found while validating the quiesce above: two
     * dispatch threads could both quiesce the same message, both see it as not checked out, and then
     * interleave their deletes and inserts, permanently orphaning a message at RECEIVED with no
     * duplicate rows, no dispatch error and nothing in the log. Against unmodified HEAD this failed on
     * roughly one Postgres run in three, and raising the settle wait to 180 seconds did not help - the
     * message was never polled again. The per-message-id serialization in
     * {@link Channel#dispatchRawMessage} is what closes it.
     */
    @Test(timeout = 240000)
    public final void testConcurrentOverwritePassesOverOverlappingMessages() throws Exception {
        final int messageCount = 10;
        channelId = "overwriteraceconcurrent";
        TestChannel channel = TestUtils.createDefaultChannel(channelId, serverId);
        channel.getSourceConnector().setRespondAfterProcessing(false);

        try {
            channel.deploy();
            channel.start(null);

            final TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

            // Seed the channel
            final List<Long> messageIds = new ArrayList<Long>();
            for (int i = 0; i < messageCount; i++) {
                messageIds.add(sourceConnector.readTestMessage(testMessage).getMessageId());
            }
            awaitSourceQueueDrained(channel);

            // Two passes over the same ids, in opposite order so they interleave
            final List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());
            List<Thread> passes = new ArrayList<Thread>();

            for (int pass = 0; pass < 2; pass++) {
                final boolean reverse = pass == 1;
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        List<Long> ids = new ArrayList<Long>(messageIds);
                        if (reverse) {
                            Collections.reverse(ids);
                        }

                        for (Long messageId : ids) {
                            try {
                                sourceConnector.readTestMessageWithOverwrite(testMessage, messageId);
                            } catch (Throwable t) {
                                errors.add(t);
                            }
                        }
                    }
                });
                thread.setName("IRT-1655 overwrite pass " + (pass + 1));
                passes.add(thread);
            }

            for (Thread thread : passes) {
                thread.start();
            }
            for (Thread thread : passes) {
                thread.join(TimeUnit.MINUTES.toMillis(2));
                assertFalse("an overwrite pass did not finish", thread.isAlive());
            }

            if (!errors.isEmpty()) {
                throw new AssertionError("overwrite passes reported " + errors.size() + " error(s), first: " + errors.get(0), errors.get(0));
            }

            awaitSourceQueueDrained(channel);

            /*
             * Every message must have exactly one row per connector and must not be left RECEIVED. A
             * duplicate-key rollback shows up here as a message stuck RECEIVED that the queue thread
             * re-attempts forever.
             */
            for (Long messageId : messageIds) {
                awaitSettled(messageId);
            }
        } finally {
            stopAndUndeploy(channel);
        }
    }

    private static int countConnectorMessageRows(long messageId, int metaDataId) throws Exception {
        long localChannelId = ChannelController.getInstance().getLocalChannelId(channelId);
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet result = null;

        try {
            connection = TestUtils.getConnection();
            statement = connection.prepareStatement("SELECT COUNT(*) FROM D_MM" + localChannelId + " WHERE message_id = ? AND id = ?");
            statement.setLong(1, messageId);
            statement.setInt(2, metaDataId);
            result = statement.executeQuery();
            result.next();
            return result.getInt(1);
        } finally {
            TestUtils.close(result);
            TestUtils.close(statement);
            TestUtils.close(connection);
        }
    }

    private static void awaitProcessed(TestChannel channel, long messageId) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(20);

        while (System.currentTimeMillis() < deadline) {
            if (TestUtils.isMessageProcessed(channelId, messageId)) {
                return;
            }
            Thread.sleep(50);
        }

        fail("message " + messageId + " was never processed - a duplicate-key rollback leaves the source row RECEIVED and it re-errors on every poll");
    }

    /**
     * Waits until a message has settled into its expected final shape: exactly one row per connector
     * and a source status of TRANSFORMED.
     *
     * <p>
     * This has to be a wait rather than a bare assertion. {@link SourceQueue#poll()} decrements the
     * queue size when a message is checked out, not when it finishes, so a drained queue does not mean
     * the last copy is done - it can still be mid-process with its destination row not yet inserted
     * and its source row still RECEIVED. A message genuinely wedged by a duplicate-key rollback never
     * reaches this state, so the bounded wait still catches the regression.
     */
    private static void awaitSettled(long messageId) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        int sourceRows = -1;
        int destinationRows = -1;
        Status sourceStatus = null;

        while (System.currentTimeMillis() < deadline) {
            sourceRows = countConnectorMessageRows(messageId, 0);
            destinationRows = countConnectorMessageRows(messageId, 1);
            sourceStatus = getConnectorMessageStatus(messageId, 0);

            if (sourceRows == 1 && destinationRows == 1 && sourceStatus == Status.TRANSFORMED) {
                return;
            }

            Thread.sleep(50);
        }

        fail("message " + messageId + " never settled - source rows: " + sourceRows + " (expected 1), destination rows: " + destinationRows + " (expected 1), source status: " + sourceStatus + " (expected TRANSFORMED). More than one row means the overwrite inserted a duplicate; a source row stuck RECEIVED means a duplicate-key rollback left it re-erroring on every poll.");
    }

    private static Status getConnectorMessageStatus(long messageId, int metaDataId) throws Exception {
        long localChannelId = ChannelController.getInstance().getLocalChannelId(channelId);
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet result = null;

        try {
            connection = TestUtils.getConnection();
            statement = connection.prepareStatement("SELECT status FROM D_MM" + localChannelId + " WHERE message_id = ? AND id = ?");
            statement.setLong(1, messageId);
            statement.setInt(2, metaDataId);
            result = statement.executeQuery();

            if (!result.next()) {
                return null;
            }

            return Status.fromChar(result.getString("status").charAt(0));
        } finally {
            TestUtils.close(result);
            TestUtils.close(statement);
            TestUtils.close(connection);
        }
    }

    private static void awaitSourceQueueDrained(TestChannel channel) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(20);

        while (System.currentTimeMillis() < deadline) {
            if (channel.getSourceQueue().size() == 0) {
                return;
            }
            Thread.sleep(50);
        }

        fail("the source queue never drained, size is " + channel.getSourceQueue().size() + " - a message that collides on every poll never leaves the queue");
    }

    /**
     * Halts rather than stops. A regression in the overwrite guard leaves a queue thread wedged
     * re-attempting a message that collides on every poll, and {@code stop()} would block joining it,
     * replacing the real assertion failure with a test timeout.
     */
    private static void stopAndUndeploy(TestChannel channel) {
        try {
            channel.halt();
        } catch (Exception e) {
            // Nothing useful to do during cleanup
        }

        try {
            channel.undeploy();
            ChannelController.getInstance().removeChannel(channelId);
        } catch (Exception e) {
            // Nothing useful to do during cleanup
        }
    }
}
