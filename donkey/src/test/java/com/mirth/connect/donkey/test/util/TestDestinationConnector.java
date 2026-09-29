/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.test.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.DestinationConnector;

public class TestDestinationConnector extends DestinationConnector {
    protected TestConnectorProperties connectorProperties;
    final public static String TEST_RESPONSE_PREFIX = "response";
    private volatile boolean queueThreadRunning = false;

    private List<Long> messageIds = new ArrayList<Long>();
    private boolean isDeployed = false;

    /*
     * IRT-2107 failure-path hooks, see TestSourceConnector: send, onStop or onHalt park on a gate
     * that ignores interrupts until the test releases it.
     */
    private volatile CountDownLatch sendGate;
    private volatile CountDownLatch onStopGate;
    private volatile CountDownLatch onHaltGate;
    private volatile CountDownLatch onUndeployGate;
    private final CountDownLatch sendEntered = new CountDownLatch(1);
    private final CountDownLatch onHaltEntered = new CountDownLatch(1);
    private final java.util.concurrent.atomic.AtomicBoolean sendBlockConsumed = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Makes the next send block, ignoring interrupts, until the gate is counted down. One-shot: the
     * send after that goes through, so a restart's recovery re-send of the wedged message (the
     * documented possible duplicate after a halt) completes instead of wedging recovery too.
     */
    public void blockSend(CountDownLatch gate) {
        sendBlockConsumed.set(false);
        this.sendGate = gate;
    }

    public void blockOnStop(CountDownLatch gate) {
        this.onStopGate = gate;
    }

    public void blockOnHalt(CountDownLatch gate) {
        this.onHaltGate = gate;
    }

    public void blockOnUndeploy(CountDownLatch gate) {
        this.onUndeployGate = gate;
    }

    /** Counted down when the first blocked send is entered. */
    public CountDownLatch getSendEntered() {
        return sendEntered;
    }

    public CountDownLatch getOnHaltEntered() {
        return onHaltEntered;
    }

    public List<Long> getMessageIds() {
        return messageIds;
    }

    public boolean isDeployed() {
        return isDeployed;
    }

    public boolean isQueueThreadRunning() {
        return queueThreadRunning;
    }

    @Override
    public Response send(ConnectorProperties connectorProperties, ConnectorMessage message) {
        CountDownLatch gate = sendGate;
        if (gate != null && sendBlockConsumed.compareAndSet(false, true)) {
            sendEntered.countDown();
            TestSourceConnector.awaitUninterruptibly(gate);
        }
        messageIds.add(message.getMessageId());
        return new Response(Status.SENT, TEST_RESPONSE_PREFIX + message.getMessageId());
    }

    @Override
    public void run() {
        queueThreadRunning = true;
        super.run();
        queueThreadRunning = false;
    }

    @Override
    public void onDeploy() throws ConnectorTaskException {
        isDeployed = true;
    }

    @Override
    public void onUndeploy() throws ConnectorTaskException {
        CountDownLatch gate = onUndeployGate;
        if (gate != null) {
            TestSourceConnector.awaitUninterruptibly(gate);
        }
        isDeployed = false;
    }

    @Override
    public void onStart() throws ConnectorTaskException {}

    @Override
    public void onStop() throws ConnectorTaskException {
        CountDownLatch gate = onStopGate;
        if (gate != null) {
            TestSourceConnector.awaitUninterruptibly(gate);
        }
    }

    @Override
    public void onHalt() throws ConnectorTaskException {
        onHaltEntered.countDown();
        CountDownLatch gate = onHaltGate;
        if (gate != null) {
            TestSourceConnector.awaitUninterruptibly(gate);
        }
    }

    @Override
    public void replaceConnectorProperties(ConnectorProperties connectorProperties, ConnectorMessage message) {}
}
