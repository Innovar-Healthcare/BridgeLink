/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.queue;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.mirth.connect.donkey.model.event.MessageEventType;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.server.event.MessageEvent;

public class SourceQueue extends ConnectorMessageQueue {

    private Set<Long> checkedOut = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private Set<Long> deleted = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());

    @Override
    protected ConnectorMessage pollFirstValue() {
        Iterator<Entry<Long, ConnectorMessage>> iterator = buffer.entrySet().iterator();

        if (iterator.hasNext()) {
            ConnectorMessage connectorMessage = iterator.next().getValue();

            iterator.remove();

            return connectorMessage;
        }

        return null;
    }

    public synchronized ConnectorMessage poll() {
        if (size == null) {
            updateSize();
        }

        ConnectorMessage connectorMessage = null;

        if (size > 0) {
            connectorMessage = pollFirstValue();

            // if no element was received and there are elements in the database,
            // fill the buffer from the database and get the next element in the queue
            if (connectorMessage == null) {
                fillBuffer();
                connectorMessage = pollFirstValue();

                /*
                 * The buffer is still empty even though size claims there are queued messages.
                 * The in-memory count has drifted from the database (e.g. an invalidate raced an
                 * in-flight message), so re-sync it. Otherwise the dashboard shows a phantom
                 * queued count and this thread hot-spins, querying the database on every poll.
                 */
                if (connectorMessage == null && size > 0) {
                    updateSize();
                    eventDispatcher.dispatchEvent(new MessageEvent(channelId, metaDataId, MessageEventType.QUEUED, (long) size(), true));
                }
            }

            /*
             * We use a while loop here to ensure that no message gets polled at the same time from
             * multiple queue threads. After calling poll() and acquiring a connector message, the
             * caller is expected to call finish to remove the message ID from the checked out set.
             *
             * Messages marked as deleted are skipped for the same reason. An overwrite defers its
             * delete to commit time, so until then the previous message's row is still RECEIVED and
             * fillBuffer() above would hand it back out. Processing that stale copy is what collides
             * with the replacement on the connector message primary key (IRT-1655).
             */
            while (connectorMessage != null && (checkedOut.contains(connectorMessage.getMessageId()) || deleted.contains(connectorMessage.getMessageId()))) {
                connectorMessage = pollFirstValue();
            }
        }

        // if an element was found, decrement the overall count
        if (connectorMessage != null) {
            decrementActualSize();
            checkedOut.add(connectorMessage.getMessageId());
            eventDispatcher.dispatchEvent(new MessageEvent(channelId, metaDataId, MessageEventType.QUEUED, (long) size(), true));
        }

        return connectorMessage;
    }

    public synchronized void finish(ConnectorMessage connectorMessage) {
        if (connectorMessage != null) {
            Long messageId = connectorMessage.getMessageId();

            if (buffer.containsKey(messageId)) {
                buffer.remove(messageId);
            }

            checkedOut.remove(messageId);
        }
    }

    /**
     * Flags a message as deleted so that the queue stops handing out any copy of it, whether that copy
     * is already buffered or gets re-read from the database by {@link #fillBuffer()}.
     *
     * <p>
     * Unlike {@link DestinationQueue#markAsDeleted(Long)} the flag is <b>not</b> cleared by
     * {@link #isCheckedOut(Long)}. It has to stay set until the overwriting dispatch has committed its
     * delete and queued the replacement, because until that commit the previous message's row is still
     * RECEIVED and therefore still pollable. The caller must guarantee a matching
     * {@link #clearDeleted(Long)} - a flag left set would blackhole every future copy of that reused
     * message id.
     */
    public synchronized void markAsDeleted(Long messageId) {
        deleted.add(messageId);
    }

    /**
     * Clears the deleted flag set by {@link #markAsDeleted(Long)}, releasing the message id for
     * polling again. Must be called on every path out of an overwrite, including failures.
     */
    public synchronized void clearDeleted(Long messageId) {
        deleted.remove(messageId);
    }

    /**
     * Mirrors {@link DestinationQueue#isCheckedOut(Long)}. Returns whether a queue thread currently
     * has the message checked out, and once it no longer does, discards the stale buffered copy.
     */
    public synchronized boolean isCheckedOut(Long messageId) {
        boolean isCheckedOut = checkedOut.contains(messageId);

        if (!isCheckedOut && deleted.contains(messageId)) {
            /*
             * Discard the buffered copy so it is not handed out again. Only re-sync the size if a copy
             * was actually discarded, since that copy was counted in the size. Unlike DestinationQueue
             * this is conditional: a bulk reprocess calls this once per overwritten message, and an
             * unconditional re-sync would add a count query per message even when there was nothing to
             * discard.
             */
            if (buffer.remove(messageId) != null) {
                updateSize();
            }
        }

        return isCheckedOut;
    }

    @Override
    protected void reset() {
        checkedOut.clear();
        deleted.clear();
    }

    public synchronized void decrementSize() {
        if (size != null) {
            decrementActualSize();
        }

        eventDispatcher.dispatchEvent(new MessageEvent(channelId, metaDataId, MessageEventType.QUEUED, (long) size(), true));
    }

    public ConnectorMessage poll(long timeout, TimeUnit unit) throws InterruptedException {
        waitTimeout(timeout, unit);

        return poll();
    }

    private void waitTimeout(long timeout, TimeUnit unit) throws InterruptedException {
        /*
         * If there are no queued messages, then we want to wait. Otherwise, it's possible that
         * multiple queue threads all have messages checked out and the buffer is full. In this case
         * we also want to wait until at least one of the messages has finished.
         */
        if ((size == null || size == 0 || checkedOut.size() == getBufferCapacity()) && timeout > 0) {
            synchronized (timeoutLock) {
                timeoutLock.set(true);
                timeoutLock.wait(TimeUnit.MILLISECONDS.convert(timeout, unit));
            }
        }
    }
}