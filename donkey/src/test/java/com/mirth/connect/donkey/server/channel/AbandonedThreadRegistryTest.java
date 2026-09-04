/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

/**
 * IRT-2107: an abandoned thread is tracked only while it is still inside the channel. Dispatch threads
 * are frequently pooled or belong to another channel; once the thread restores its original name it
 * has left, and the channel must stop reporting, interrupting or waiting for it.
 */
public class AbandonedThreadRegistryTest {

    private static final String CHANNEL_ID = "irt2107-registry";
    private final CountDownLatch release = new CountDownLatch(1);

    @After
    public void cleanup() {
        release.countDown();
        AbandonedThreadRegistry.clear(CHANNEL_ID);
    }

    private Thread parked(String name) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        return thread;
    }

    @Test
    public void threadIsDroppedOnceItChangesItsName() throws Exception {
        Thread pooled = parked("Channel Dispatch Thread on X (" + CHANNEL_ID + ") < qtp-17");
        AbandonedThreadRegistry.add(CHANNEL_ID, pooled, AbandonedThreadRegistry.Source.HALT);
        assertEquals(1, AbandonedThreadRegistry.alive(CHANNEL_ID).size());

        // The dispatch finished and the pool thread took its original name back: it has left the channel
        pooled.setName("qtp-17");
        assertTrue(AbandonedThreadRegistry.alive(CHANNEL_ID).isEmpty());
        assertTrue("a pruned thread stays pruned", AbandonedThreadRegistry.alive(CHANNEL_ID).isEmpty());
    }

    @Test
    public void deadThreadIsDroppedAndNeverAdded() throws Exception {
        Thread hook = parked("Channel Halt Hook Thread on X (" + CHANNEL_ID + "), Source (0)");
        AbandonedThreadRegistry.add(CHANNEL_ID, hook, AbandonedThreadRegistry.Source.HALT);
        assertEquals(1, AbandonedThreadRegistry.alive(CHANNEL_ID).size());

        release.countDown();
        hook.join(10000);
        assertTrue(AbandonedThreadRegistry.alive(CHANNEL_ID).isEmpty());

        AbandonedThreadRegistry.add(CHANNEL_ID, hook, AbandonedThreadRegistry.Source.HALT);
        assertTrue(AbandonedThreadRegistry.alive(CHANNEL_ID).isEmpty());
    }

    @Test
    public void channelsAreIndependent() throws Exception {
        Thread thread = parked("Source Queue Thread 1 on X (" + CHANNEL_ID + ")");
        AbandonedThreadRegistry.add(CHANNEL_ID, thread, AbandonedThreadRegistry.Source.HALT);
        assertTrue(AbandonedThreadRegistry.alive("some-other-channel").isEmpty());
        assertEquals(1, AbandonedThreadRegistry.alive(CHANNEL_ID).size());
    }

    /**
     * Every tracked thread is interrupted by the next halt and waited for by the next start, whatever
     * recorded it, but only a halt-abandoned one is reported to an operator as abandoned (IRT-2107).
     */
    @Test
    public void aLifecycleTimeoutIsTrackedButNotReportedAsAbandoned() throws Exception {
        Thread stuck = parked("Channel Dispatch Thread on X (" + CHANNEL_ID + ")");
        AbandonedThreadRegistry.add(CHANNEL_ID, stuck, AbandonedThreadRegistry.Source.LIFECYCLE_TIMEOUT);

        assertEquals("the next halt still has to interrupt it", 1, AbandonedThreadRegistry.alive(CHANNEL_ID).size());
        assertTrue("no halt has abandoned it", AbandonedThreadRegistry.abandonedByHalt(CHANNEL_ID).isEmpty());
    }

    @Test
    public void aHaltAbandonedThreadIsReportedAsAbandoned() throws Exception {
        Thread stuck = parked("Destination Queue Thread 1 on X (" + CHANNEL_ID + ")");
        AbandonedThreadRegistry.add(CHANNEL_ID, stuck, AbandonedThreadRegistry.Source.HALT);

        assertEquals(1, AbandonedThreadRegistry.alive(CHANNEL_ID).size());
        assertEquals(1, AbandonedThreadRegistry.abandonedByHalt(CHANNEL_ID).size());
    }

    /**
     * Halt's survivor path records the thread as abandoned and then records the timeout that named it,
     * which arrives as an ordinary lifecycle timeout. The halt outcome is the one the operator needs
     * to keep seeing, so it must not be downgraded.
     */
    @Test
    public void haltProvenanceIsStickyAgainstALaterLifecycleTimeout() throws Exception {
        Thread stuck = parked("Destination Queue Thread 1 on Y (" + CHANNEL_ID + ")");
        AbandonedThreadRegistry.add(CHANNEL_ID, stuck, AbandonedThreadRegistry.Source.HALT);
        AbandonedThreadRegistry.add(CHANNEL_ID, stuck, AbandonedThreadRegistry.Source.LIFECYCLE_TIMEOUT);

        assertEquals("a later timeout must not clear the halt outcome", 1, AbandonedThreadRegistry.abandonedByHalt(CHANNEL_ID).size());
    }

    /** The other direction does upgrade: a thread a stop gave up on and a halt then abandoned. */
    @Test
    public void aHaltUpgradesAThreadAlreadyTrackedByALifecycleTimeout() throws Exception {
        Thread stuck = parked("Channel Stop Hook Thread on X (" + CHANNEL_ID + "), Source (0)");
        AbandonedThreadRegistry.add(CHANNEL_ID, stuck, AbandonedThreadRegistry.Source.LIFECYCLE_TIMEOUT);
        assertTrue(AbandonedThreadRegistry.abandonedByHalt(CHANNEL_ID).isEmpty());

        AbandonedThreadRegistry.add(CHANNEL_ID, stuck, AbandonedThreadRegistry.Source.HALT);
        assertEquals(1, AbandonedThreadRegistry.abandonedByHalt(CHANNEL_ID).size());
        assertEquals(1, AbandonedThreadRegistry.alive(CHANNEL_ID).size());
    }

    /**
     * A rename means the thread left the channel, so the halt outcome recorded under the old name no
     * longer describes it: re-adding it under a new name replaces the entry rather than keeping HALT
     * sticky. Pinned because either answer is defensible and the code has to pick one.
     */
    @Test
    public void haltProvenanceIsNotStickyAcrossARename() throws Exception {
        Thread pooled = parked("Channel Dispatch Thread on X (" + CHANNEL_ID + ") < qtp-33");
        AbandonedThreadRegistry.add(CHANNEL_ID, pooled, AbandonedThreadRegistry.Source.HALT);

        pooled.setName("Source Queue Thread 1 on X (" + CHANNEL_ID + ")");
        AbandonedThreadRegistry.add(CHANNEL_ID, pooled, AbandonedThreadRegistry.Source.LIFECYCLE_TIMEOUT);

        assertEquals("the thread is still tracked under its new name", 1, AbandonedThreadRegistry.alive(CHANNEL_ID).size());
        assertTrue("the old halt outcome does not follow it", AbandonedThreadRegistry.abandonedByHalt(CHANNEL_ID).isEmpty());
    }

    @Test
    public void aRenamedThreadIsPrunedFromTheHaltViewToo() throws Exception {
        Thread pooled = parked("Channel Dispatch Thread on X (" + CHANNEL_ID + ") < qtp-21");
        AbandonedThreadRegistry.add(CHANNEL_ID, pooled, AbandonedThreadRegistry.Source.HALT);
        assertFalse(AbandonedThreadRegistry.abandonedByHalt(CHANNEL_ID).isEmpty());

        pooled.setName("qtp-21");
        assertTrue(AbandonedThreadRegistry.abandonedByHalt(CHANNEL_ID).isEmpty());
    }

    /**
     * The membership test that stops an abandoned thread writing connector state after the fact
     * (IRT-2107). It has to be exact about the thread and its name, because the whole point is that a
     * pooled thread which has left the channel is no longer abandoned and its writes are legitimate.
     */
    @Test
    public void isAbandonedIdentifiesTheThreadAndHonoursTheNameTest() throws Exception {
        Thread hook = parked("Channel Stop Hook Thread on X (" + CHANNEL_ID + "), Source (0)");
        Thread other = parked("Channel Stop Hook Thread on Y (someone-else), Source (0)");

        assertFalse("nothing is abandoned yet", AbandonedThreadRegistry.isAbandoned(CHANNEL_ID, hook));

        AbandonedThreadRegistry.add(CHANNEL_ID, hook, AbandonedThreadRegistry.Source.HALT);
        assertTrue(AbandonedThreadRegistry.isAbandoned(CHANNEL_ID, hook));

        assertFalse("a different thread is not abandoned", AbandonedThreadRegistry.isAbandoned(CHANNEL_ID, other));
        assertFalse("another channel's view is separate", AbandonedThreadRegistry.isAbandoned("some-other-channel", hook));
        assertFalse("a null channel id is not a match", AbandonedThreadRegistry.isAbandoned(null, hook));
        assertFalse("a null thread is not a match", AbandonedThreadRegistry.isAbandoned(CHANNEL_ID, null));

        // Renamed means it left the channel, so its writes are its own business again
        hook.setName("qtp-88");
        assertFalse("a renamed thread is no longer abandoned here", AbandonedThreadRegistry.isAbandoned(CHANNEL_ID, hook));
    }
}
