/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.shared.model;

import java.util.Properties;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;

/* This class is MUTABLE to support in-place configuration updates without
 * recreating service objects (avoiding memory leaks).
 */
public class VersionHistoryProperties {
    public static final String VERSION_HISTORY_ENABLE = "versionHistory.enable";
    public static final String VERSION_HISTORY_AUTO_COMMIT_ENABLE = "versionHistory.auto.commit.enable";
    public static final String VERSION_HISTORY_AUTO_COMMIT_PROMPT = "versionHistory.auto.commit.prompt";
    public static final String VERSION_HISTORY_AUTO_COMMIT_MSG = "versionHistory.auto.commit.message";
    public static final String VERSION_HISTORY_SYNC_DELETE = "versionHistory.syncDelete";
    public static final String VERSION_HISTORY_LOG_MAX_COUNT = "versionHistory.history.logMaxCount";

    public static final String VERSION_HISTORY_REMOTE_REPO_URL = "versionHistory.remote.url";
    public static final String VERSION_HISTORY_REMOTE_BRANCH = "versionHistory.remote.branch";
    public static final String VERSION_HISTORY_REMOTE_SSH_KEY = "versionHistory.remote.ssh.key";
    public static final String VERSION_HISTORY_REMOTE_SSH_KEY_PATH = "versionHistory.remote.ssh.keyPath";
    public static final String VERSION_HISTORY_REMOTE_AUTH_TYPE = "versionHistory.remote.authType";
    public static final String VERSION_HISTORY_REMOTE_HTTPS_USERNAME = "versionHistory.remote.https.username";
    public static final String VERSION_HISTORY_REMOTE_HTTPS_PASSWORD = "versionHistory.remote.https.password";
    public static final String VERSION_HISTORY_REMOTE_HTTPS_CREDENTIALS_PATH = "versionHistory.remote.https.credentialsPath";

    private boolean enableVersionHistory;
    private boolean enableAutoCommit;
    private boolean enableAutoCommitPrompt;
    private String autoCommitMsg;
    private boolean enableSyncDelete;
    private int historyLogMaxCount;
    private GitSettings gitSettings;

    public VersionHistoryProperties() {
        enableVersionHistory = false;
        enableAutoCommit = false;
        enableAutoCommitPrompt = false;
        autoCommitMsg = "";
        enableSyncDelete = false;
        historyLogMaxCount = VersionControlConstants.REPO_LOG_MAX_COUNT;
        gitSettings = null;
    }

    public VersionHistoryProperties(Properties properties) {
        fromProperties(properties);
    }

    public Properties toProperties() {
        Properties properties = new Properties();

        // Boolean properties - always safe
        properties.setProperty(VERSION_HISTORY_ENABLE, String.valueOf(enableVersionHistory));
        properties.setProperty(VERSION_HISTORY_AUTO_COMMIT_ENABLE, String.valueOf(enableAutoCommit));
        properties.setProperty(VERSION_HISTORY_AUTO_COMMIT_PROMPT, String.valueOf(enableAutoCommitPrompt));
        properties.setProperty(VERSION_HISTORY_SYNC_DELETE, String.valueOf(enableSyncDelete));
        properties.setProperty(VERSION_HISTORY_LOG_MAX_COUNT, String.valueOf(historyLogMaxCount));

        // String property - handle null
        properties.setProperty(VERSION_HISTORY_AUTO_COMMIT_MSG, autoCommitMsg != null ? autoCommitMsg : "");

        // Git settings - handle null safely
        if (gitSettings != null) {
            properties.setProperty(VERSION_HISTORY_REMOTE_REPO_URL, gitSettings.getRemoteRepositoryUrl() != null ? gitSettings.getRemoteRepositoryUrl() : "");
            properties.setProperty(VERSION_HISTORY_REMOTE_BRANCH, gitSettings.getBranchName() != null ? gitSettings.getBranchName() : "");
            properties.setProperty(VERSION_HISTORY_REMOTE_SSH_KEY, gitSettings.getSshPrivateKey() != null ? gitSettings.getSshPrivateKey() : "");
            properties.setProperty(VERSION_HISTORY_REMOTE_SSH_KEY_PATH, gitSettings.getSshPrivateKeyPath() != null ? gitSettings.getSshPrivateKeyPath() : "");
            properties.setProperty(VERSION_HISTORY_REMOTE_AUTH_TYPE, gitSettings.getAuthType() != null ? gitSettings.getAuthType() : GitSettings.AUTH_TYPE_SSH);
            properties.setProperty(VERSION_HISTORY_REMOTE_HTTPS_USERNAME, gitSettings.getHttpsUsername() != null ? gitSettings.getHttpsUsername() : "");
            properties.setProperty(VERSION_HISTORY_REMOTE_HTTPS_PASSWORD, gitSettings.getHttpsPassword() != null ? gitSettings.getHttpsPassword() : "");
            properties.setProperty(VERSION_HISTORY_REMOTE_HTTPS_CREDENTIALS_PATH, gitSettings.getHttpsCredentialsPath() != null ? gitSettings.getHttpsCredentialsPath() : "");
        } else {
            // GitSettings is null - set empty defaults
            properties.setProperty(VERSION_HISTORY_REMOTE_REPO_URL, "");
            properties.setProperty(VERSION_HISTORY_REMOTE_BRANCH, "");
            properties.setProperty(VERSION_HISTORY_REMOTE_SSH_KEY, "");
            properties.setProperty(VERSION_HISTORY_REMOTE_SSH_KEY_PATH, "");
            properties.setProperty(VERSION_HISTORY_REMOTE_AUTH_TYPE, GitSettings.AUTH_TYPE_SSH);
            properties.setProperty(VERSION_HISTORY_REMOTE_HTTPS_USERNAME, "");
            properties.setProperty(VERSION_HISTORY_REMOTE_HTTPS_PASSWORD, "");
            properties.setProperty(VERSION_HISTORY_REMOTE_HTTPS_CREDENTIALS_PATH, "");
        }

        return properties;
    }

