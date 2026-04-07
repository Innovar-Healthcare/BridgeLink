/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.shared;

/**
 * @author Jim(Zi Min) Weng
 * @create 2024-05-24 5:48 AM
 */
public class VersionControlConstants {
    public static final String PLUGIN_NAME = "Version History Plugin";
    public static final String PLUGIN_POINTNAME = "Innovar Channel History";

    public static final String VERSION_HISTORY_REMOTE_REPO_URL = "versionHistory.remote.url";
    public static final String VERSION_HISTORY_REMOTE_BRANCH = "versionHistory.remote.branch";
    public static final String VERSION_HISTORY_REMOTE_SSH_KEY = "versionHistory.remote.ssh.key";
    public static final String VERSION_HISTORY_ENABLE = "versionHistory.enable";
    public static final String VERSION_HISTORY_AUTO_COMMIT_ENABLE = "versionHistory.auto.commit.enable";

    /** Maximum number of commits fetched for the repository history log. */
    public static final int REPO_LOG_MAX_COUNT = 1000;

    public static final String MODE_CHANNEL = "MODE_CHANNEL";
    public static final String MODE_CODE_TEMPLATE = "MODE_CODE_TEMPLATE";
    public static final String MODE_CODE_TEMPLATE_LIBRARY = "MODE_CODE_TEMPLATE_LIBRARY";
    public static final String MODE_GLOBAL_SCRIPTS = "MODE_GLOBAL_SCRIPTS";

    public VersionControlConstants() {
    }
}