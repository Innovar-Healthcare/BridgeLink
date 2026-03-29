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

import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.mirth.connect.client.core.TaskConstants;
import com.mirth.connect.client.ui.Frame;
import org.jdesktop.swingx.JXTaskPane;

/**
 * Manages task pane context switching based on which panel is shown.
 * Detects when panels are activated and switches the task pane to the appropriate context.
 */
public class TaskPaneContextManager {

    private final Frame parent;
    private final VersionHistoryTaskPane versionHistoryPane;
    private final Map<String, Supplier<TaskPaneContext>> contextFactories;

    public TaskPaneContextManager(Frame parent, VersionHistoryTaskPane versionHistoryPane) {
        this.parent = parent;
        this.versionHistoryPane = versionHistoryPane;
        this.contextFactories = new HashMap<>();

        registerContextFactories();
    }

    /**
     * Registers context factories for each panel type
     */
    private void registerContextFactories() {
        contextFactories.put(TaskConstants.CODE_TEMPLATE_KEY, this::createCodeTemplateContext);
        contextFactories.put(TaskConstants.CHANNEL_KEY, this::createChannelContext);
        contextFactories.put(TaskConstants.GLOBAL_SCRIPT_KEY, this::createGlobalScriptContext);
    }

    /**
     * Sets up listeners on relevant task panes to detect context switches
     */
    public void setupListeners() {
        Map<String, JXTaskPane> taskPanes = findTaskPanes();

        for (Map.Entry<String, JXTaskPane> entry : taskPanes.entrySet()) {
            String panelKey = entry.getKey();
            JXTaskPane pane = entry.getValue();

            attachListener(pane, panelKey);
        }
    }

    /**
     * Finds relevant task panes in the container
     */
    private Map<String, JXTaskPane> findTaskPanes() {
        Map<String, JXTaskPane> found = new HashMap<>();

        Component[] components = parent.taskPaneContainer.getComponents();

        for (Component comp : components) {
            if (!(comp instanceof JXTaskPane)) {
                continue;
            }

            JXTaskPane pane = (JXTaskPane) comp;
            String name = pane.getName();

            if (TaskConstants.CHANNEL_KEY.equals(name)) {
                found.put(name, pane);
            } else if (TaskConstants.CODE_TEMPLATE_KEY.equals(name)) {
                found.put(name, pane);
            } else if (TaskConstants.GLOBAL_SCRIPT_KEY.equals(name)) {
                found.put(name, pane);
            }

            // Add more panel types as needed
        }

        return found;
    }

    /**
     * Attaches component listener to detect when panel is shown
     */
    private void attachListener(JXTaskPane pane, String panelKey) {
        pane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                onPanelShown(panelKey);
            }
        });
    }

    /**
     * Called when a panel is shown - switches to appropriate context
     */
    private void onPanelShown(String panelKey) {
        Supplier<TaskPaneContext> factory = contextFactories.get(panelKey);

        if (factory != null) {
            TaskPaneContext context = factory.get();
            versionHistoryPane.setContext(context);
        }
    }

    /**
     * Creates context for Code Template edit view
     */
    private TaskPaneContext createCodeTemplateContext() {
        CodeTemplateOperations operations = new CodeTemplateOperations(parent);
        return new CodeTemplateEditContext(operations);
    }

    private TaskPaneContext createChannelContext() {
        ChannelOperations operations = new ChannelOperations(parent);
        return new ChannelPanelContext(operations);
    }

    private TaskPaneContext createGlobalScriptContext() {
        GlobalScriptOperations operations = new GlobalScriptOperations(parent);
        return new GlobalScriptEditContext(operations);
    }

    /**
     * Cleanup method - remove listeners if needed
     */
    public void cleanup() {
        versionHistoryPane.setContext(null);
    }
}