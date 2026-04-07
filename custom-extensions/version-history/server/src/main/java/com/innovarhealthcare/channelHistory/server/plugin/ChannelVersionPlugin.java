/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */
package com.innovarhealthcare.channelHistory.server.plugin;
import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.server.exception.GitNotConnectedException;
import com.innovarhealthcare.channelHistory.server.service.VersionHistoryService;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ServerEventContext;
import com.mirth.connect.model.User;
import com.mirth.connect.plugins.ChannelPlugin;
import com.mirth.connect.server.controllers.ControllerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-12-07 9:25 AM
 */
public class ChannelVersionPlugin implements ChannelPlugin {
    private final Logger logger = LogManager.getLogger(ChannelVersionPlugin.class);
    @Override
    public String getPluginPointName() {
        return VersionControlConstants.PLUGIN_POINTNAME;
    }
    @Override
    public void start() {
    }
    @Override
    public void stop() {
    }
    @Override
    public void save(Channel channel, ServerEventContext sec) {
//        thai: not use now -> keep for future
//
//        VersionHistoryService service = GitRepositoryController.getInstance().getVersionHistoryService();
//
//        if (!service.isGitAvailable()) {
//            logger.debug("Git not available, skipping channel save");
//            return;
//        }
//
//        if (!service.isAutoCommitEnabled()) {
//            service.writeChannelToRepo(channel);
//            return;
//        }
//
//        User user;
//        try {
//            user = ControllerFactory.getFactory().createUserController().getUser(sec.getUserId(), null);
//            if (user == null) {
//                logger.error("User not found: {}", sec.getUserId());
//                return;
//            }
//        } catch (ControllerException e) {
//            logger.error("User not found: {}. Exception: {}", sec.getUserId(), e.getMessage());
//            return;
//        }
//
//        try {
//            service.saveChannelAndPush(channel, "", user);
//        } catch (GitNotConnectedException e) {
//            logger.error("Git not connected: {}", e.getMessage());
//        } catch (GitPushFailedException e) {
//            logger.error("Push failed: {}", e.getMessage());
//        } catch (GitOperationException e) {
//            logger.error("Git operation failed: {}", e.getMessage(), e);
//        } catch (Exception e) {
//            logger.error("Unexpected error saving channel", e);
//        }
    }
    @Override
    public void remove(Channel channel, ServerEventContext sec) {
        VersionHistoryService service = GitRepositoryController.getInstance().getVersionHistoryService();
        if (!service.isGitAvailable()) {
            logger.debug("Git not available, skipping channel remove");
            return;
        }
        if (!service.isEnableSyncDelete()) {
            logger.debug("Sync Delete is disabled.");
            return;
        }
        if (!service.isAutoCommitEnabled()) {
            service.deleteChannelFromRepo(channel);
            return;
        }
        User user;
        try {
            user = ControllerFactory.getFactory().createUserController().getUser(sec.getUserId(), null);
            if (user == null) {
                logger.error("Failed to retrieve user for ID: {}", sec.getUserId());
                return;
            }
        } catch (ControllerException e) {
            logger.error("User not found: {}. Exception: {}", sec.getUserId(), e.getMessage());
            return;
        }
        try {
            service.deleteChannelAndPush(channel, "Remove Channel", user);
        } catch (GitNotConnectedException e) {
            logger.warn("Git repository not connected", e);
        } catch (Exception e) {
            logger.error("Unexpected error during commit and push", e);
        }
    }
    @Override
    public void deploy(Channel channel, ServerEventContext arg1) {
    }
    @Override
    public void deploy(ServerEventContext sec) {
    }
    @Override
    public void undeploy(ServerEventContext sec) {
    }
    @Override
    public void undeploy(String id, ServerEventContext sec) {
    }
}
