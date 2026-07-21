/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.channel;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import com.mirth.connect.donkey.server.channel.LogContext;
import com.mirth.connect.model.Channel;
import com.mirth.connect.server.controllers.ChannelController;

public abstract class ChannelTask implements Callable<Void> {

    protected String channelId;
    protected Integer metaDataId;
    private ChannelTaskHandler handler;

    public ChannelTask(String channelId) {
        this(channelId, null);
    }

    public ChannelTask(String channelId, Integer metaDataId) {
        this.channelId = channelId;
        this.metaDataId = metaDataId;
    }

    public String getChannelId() {
        return channelId;
    }

    public Integer getMetaDataId() {
        return metaDataId;
    }

    public ChannelTaskHandler getHandler() {
        return handler;
    }

    public void setHandler(ChannelTaskHandler handler) {
        this.handler = handler;
    }

    public ChannelFuture submitTo(ExecutorService executor) throws RejectedExecutionException {
        return new ChannelFuture(channelId, metaDataId, executor.submit(this), handler);
    }

    @Override
    public final Void call() throws Exception {
        String originalThreadName = Thread.currentThread().getName();

        // Best-effort channel name so admin-task error logging (deploy/undeploy/start/stop, logged
        // via LoggingTaskHandler) carries channelName instead of the channel GUID. Failures here
        // must never break task execution, so swallow any lookup error.
        String channelName = null;
        try {
            Channel channelModel = ChannelController.getInstance().getChannelById(channelId);
            if (channelModel != null) {
                channelName = channelModel.getName();
            }
        } catch (Exception ignore) {
            // ignore — the name is only used for log context
        }

        try (LogContext.Scope channelScope = LogContext.channel(channelId, channelName);
             LogContext.Scope connectorScope = metaDataId != null ? LogContext.connector(null, metaDataId) : null) {
            if (metaDataId != null) {
                Thread.currentThread().setName("Channel " + getClass().getSimpleName() + " Thread on (" + channelId + ") connector (" + metaDataId + ") < " + originalThreadName);
            } else {
                Thread.currentThread().setName("Channel " + getClass().getSimpleName() + " Thread on (" + channelId + ") < " + originalThreadName);
            }

            if (handler == null) {
                handler = new ChannelTaskHandler();
            }

            // Catch is inside the resource scopes so handler.taskErrored() logs with channel+connector MDC.
            try {
                handler.taskStarted(channelId, metaDataId);
                execute();
                handler.taskCompleted(channelId, metaDataId);
            } catch (Exception e) {
                handler.taskErrored(channelId, metaDataId, e);
            }

            return null;
        } finally {
            Thread.currentThread().setName(originalThreadName);
        }
    }

    public abstract Void execute() throws Exception;
}
