/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.taskpanel;

import com.innovarhealthcare.channelHistory.client.dialog.CommitMessageDialog;
import com.innovarhealthcare.channelHistory.client.dialog.GlobalScriptsHistoryDialog;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.mirth.connect.client.ui.Frame;

import java.util.Map;

/**
 * Operations for Global Script history management in the left task panel.
 * Provides actions for diff, commit/push, and history viewing.
 */
public class GlobalScriptOperations {

    private final Frame parent;

    public GlobalScriptOperations(Frame parent) {
        this.parent = parent;
    }

    /**
     * Shows global script template history dialog
     */
    public void showHistory() {
        new GlobalScriptsHistoryDialog(parent);
    }

    /**
     * Commits current MC global scripts and pushes to Git repository.
     * Saves all 4 script types as single commit to maintain MC behavior consistency.
     */
    public void commitAndPush() {
        if (parent.isSaveEnabled()) {
            parent.alertWarning(parent, "Please save your changes before commit global scripts to remote repository.");
            return;
        }

        // Read UI data on EDT before handing off to background thread
        Map<String, String> globalScripts = parent.globalScriptsPanel.exportAllScripts();
        if (globalScripts == null || globalScripts.isEmpty()) {
            parent.alertError(parent, "No global scripts found to commit.");
            return;
        }

        CommitMessageDialog.CommitAction commitAction = msg -> {
            String userId = String.valueOf(parent.mirthClient.getCurrentUser().getId());
            VersionHistoryServiceClient.getInstance().commitAndPushGlobalScripts(globalScripts, msg, userId);
        };

        Runnable onSuccess = () ->
                parent.alertInformation(parent, "Global scripts committed and pushed successfully.");

        new CommitMessageDialog(parent, commitAction, onSuccess, null).setVisible(true);
    }
}