    public void fromProperties(Properties properties) {

        // Boolean properties with defaults
        enableVersionHistory = getBooleanProperty(properties, VERSION_HISTORY_ENABLE, false);
        enableAutoCommit = getBooleanProperty(properties, VERSION_HISTORY_AUTO_COMMIT_ENABLE, false);
        enableAutoCommitPrompt = getBooleanProperty(properties, VERSION_HISTORY_AUTO_COMMIT_PROMPT, false);
        enableSyncDelete = getBooleanProperty(properties, VERSION_HISTORY_SYNC_DELETE, false);
        historyLogMaxCount = getIntProperty(properties, VERSION_HISTORY_LOG_MAX_COUNT, VersionControlConstants.REPO_LOG_MAX_COUNT);

        // String properties
        autoCommitMsg = getStringProperty(properties, VERSION_HISTORY_AUTO_COMMIT_MSG, "");
        String remoteRepositoryUrl = getStringProperty(properties, VERSION_HISTORY_REMOTE_REPO_URL, "");
        String branchName = getStringProperty(properties, VERSION_HISTORY_REMOTE_BRANCH, "");
        String sshPrivateKey = getStringProperty(properties, VERSION_HISTORY_REMOTE_SSH_KEY, "");
        String sshPrivateKeyPath = getStringProperty(properties, VERSION_HISTORY_REMOTE_SSH_KEY_PATH, "");
        String authType = getStringProperty(properties, VERSION_HISTORY_REMOTE_AUTH_TYPE, GitSettings.AUTH_TYPE_SSH);
        String httpsUsername = getStringProperty(properties, VERSION_HISTORY_REMOTE_HTTPS_USERNAME, "");
        String httpsPassword = getStringProperty(properties, VERSION_HISTORY_REMOTE_HTTPS_PASSWORD, "");
        String httpsCredentialsPath = getStringProperty(properties, VERSION_HISTORY_REMOTE_HTTPS_CREDENTIALS_PATH, "");

        // Update GitSettings IN-PLACE or create new if null
        if (gitSettings == null) {
            gitSettings = new GitSettings(remoteRepositoryUrl, branchName, sshPrivateKey);
        } else {
            // Update existing GitSettings object (preserves references in GitRepositoryService)
            gitSettings.setRemoteRepositoryUrl(remoteRepositoryUrl);
            gitSettings.setBranchName(branchName);
            gitSettings.setSshPrivateKey(sshPrivateKey);
        }
        gitSettings.setSshPrivateKeyPath(sshPrivateKeyPath);
        gitSettings.setAuthType(authType);
        gitSettings.setHttpsUsername(httpsUsername);
        gitSettings.setHttpsPassword(httpsPassword);
        gitSettings.setHttpsCredentialsPath(httpsCredentialsPath);
    }

    public boolean isEnableAutoCommit() {
        return enableAutoCommit;
    }

    public void setEnableAutoCommit(boolean enableAutoCommit) {
        this.enableAutoCommit = enableAutoCommit;
    }

    public boolean isEnableVersionHistory() {
        return enableVersionHistory;
    }

    public void setEnableVersionHistory(boolean enableVersionHistory) {
        this.enableVersionHistory = enableVersionHistory;
    }

    public GitSettings getGitSettings() {
        return gitSettings;
    }

    public boolean isEnableSyncDelete() {
        return enableSyncDelete;
    }

    public void setEnableSyncDelete(boolean enableSyncDelete) {
        this.enableSyncDelete = enableSyncDelete;
    }

    public int getHistoryLogMaxCount() {
        return historyLogMaxCount;
    }

    public void setHistoryLogMaxCount(int historyLogMaxCount) {
        this.historyLogMaxCount = historyLogMaxCount;
    }

    public boolean isEnableAutoCommitPrompt() {
        return enableAutoCommitPrompt;
    }

    public void setEnableAutoCommitPrompt(boolean enableAutoCommitPrompt) {
        this.enableAutoCommitPrompt = enableAutoCommitPrompt;
    }

    public String getAutoCommitMsg() {
        return autoCommitMsg;
    }

    public void setAutoCommitMsg(String autoCommitMsg) {
        this.autoCommitMsg = autoCommitMsg;
    }

    public void setGitSettings(GitSettings gitSettings) {
        this.gitSettings = gitSettings;
    }

    /**
     * Helper: Get boolean property with default
     */
    private boolean getBooleanProperty(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Helper: Get int property with default. Falls back to default if missing or unparseable.
     */
    private int getIntProperty(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Helper: Get string property with default
     */
    private String getStringProperty(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}
