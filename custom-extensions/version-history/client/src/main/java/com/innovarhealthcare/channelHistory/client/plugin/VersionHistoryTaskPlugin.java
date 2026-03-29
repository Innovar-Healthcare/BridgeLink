/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */
package com.innovarhealthcare.channelHistory.client.plugin;
import javax.swing.SwingUtilities;
import com.innovarhealthcare.channelHistory.client.taskpanel.TaskPaneContextManager;
import com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane;
import com.mirth.connect.client.ui.components.MirthTreeTable;
import com.mirth.connect.plugins.TaskPlugin;
import org.jdesktop.swingx.JXTaskPane;
/*
 * Task plugin for Version History functionality.
 * Manages the lifecycle of the Version History task pane and context switching.
 */ public class VersionHistoryTaskPlugin extends TaskPlugin {
    private TaskPaneContextManager contextManager;
    public VersionHistoryTaskPlugin(String name) {
        super(name);
    }
    @Override
    public void onRowSelected(MirthTreeTable mirthTreeTable) {
    }
    @Override
    public String getPluginPointName() {
        return "";
    }
    @Override
    public void start() {
        // Create and add task pane to UI
        VersionHistoryTaskPane taskPane = VersionHistoryTaskPane.getInstance();
        taskPane.createAndAdd();
        // Setup context manager to handle context switching
        contextManager = new TaskPaneContextManager(parent, taskPane);
        // Setup listeners after UI is ready
        SwingUtilities.invokeLater(() -> {
            contextManager.setupListeners();
        });
    }
    @Override
    public void stop() {
        if (contextManager != null) {
            contextManager.cleanup();
        }
        VersionHistoryTaskPane.reset();
    }
    @Override
    public void reset() {
        // Reset if needed
    }
    /**
     * Called when a row is selected in channel table
     * Can be used for future enhancements
     */
    public void onRowSelected(Object channelTable) {
        // Future: Handle row selection if needed
    }
    /**
     * Called when a row is deselected
     * Can be used for future enhancements
     */
    public void onRowDeselected() {
        // Future: Handle deselection if needed
    }
    /**
     * Gets the task pane instance
     *
     * @return The Version History task pane
     */
    public JXTaskPane getTaskPane() {
        return VersionHistoryTaskPane.getInstance().getTaskPane();
    }
}
