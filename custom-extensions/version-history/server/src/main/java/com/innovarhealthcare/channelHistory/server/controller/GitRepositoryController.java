/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.server.controller;

import java.util.Properties;

import com.innovarhealthcare.channelHistory.server.exception.GitRepositoryException;
import com.innovarhealthcare.channelHistory.server.service.GitRepositoryService;
import com.innovarhealthcare.channelHistory.server.service.VersionHistoryService;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GitRepositoryController {
    private static final Logger logger = LogManager.getLogger(GitRepositoryController.class);

    private GitRepositoryService gitService;
    private VersionHistoryService versionHistoryService;
    private VersionHistoryProperties versionHistoryProperties;  // Mutable

    private static GitRepositoryController instance;

    public static GitRepositoryController getInstance() {
        synchronized (GitRepositoryController.class) {
            if (instance == null) {
                instance = new GitRepositoryController();
            }

            return instance;
        }
    }

    public void init(Properties properties) throws GitRepositoryException {
        logger.info("Initializing Version History Plugin...");

        if (properties == null) {
            throw new GitRepositoryException("Properties cannot be null");
        }

        try {
            // Store config
            this.versionHistoryProperties = new VersionHistoryProperties(properties);

            // Create service objects
            this.gitService = new GitRepositoryService(versionHistoryProperties);
            this.versionHistoryService = new VersionHistoryService(gitService, versionHistoryProperties);

            logger.info("Plugin initialized (services created)");

        } catch (Exception e) {
            logger.error("Initialization failed", e);
            throw new GitRepositoryException("Init failed", e);
        }
    }

    public void start() throws GitRepositoryException {
        logger.info("Starting Version History Plugin...");

        if (gitService == null) {
            throw new GitRepositoryException("Not initialized. Call init() first.");
        }

        try {
            // Start Git infrastructure (may take time)
            gitService.startGit();

            // Log Git status
            if (gitService.isGitAvailable()) {
                logger.info("✅ Git is AVAILABLE and ready");
            } else {
                logger.warn("⚠️  Git is UNAVAILABLE: {}", gitService.getGitUnavailableReason());
            }

            logger.info("Plugin started successfully");

        } catch (Exception e) {
            logger.error("Start failed", e);
            throw new GitRepositoryException("Start failed", e);
        }
    }

    /**
     * Updates plugin configuration and restarts Git infrastructure.
     *
     * @param newProperties New configuration properties
     * @throws GitRepositoryException if update fails
     */
    public void update(Properties newProperties) throws GitRepositoryException {
        logger.info("Updating Version History Plugin configuration...");

        if (newProperties == null) {
            throw new GitRepositoryException("Properties cannot be null");
        }

        if (gitService == null) {
            throw new GitRepositoryException("Plugin not initialized. Call init() first.");
        }

        try {
            gitService.stopGit();

            versionHistoryProperties.fromProperties(newProperties);

            gitService.startGit();

            if (gitService.isGitAvailable()) {
                logger.info("✅ Git started successfully and is AVAILABLE");
            } else {
                logger.warn("⚠️  Git started but is UNAVAILABLE: {}", gitService.getGitUnavailableReason());
            }

        } catch (Exception e) {
            logger.error("Configuration update FAILED: {}", e.getMessage());

            throw new GitRepositoryException("Configuration update failed: " + e.getMessage(), e);
        }
    }

    public VersionHistoryService getVersionHistoryService() {
        return versionHistoryService;
    }

}
