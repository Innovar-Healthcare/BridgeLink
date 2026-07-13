/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.server.event.EventDispatcher;

/**
 * Regression tests for IRT-1119: SourceQueue's in-memory size can drift above the true database
 * count when an invalidate races an in-flight message (poll -> process -> finish). SourceQueue has
 * no invalidation lock (unlike DestinationQueue), so poll() self-heals by re-syncing the size from
 * the database whenever the size claims messages exist but the database returns an empty buffer.
 */
public class SourceQueueTest {

    private static final String CHANNEL_ID = "sourcequeuetest";

    private SourceQueue queue;
    private ConnectorMessageQueueDataSource dataSource;

    @Before
    public void setup() {
        dataSource = mock(ConnectorMessageQueueDataSource.class);
        when(dataSource.getChannelId()).thenReturn(CHANNEL_ID);
        when(dataSource.getMetaDataId()).thenReturn(0);

        queue = new SourceQueue();
        // The real dispatcher comes from Donkey.getInstance(), which isn't started in unit tests
        queue.eventDispatcher = mock(EventDispatcher.class);
        queue.setDataSource(dataSource);
    }

    private ConnectorMessage message(long messageId) {
        ConnectorMessage connectorMessage = new ConnectorMessage();
        connectorMessage.setChannelId(CHANNEL_ID);
        connectorMessage.setMessageId(messageId);
        connectorMessage.setMetaDataId(0);
        return connectorMessage;
    }

    private Map<Long, ConnectorMessage> buffer(ConnectorMessage... messages) {
        Map<Long, ConnectorMessage> map = new LinkedHashMap<Long, ConnectorMessage>();
        for (ConnectorMessage connectorMessage : messages) {
            map.put(connectorMessage.getMessageId(), connectorMessage);
        }
        return map;
    }

    @Test
    public void pollShouldHealPhantomSizeWhenDatabaseIsEmpty() {
        // Seed the drift: the queue believes one message is queued
        when(dataSource.getSize()).thenReturn(1);
        queue.updateSize();
        assertEquals(1, queue.size());

        // But the database actually has nothing queued
        when(dataSource.getSize()).thenReturn(0);
        when(dataSource.getItems(anyInt(), anyInt())).thenReturn(buffer());

        assertNull(queue.poll());

        // Without the self-heal the size stays at 1 forever and the queue thread hot-spins
        assertEquals(0, queue.size());
    }

    @Test
    public void invalidateDuringInFlightMessageShouldHealOnNextPoll() {
        ConnectorMessage inFlight = message(1);
        when(dataSource.getSize()).thenReturn(1);
        when(dataSource.getItems(anyInt(), anyInt())).thenReturn(buffer(inFlight));

        // The source queue thread checks out the message and starts processing it
        assertNotNull(queue.poll());
        assertEquals(0, queue.size());

        // Messages are removed mid-flight: Channel.invalidateQueues() invalidates with no lock,
        // and the size re-read still counts the in-flight message (still RECEIVED in the database)
        queue.invalidate(true, false);
        assertEquals(1, queue.size());

        // The in-flight message commits and finishes; the phantom count is now permanent
        queue.finish(inFlight);
        when(dataSource.getSize()).thenReturn(0);
        when(dataSource.getItems(anyInt(), anyInt())).thenReturn(buffer());

        assertNull(queue.poll());
        assertEquals(0, queue.size());
    }

    @Test
    public void pollShouldNotResyncWhenDatabaseHasMessages() {
        ConnectorMessage first = message(1);
        ConnectorMessage second = message(2);
        when(dataSource.getSize()).thenReturn(2);
        when(dataSource.getItems(anyInt(), anyInt())).thenReturn(buffer(first, second));

        assertEquals(1, queue.poll().getMessageId());
        queue.finish(first);
        assertEquals(2, queue.poll().getMessageId());
        queue.finish(second);
        assertEquals(0, queue.size());

        // Healthy operation reads the size from the database exactly once (the initial updateSize)
        verify(dataSource).getSize();
    }
}
