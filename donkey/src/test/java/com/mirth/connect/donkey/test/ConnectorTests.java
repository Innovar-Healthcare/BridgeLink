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
import com.mirth.connect.donkey.server.channel.SourceConnector;
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
     * Create a new poll connector channel Set the polling frequency to 500 ms Starts the channel,
     * waits 3250 ms, asserts that: - 7 messages are processed by the channel
     */
    @Test
    public final void testPollConnector() throws Exception {
        final int pollingFrequency = 500;
        // Polls fire at t=0, 500, 1000, 1500, 2000, 2500, 3000ms (7 total).
        // 3400ms gives enough buffer for the 7th poll to complete on slow DBs (PostgreSQL)
        // while stopping before the 8th poll fires at t=3500ms.
        final int sleepMillis = 3400;
        final int expectedMessageCount = 7;

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

        SourceConnector sourceConnector = new TestPollConnector();
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
        channel.start(null);
        Thread.sleep(sleepMillis);
        channel.stop();
        channel.undeploy();

        assertEquals(expectedMessageCount, channel.getNumMessages());
    }
}
