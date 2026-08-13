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
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.mirth.connect.donkey.test.util.TestConnectorProperties;
import com.mirth.connect.donkey.test.util.TestResponseTransformer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.PollConnectorPropertiesInterface;
import com.mirth.connect.donkey.model.channel.PollingType;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.DonkeyConfiguration;
import com.mirth.connect.donkey.server.DonkeyConnectionPools;
import com.mirth.connect.donkey.server.StartException;
import com.mirth.connect.donkey.server.channel.DestinationChainProvider;
import com.mirth.connect.donkey.server.channel.MetaDataReplacer;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.test.util.TestChannel;
import com.mirth.connect.donkey.test.util.TestDataType;
import com.mirth.connect.donkey.test.util.TestDestinationConnector;
import com.mirth.connect.donkey.test.util.TestPollConnector;
import com.mirth.connect.donkey.test.util.TestPollConnectorProperties;
import com.mirth.connect.donkey.test.util.TestPostProcessor;
import com.mirth.connect.donkey.test.util.TestPreProcessor;
import com.mirth.connect.donkey.test.util.TestUtils;

public class ConnectorTests {
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

    /*
     * Creates a poll connector channel with a 1000 ms polling frequency, waits until 7 polls have
     * completed, and asserts that each poll produced exactly one processed message.
     *
     * IRT-1788: this used to sleep 6800 ms and assert exactly 7 polls, which failed roughly 1 run
     * in 4. Quartz anchors an interval trigger at midnight rather than at channel.start()
     * (TriggerFactory.createDailyInterval), and pollOnStart is false, so polls fire on absolute
     * whole-second boundaries. A 6800 ms window only contains 7 of those boundaries when it happens
     * to start at least 200 ms into a second, so ~20% of runs saw 6 polls on a completely idle
     * machine, before machine load was a factor at all. Waiting for the polls removes the
     * dependency on wall clock; the elapsed-time assertion below still holds the polling frequency
     * itself to account.
     */
    @Test
    public final void testPollConnector() throws Exception {
        final int pollingFrequency = 1000;
        final int expectedPollCount = 7;
        // 7 polls need at least 6 s of wall clock, so this is generous margin for a loaded machine
        // rather than a timing assumption - only the failure path waits this long.
        final long pollTimeoutMillis = 30000L;

        String channelId = TestUtils.DEFAULT_CHANNEL_ID;
        String serverId = TestUtils.DEFAULT_SERVER_ID;

        if (ChannelController.getInstance().channelExists(channelId)) {
            ChannelController.getInstance().deleteAllMessages(channelId);
        }

        TestChannel channel = new TestChannel();

        channel.setChannelId(channelId);
        channel.setServerId(serverId);

        channel.setPreProcessor(new TestPreProcessor());
        channel.setPostProcessor(new TestPostProcessor());

        ConnectorProperties connectorProperties = new TestPollConnectorProperties();
        ((PollConnectorPropertiesInterface) connectorProperties).getPollConnectorProperties().setPollingType(PollingType.INTERVAL);
        ((PollConnectorPropertiesInterface) connectorProperties).getPollConnectorProperties().setPollingFrequency(pollingFrequency);

        CountingPollConnector sourceConnector = new CountingPollConnector(expectedPollCount);
        sourceConnector.setConnectorProperties(connectorProperties);
        sourceConnector.setInboundDataType(new TestDataType());
        sourceConnector.setOutboundDataType(new TestDataType());
        sourceConnector.setMetaDataReplacer(new MetaDataReplacer());
        sourceConnector.setChannelId(channel.getChannelId());
        sourceConnector.setChannel(channel);
        channel.setSourceConnector(sourceConnector);
        channel.getSourceConnector().setFilterTransformerExecutor(TestUtils.createDefaultFilterTransformerExecutor());

        // Initialize ResponseSelector before accessing it
        channel.setResponseSelector(new com.mirth.connect.donkey.server.channel.ResponseSelector(sourceConnector.getInboundDataType()));
        channel.getResponseSelector().setRespondFromName(TestUtils.DEFAULT_RESPOND_FROM_NAME);

        // Create destination connector with channel reference to avoid NullPointerException in getSerializer()
        TestDestinationConnector destinationConnector = (TestDestinationConnector) TestUtils.createDestinationConnector(
            channel, channelId, serverId,
            new com.mirth.connect.donkey.test.util.TestConnectorProperties(),
            TestUtils.DEFAULT_DESTINATION_NAME,
            new com.mirth.connect.donkey.test.util.TestDataType(),
            new com.mirth.connect.donkey.test.util.TestDataType(),
            new com.mirth.connect.donkey.test.util.TestResponseTransformer(),
            1
        );

        DestinationChainProvider chain = new DestinationChainProvider();
        chain.setChannelId(channelId);
        destinationConnector.setMetaDataReplacer(sourceConnector.getMetaDataReplacer());
        destinationConnector.setMetaDataColumns(channel.getMetaDataColumns());
        destinationConnector.setFilterTransformerExecutor(TestUtils.createDefaultFilterTransformerExecutor());
        chain.addDestination(1, destinationConnector);
        channel.addDestinationChainProvider(chain);

        // Initialize the source queue (required for deployment)
        com.mirth.connect.donkey.server.queue.SourceQueue sourceQueue = new com.mirth.connect.donkey.server.queue.SourceQueue();
        channel.setSourceQueue(sourceQueue);

        // Initialize the channel process lock (default to 1 processing thread for tests)
        com.mirth.connect.donkey.server.channel.ChannelProcessLock processLock = new com.mirth.connect.donkey.server.channel.DefaultChannelProcessLock(1);
        channel.setProcessLock(processLock);

        channel.deploy();

        long startNanos = System.nanoTime();
        channel.start(null);
        boolean reachedExpectedPolls = sourceConnector.awaitPolls(pollTimeoutMillis, TimeUnit.MILLISECONDS);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        // stop() shuts the Quartz scheduler down and waits for any in-flight poll, so the counts
        // below are stable once it returns.
        channel.stop();
        channel.undeploy();

        int actualPollCount = sourceConnector.getPollCount();

        assertTrue("Expected " + expectedPollCount + " polls within " + pollTimeoutMillis + "ms but only " + actualPollCount + " completed", reachedExpectedPolls);
        // Polls are spaced by the polling frequency, so reaching the expected count any faster
        // would mean the frequency was not honored.
        assertTrue("Expected " + expectedPollCount + " polls to take at least " + ((expectedPollCount - 1) * pollingFrequency) + "ms but took " + elapsedMillis + "ms", elapsedMillis >= (expectedPollCount - 1) * pollingFrequency);
        // Every poll dispatches exactly one message. Not asserted against expectedPollCount
        // directly: a further poll may fire between the latch opening and stop() completing.
        assertEquals(actualPollCount, channel.getNumMessages());
    }

    /**
     * Counts completed polls so a test can wait for them instead of sleeping for a fixed time.
     * Mirrors the CountDownJob pattern in PollConnectorJobTests.
     */
    private static class CountingPollConnector extends TestPollConnector {
        private final AtomicInteger pollCount = new AtomicInteger();
        private final CountDownLatch latch;

        CountingPollConnector(int expectedPolls) {
            latch = new CountDownLatch(expectedPolls);
        }

        @Override
        protected void poll() {
            super.poll();
            pollCount.incrementAndGet();
            latch.countDown();
        }

        boolean awaitPolls(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        int getPollCount() {
            return pollCount.get();
        }
    }
}
