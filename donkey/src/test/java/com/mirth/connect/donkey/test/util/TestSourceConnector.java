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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.ChannelException;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.SourceConnector;

public class TestSourceConnector extends SourceConnector {
    protected TestConnectorProperties connectorProperties;
    private List<DispatchResult> recoveredDispatchResults = new ArrayList<DispatchResult>();
    private boolean isDeployed = false;
    private List<Long> messageIds = Collections.synchronizedList(new ArrayList<Long>());

    /*
     * IRT-2107 failure-path hooks: a test parks onStop or onHalt on a gate that ignores interrupts,
     * the way a connector blocked in a JDBC close or a socket read with no timeout does. The gate is
     * released by the test in a finally so a wedged hook thread never leaks into the next test.
     */
    private volatile CountDownLatch onStopGate;
    private volatile CountDownLatch onHaltGate;
    private volatile CountDownLatch onUndeployGate;
    private volatile boolean onStopInterruptible;
    private volatile CountDownLatch onStartGate;
    private final CountDownLatch onStartEntered = new CountDownLatch(1);
    private final CountDownLatch onStopEntered = new CountDownLatch(1);
    private final CountDownLatch onHaltEntered = new CountDownLatch(1);

    /** Makes the next onStop block, ignoring interrupts, until the gate is counted down. */
    public void blockOnStop(CountDownLatch gate) {
        this.onStopGate = gate;
    }

    /** Makes the next onHalt block, ignoring interrupts, until the gate is counted down. */
    public void blockOnHalt(CountDownLatch gate) {
        this.onHaltGate = gate;
    }

    /** Makes the next onStart block, ignoring interrupts, until the gate is counted down (a slow connector start). */
    public void blockOnStart(CountDownLatch gate) {
        this.onStartGate = gate;
    }

    public CountDownLatch getOnStartEntered() {
        return onStartEntered;
    }

    /** Like {@link #blockOnStop} but the block honours interrupts, the way a socket accept or a join does. */
    public void blockOnStopInterruptibly(CountDownLatch gate) {
        this.onStopGate = gate;
        this.onStopInterruptible = true;
    }

    /** Makes the next onUndeploy block, ignoring interrupts, until the gate is counted down. */
    public void blockOnUndeploy(CountDownLatch gate) {
        this.onUndeployGate = gate;
    }

    /** Counted down once onStop has been entered, so a test can be sure the hook is the thing blocking. */
    public CountDownLatch getOnStopEntered() {
        return onStopEntered;
    }

    public CountDownLatch getOnHaltEntered() {
        return onHaltEntered;
    }

    /**
     * Blocks until the gate opens, swallowing interrupts and re-asserting the flag afterwards. This
     * models the uninterruptible calls halt cannot reach; a test that wants an interruptible block
     * should await a latch directly instead.
     */
    public static void awaitUninterruptibly(CountDownLatch gate) {
        boolean interrupted = false;
        while (true) {
            try {
                gate.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public List<DispatchResult> getRecoveredDispatchResults() {
        return recoveredDispatchResults;
    }

    public boolean isDeployed() {
        return isDeployed;
    }

    public List<Long> getMessageIds() {
        return messageIds;
    }

    @Override
    public void onDeploy() {
        this.connectorProperties = (TestConnectorProperties) getConnectorProperties();
        isDeployed = true;
    }

    @Override
    public void onUndeploy() {
        CountDownLatch gate = onUndeployGate;
        if (gate != null) {
            awaitUninterruptibly(gate);
        }
        isDeployed = false;
    }

    @Override
    public void onStart() throws ConnectorTaskException {
        onStartEntered.countDown();
        CountDownLatch gate = onStartGate;
        if (gate != null) {
            awaitUninterruptibly(gate);
        }
    }

    @Override
    public void onStop() throws ConnectorTaskException {
        onStopEntered.countDown();
        CountDownLatch gate = onStopGate;
        if (gate != null) {
            if (onStopInterruptible) {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ConnectorTaskException(e);
                }
            } else {
                awaitUninterruptibly(gate);
            }
        }
    }

    @Override
    public void onHalt() throws ConnectorTaskException {
        onHaltEntered.countDown();
        CountDownLatch gate = onHaltGate;
        if (gate != null) {
            awaitUninterruptibly(gate);
        }
    }

    @Override
    public void handleRecoveredResponse(DispatchResult dispatchResult) {
        recoveredDispatchResults.add(dispatchResult);
    }

    public DispatchResult readTestMessage(String raw) throws ChannelException {
        return dispatch(new RawMessage(raw));
    }

    /**
     * Dispatches a raw message that overwrites an existing one, the way "Reprocess and overwrite"
     * does. The overwritten message keeps its original message id.
     */
    public DispatchResult readTestMessageWithOverwrite(String raw, Long originalMessageId) throws ChannelException {
        RawMessage rawMessage = new RawMessage(raw);
        rawMessage.setOverwrite(true);
        rawMessage.setOriginalMessageId(originalMessageId);

        return dispatch(rawMessage);
    }

    private DispatchResult dispatch(RawMessage rawMessage) throws ChannelException {
        DispatchResult dispatchResult = null;

        try {
            dispatchResult = dispatchRawMessage(rawMessage);
        } finally {
            finishDispatch(dispatchResult);
        }

        if (dispatchResult != null) {
            messageIds.add(dispatchResult.getMessageId());
        }

        return dispatchResult;
    }
}
