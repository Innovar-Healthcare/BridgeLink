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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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

    /**
     * Stubs the data source to return a <b>fresh</b> map on every call, modelling rows that are still
     * RECEIVED in the database. A plain thenReturn hands back one map instance which fillBuffer assigns
     * straight to the queue's buffer, so pollFirstValue's iterator.remove() would empty the stub itself
     * and every later fill would come back empty.
     */
    private void stubDatabaseItems(final ConnectorMessage... messages) {
        when(dataSource.getItems(anyInt(), anyInt())).thenAnswer(new Answer<Map<Long, ConnectorMessage>>() {
            @Override
            public Map<Long, ConnectorMessage> answer(InvocationOnMock invocation) {
                return buffer(messages);
            }
        });
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

    /*
     * IRT-1655: an overwrite reuses the original message id, so Channel must be able to quiesce the
     * source queue for that id before deleting the previous message's rows. Otherwise a queue thread
     * mid-process on the old copy commits its destination connector messages after the delete, and
     * the next pass's insert collides on the connector message primary key.
     */

    @Test
    public void isCheckedOutShouldBeTrueWhileAQueueThreadHoldsTheMessage() {
        ConnectorMessage inFlight = message(1);
        when(dataSource.getSize()).thenReturn(1);
        when(dataSource.getItems(anyInt(), anyInt())).thenReturn(buffer(inFlight));

        assertNotNull(queue.poll());

        queue.markAsDeleted(1L);

        // The dispatching thread must wait: the old copy is still being processed
        assertTrue(queue.isCheckedOut(1L));
    }

    /**
     * The overwrite's delete is deferred to commit time, so until then the previous message's row is
     * still RECEIVED and fillBuffer() will hand it back out. Polling that stale copy is what collides
     * with the replacement, so poll() has to honour the deleted flag.
     */
    @Test
    public void pollShouldSkipAMessageMarkedAsDeletedEvenWhenReReadFromTheDatabase() {
        ConnectorMessage stale = message(1);
        ConnectorMessage other = message(2);
        when(dataSource.getSize()).thenReturn(2);
        stubDatabaseItems(stale, other);

        queue.markAsDeleted(1L);

        // The suppressed message must never be handed out, even though the database still returns it
        assertEquals(2, queue.poll().getMessageId());
        assertNull(queue.poll());
    }

    @Test
    public void pollShouldHandOutTheMessageAgainOnceTheDeletedFlagIsCleared() {
        ConnectorMessage replacement = message(1);
        when(dataSource.getSize()).thenReturn(1);
        stubDatabaseItems(replacement);

        queue.markAsDeleted(1L);
        assertNull(queue.poll());

        // What Channel does once the replacement is committed and queued
        queue.clearDeleted(1L);

        assertNotNull(queue.poll());
    }

    @Test
    public void isCheckedOutShouldNotClearTheDeletedFlag() {
        ConnectorMessage queued = message(1);
        when(dataSource.getSize()).thenReturn(1);
        stubDatabaseItems(queued);
        queue.updateSize();
        queue.fillBuffer();

        queue.markAsDeleted(1L);
        assertFalse(queue.isCheckedOut(1L));

        /*
         * The flag has to outlive the wait: the delete is not committed yet, so clearing it here would
         * let a queue thread re-read the stale row from the database and process it.
         */
        assertNull(queue.poll());
    }

    @Test
    public void isCheckedOutShouldEvictTheStaleCopyOnceTheMessageIsFinished() {
        ConnectorMessage inFlight = message(1);
        when(dataSource.getSize()).thenReturn(1);
        when(dataSource.getItems(anyInt(), anyInt())).thenReturn(buffer(inFlight));

        assertNotNull(queue.poll());
        queue.markAsDeleted(1L);
        assertTrue(queue.isCheckedOut(1L));

        // The in-flight attempt completes and the queue thread releases the message
        queue.finish(inFlight);

        assertFalse(queue.isCheckedOut(1L));
        /*
         * Polling already removed this copy from the buffer and decremented the size, so there is
         * nothing to discard and no count query is needed
         */
        verify(dataSource).getSize();

        /*
         * Once the overwrite has committed and queued the replacement it clears the flag, and only then
         * is the replacement handed out - never the copy that was superseded.
         */
        ConnectorMessage replacement = message(1);
        queue.add(replacement);
        queue.clearDeleted(1L);
        assertSame(replacement, queue.poll());
    }

    @Test
    public void isCheckedOutShouldRemoveAnUnprocessedCopyFromTheBuffer() {
        ConnectorMessage queued = message(1);
        when(dataSource.getSize()).thenReturn(1);
        when(dataSource.getItems(anyInt(), anyInt())).thenReturn(buffer(queued));
        queue.updateSize();
        queue.fillBuffer();
        assertTrue(queue.contains(queued));

        // Nothing has polled it yet, so the dispatching thread never waits
        queue.markAsDeleted(1L);
        assertFalse(queue.isCheckedOut(1L));

        // ...but the copy it would have handed out is gone
        assertFalse(queue.contains(queued));
        // That copy was counted in the size, so the size is re-read
        verify(dataSource, times(2)).getSize();
    }

    @Test
    public void markAsDeletedShouldNotAffectOtherMessages() {
        ConnectorMessage first = message(1);
        ConnectorMessage second = message(2);
        when(dataSource.getSize()).thenReturn(2);
        when(dataSource.getItems(anyInt(), anyInt())).thenReturn(buffer(first, second));

        assertNotNull(queue.poll());
        queue.markAsDeleted(1L);

        // Message 2 was never marked, so checking it must not touch the buffer or the size
        assertFalse(queue.isCheckedOut(2L));
        assertTrue(queue.contains(second));
        verify(dataSource).getSize();
    }

    @Test
    public void resetShouldClearTheDeletedSet() {
        queue.markAsDeleted(1L);

        // invalidate(reset = true) happens on deploy and on channel start
        queue.invalidate(false, true);

        ConnectorMessage replacement = message(1);
        when(dataSource.getSize()).thenReturn(1);
        when(dataSource.getItems(anyInt(), anyInt())).thenReturn(buffer(replacement));
        queue.updateSize();
        queue.fillBuffer();

        /*
         * A deleted flag that survived the reset would blackhole every future copy of this reused
         * message id, evicting it from the buffer on the next check.
         */
        assertFalse(queue.isCheckedOut(1L));
        assertTrue(queue.contains(replacement));
    }

}
