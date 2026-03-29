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

import com.innovarhealthcare.channelHistory.client.dialog.CodeTemplateHistoryDialogWithTaskPane;
import com.innovarhealthcare.channelHistory.client.dialog.CommitMessageDialog;
import com.innovarhealthcare.channelHistory.client.dialog.ConflictResolutionDialog;
import com.innovarhealthcare.channelHistory.client.dialog.ImportCodeTemplateDialog;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Business operations for Code Template context.
 * Encapsulates logic extracted from plugin.
 */
public class CodeTemplateOperations {

    private final Frame parent;

    public CodeTemplateOperations(Frame parent) {
        this.parent = parent;
    }

    /**
     * Shows code template history dialog
     */
    public void showHistory() {
        if (parent.isSaveEnabled()) {
            parent.alertWarning(parent, "Please save your changes before viewing history.");
            return;
        }

        String selectedId = parent.codeTemplatePanel.getCurrentSelectedId();

        if (selectedId == null) {
            parent.alertError(parent, "No code template selected.");
            return;
        }

        if (parent.codeTemplatePanel.getCachedCodeTemplateLibraries().containsKey(selectedId)) {
            parent.alertError(parent, "Please select a code template, not a library.");
            return;
        }

        new CodeTemplateHistoryDialogWithTaskPane(parent, selectedId);
    }

    /**
     * Shows import code template dialog
     */
    public void importTemplate() {
        if (parent.isSaveEnabled()) {
            parent.alertWarning(parent, "Please save your changes before importing.");
            return;
        }

        new ImportCodeTemplateDialog(parent);
    }

    /**
     * Saves libraries to repository
     */
    public void saveLibraries() {
        if (parent.isSaveEnabled()) {
            parent.alertWarning(parent, "Please save your changes before commit libraries to remote repository.");
            return;
        }

        // Read UI data on EDT before handing off to background thread
        Map<String, CodeTemplateLibrary> cached = parent.codeTemplatePanel.getCachedCodeTemplateLibraries();
        if (cached == null || cached.isEmpty()) {
            parent.alertError(parent, "No libraries found to commit.");
            return;
        }
        List<CodeTemplateLibrary> libraries = new ArrayList<>(cached.values());

        CommitMessageDialog.CommitAction commitAction = msg -> {
            String userId = String.valueOf(parent.mirthClient.getCurrentUser().getId());
            VersionHistoryServiceClient.getInstance().saveLibraries(libraries, msg, userId);
        };

        Runnable onSuccess = () -> parent.alertInformation(parent, "Libraries committed and pushed successfully.");

        Consumer<Map<String, String>> onConflict = backedUpContent ->
                new ConflictResolutionDialog(PlatformUI.MIRTH_FRAME, VersionHistoryServiceClient.getInstance(), backedUpContent, () ->
                        parent.alertInformation(parent, "Your changes have been restored. Please commit again.")
                ).setVisible(true);

        new CommitMessageDialog(parent, commitAction, onSuccess, onConflict).setVisible(true);
    }
}