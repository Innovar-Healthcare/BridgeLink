/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.donkey.model.channel.DebugOptions;
import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.DestinationChainProvider;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.server.util.javascript.MirthContextFactory;
import com.mirth.connect.model.ChannelMetadata;

/**
 * IRT-2107: a halt aimed at a channel that is deploying must not be undone by the start that follows
 * the deploy.
 *
 * <p>
 * DeployTask makes two lifecycle calls -- deploy, then start -- and releases the channel's lifecycle
 * lock between them, so a halt can land in that gap, mark the channel STOPPED, and then be overtaken.
 * QA could not reach this by hand: DEPLOYING is haltable in both admin clients, but the deploy call
 * blocks the caller, so one session cannot issue the halt. That is why it is tested here.
 *
 * <p>
 * The guard reads {@link Channel#getHaltEpoch()} before the channel enters the deployed set (the map
 * HaltTask resolves against) and compares before starting. This test drives the comparison; that a
 * real halt moves the epoch, forced or not, is pinned by {@code ChannelLifecycleTests}.
 */
public class DonkeyEngineControllerHaltDuringDeployTest {

    private static final String CHANNEL_ID = "irt2107-halt-during-deploy";

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicLong haltEpoch = new AtomicLong();
    private final AtomicBoolean haltDuringDeploy = new AtomicBoolean();

    /** A channel that records whether it was started, and can have a halt land while it deploys. */
    private class FakeChannel extends Channel {

        FakeChannel() {
            setChannelId(CHANNEL_ID);
            setName("Halt During Deploy");
        }

        @Override
        public long getHaltEpoch() {
            return haltEpoch.get();
        }

        @Override
        public void deploy() {
            if (haltDuringDeploy.get()) {
                // What a halt does to this channel while the deploy is in flight
                haltEpoch.incrementAndGet();
            }
        }

        @Override
        public void start(Set<Integer> connectorsToStart) {
            started.set(true);
        }

        @Override
        public void updateCurrentState(DeployedState currentState) {
            // No event dispatcher in this fixture
        }

        @Override
        public DeployedState getInitialState() {
            return DeployedState.STARTED;
        }

        @Override
        public List<Integer> getMetaDataIds() {
            List<Integer> ids = new ArrayList<Integer>();
            ids.add(0);
            return ids;
        }

        @Override
        public SourceConnector getSourceConnector() {
            return mock(SourceConnector.class);
        }

        @Override
        public List<DestinationChainProvider> getDestinationChainProviders() {
            return new ArrayList<DestinationChainProvider>();
        }
    }

    private class TestController extends DonkeyEngineController {

        @Override
        protected ConfigurationController getConfigurationController() {
            ConfigurationController controller = mock(ConfigurationController.class);
            when(controller.getChannelMetadata()).thenReturn(new HashMap<String, ChannelMetadata>());
            return controller;
        }

        @Override
        protected ChannelController getChannelController() {
            return mock(ChannelController.class);
        }

        @Override
        protected ScriptController getScriptController() {
            return mock(ScriptController.class);
        }

        @Override
        protected ContextFactoryController getContextFactoryController() {
            ContextFactoryController controller = mock(ContextFactoryController.class);
            try {
                when(controller.getContextFactory(anySet())).thenReturn(mock(MirthContextFactory.class));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return controller;
        }

        @Override
        protected Channel createChannelFromModel(com.mirth.connect.model.Channel channelModel, DebugOptions debugOptions) {
            return new FakeChannel();
        }

        DeployTask task() {
            return new DeployTask(CHANNEL_ID, null, null, null, null) {
                @Override
                protected boolean checkEnabled(com.mirth.connect.model.Channel channelModel) {
                    return true;
                }
            };
        }
    }

    private com.mirth.connect.model.Channel channelModel() {
        com.mirth.connect.model.Channel model = new com.mirth.connect.model.Channel();
        model.setId(CHANNEL_ID);
        model.setName("Halt During Deploy");
        return model;
    }

    @Before
    public void setUp() {
        Donkey.getInstance().getDeployedChannels().remove(CHANNEL_ID);
        started.set(false);
        haltEpoch.set(0);
        haltDuringDeploy.set(false);
    }

    @After
    public void tearDown() {
        Donkey.getInstance().getDeployedChannels().remove(CHANNEL_ID);
    }

    @Test(timeout = 30000)
    public void aHaltThatLandsWhileTheChannelIsDeployingIsNotUndoneByTheStart() throws Exception {
        haltDuringDeploy.set(true);

        Channel deployed = new TestController().task().doDeploy(channelModel());

        assertFalse("a halt ran while this channel was deploying; starting it would undo the halt", started.get());
        assertTrue("the halt must be what stopped the start, not a failed deploy", deployed != null);
        assertEquals("the epoch must have moved, or this test proves nothing", 1L, haltEpoch.get());
    }

    @Test(timeout = 30000)
    public void anOrdinaryDeployStillStartsTheChannel() throws Exception {
        haltDuringDeploy.set(false);

        new TestController().task().doDeploy(channelModel());

        assertTrue("with no halt in the way the deploy must still start the channel", started.get());
        assertEquals(0L, haltEpoch.get());
    }
}
