/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.channel;

import java.util.concurrent.TimeUnit;

public interface ChannelProcessLock {
    public void acquire() throws InterruptedException;

    public void acquireAll() throws InterruptedException;

    /**
     * Acquires every permit, giving up after the timeout. Returns true if the permits were acquired.
     * The default waits without bound so existing implementations keep their behaviour; override it
     * to make a channel stop honour its grace period (IRT-2107).
     */
    public default boolean tryAcquireAll(long timeout, TimeUnit unit) throws InterruptedException {
        acquireAll();
        return true;
    }

    public void release();

    public void releaseAll();

    public void reset();
}
