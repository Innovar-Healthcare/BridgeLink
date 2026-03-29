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
import com.innovarhealthcare.channelHistory.server.exception.GitRepositoryException;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.ServicePlugin;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
public class VersionHistoryPlugin implements ServicePlugin {
    private Logger logger = LogManager.getLogger(this.getClass());
    private GitRepositoryController controller = GitRepositoryController.getInstance();
    @Override
    public String getPluginPointName() {
        return "Version History Plugin";
    }
    @Override
    public void start() {
        try {
            controller.start();
        } catch (GitRepositoryException e) {
            logger.error("Failed to start version history service. Error: ", e);
        }
    }
    @Override
    public void stop() {
    }
    @Override
    public void init(Properties properties) {
        try {
            controller.init(properties);
        } catch (GitRepositoryException e) {
            logger.error("Failed to initialize version history service. Error: ", e);
        }
    }
    @Override
    public void update(Properties properties) {
        if (properties == null) {
            return;
        }
        if (!properties.containsKey(VersionControlConstants.VERSION_HISTORY_ENABLE)) {
            return;
        }
        try {
            controller.update(properties);
        } catch (GitRepositoryException e) {
            logger.error("Failed to update version history service. Error: ", e);
        }
    }
    @Override
    public Properties getDefaultProperties() {
        Properties properties = new Properties();
        properties.setProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_REPO_URL, "");
        properties.setProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_BRANCH, "");
        properties.setProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_SSH_KEY, "");
        properties.setProperty(VersionControlConstants.VERSION_HISTORY_ENABLE, String.valueOf(false));
        properties.setProperty(VersionControlConstants.VERSION_HISTORY_AUTO_COMMIT_ENABLE, String.valueOf(false));
        return properties;
    }
    @Override
    public ExtensionPermission[] getExtensionPermissions() {
        return new ExtensionPermission[]{};
    }
}
