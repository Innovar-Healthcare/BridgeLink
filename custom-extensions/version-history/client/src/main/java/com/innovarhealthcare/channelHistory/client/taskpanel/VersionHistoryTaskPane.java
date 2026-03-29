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

import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Container;
import java.util.Set;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import org.jdesktop.swingx.JXTaskPane;
import org.jdesktop.swingx.JXTaskPaneContainer;


/**
 * Task pane for Version History functionality.
 * Displays context-specific tasks on the left panel based on which view is active.
 * Uses Context pattern to delegate behavior.
 */
public class VersionHistoryTaskPane {

    private static VersionHistoryTaskPane instance;

    private JXTaskPane taskPane;
    private JPopupMenu popupMenu;

    private TaskPaneContext currentContext;

    // Store task indices
    private int diffTaskIndex = -1;
    private int commitPushTaskIndex = -1;
    private int pullTaskIndex = -1;
    private int importTaskIndex = -1;
    private int revertTaskIndex = -1;
    private int historyTaskIndex = -1;
    private int saveLibrariesTaskIndex = -1;

    // Task action constants
    public static final String TASK_DIFF = "doVersionHistoryDiff";
    public static final String TASK_COMMIT_PUSH = "doVersionHistoryCommitPush";
    public static final String TASK_PULL = "doVersionHistoryPull";
    public static final String TASK_IMPORT = "doVersionHistoryImport";
    public static final String TASK_REVERT = "doVersionHistoryRevert";
    public static final String TASK_HISTORY = "doVersionHistoryHistory";
    public static final String TASK_SAVE_LIBRARIES = "doVersionHistorySaveLibraries";

    /**
     * Private constructor for singleton pattern
     */
    private VersionHistoryTaskPane() {
    }

    /**
     * Gets or creates the singleton instance
     *
     * @return The singleton instance
     */
    public static synchronized VersionHistoryTaskPane getInstance() {
        if (instance == null) {
            instance = new VersionHistoryTaskPane();
        }
        return instance;
    }

    /**
     * Checks if the singleton has been initialized
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Gets the parent Frame instance
     *
     * @return The Frame instance
     */
    private Frame getFrame() {
        return PlatformUI.MIRTH_FRAME;
    }

    /**
     * Creates and initializes the Version History task pane
     *
     * @return The created JXTaskPane
     */
    public JXTaskPane create() {
        if (taskPane != null) {
            return taskPane;
        }

        taskPane = new JXTaskPane();
        popupMenu = new JPopupMenu();

        taskPane.setTitle("Version History");
        taskPane.setName("Version History");
        taskPane.setFocusable(false);

        addTasks();

        return taskPane;
    }

    /**
     * Adds all tasks to the task pane
     */
    private void addTasks() {
        Frame parent = getFrame();

        // Diff task
        diffTaskIndex = parent.addTask(TASK_DIFF, "Diff", "Compare current channel with repository version", "", new ImageIcon(Frame.class.getResource("images/application_view_detail.png")), taskPane, popupMenu, this);

        // Commit & Push task
        commitPushTaskIndex = parent.addTask(TASK_COMMIT_PUSH, "Commit & Push", "Save changes and push to repository", "", new ImageIcon(Frame.class.getResource("images/accept.png")), taskPane, popupMenu, this);

        // Pull task
        pullTaskIndex = parent.addTask(TASK_PULL, "Pull", "Pull latest changes from repository", "", new ImageIcon(Frame.class.getResource("images/arrow_refresh.png")), taskPane, popupMenu, this);

        // Revert task
        revertTaskIndex = parent.addTask(TASK_REVERT, "Revert", "Revert channel to selected version", "", new ImageIcon(Frame.class.getResource("images/arrow_undo.png")), taskPane, popupMenu, this);

        // History task
        historyTaskIndex = parent.addTask(TASK_HISTORY, "History", "View channel version history", "", new ImageIcon(Frame.class.getResource("images/arrow_refresh.png")), taskPane, popupMenu, this);

        // Import from Repo task
        importTaskIndex = parent.addTask(TASK_IMPORT, "Import", "Import channel from repository", "", new ImageIcon(Frame.class.getResource("images/report_go.png")), taskPane, popupMenu, this);

        // Save Libraries task
        saveLibrariesTaskIndex = parent.addTask(TASK_SAVE_LIBRARIES, "Save Libraries", "Save library structure to repository", "", new ImageIcon(Frame.class.getResource("images/disk.png")), taskPane, popupMenu, this);
    }

    /**
     * Adds the task pane to the container, positioning it after channelEditTasks
     *
     * @return true if successfully added after channelEditTasks, false if added to end
     */
    public boolean addToContainer() {
        if (taskPane == null) {
            throw new IllegalStateException("Task pane not created. Call create() first.");
        }

        // Don't add if already in container
        if (taskPane.getParent() != null) {
            return true;
        }

        Frame parent = getFrame();
        JXTaskPaneContainer container = parent.taskPaneContainer;
        Component[] components = container.getComponents();

        // Try to insert after channelEditTasks
        for (int i = 0; i < components.length; i++) {
            if (components[i] == parent.channelEditTasks) {
                container.add(taskPane, i + 1);
                return true;
            }
        }

        // Fallback: add to end
        container.add(taskPane);

        return false;
    }

    /**
     * Convenience method to create and add in one call
     *
     * @return The created JXTaskPane
     */
    public JXTaskPane createAndAdd() {
        create();
        addToContainer();
        return taskPane;
    }

    /**
     * Shows the task pane
     */
    public void show() {
        if (taskPane != null) {
            taskPane.setVisible(true);
        }
    }

    /**
     * Hides the task pane
     */
    public void hide() {
        if (taskPane != null) {
            taskPane.setVisible(false);
        }
    }

    /**
     * Checks if the task pane is currently visible
     *
     * @return true if visible
     */
    public boolean isVisible() {
        return taskPane != null && taskPane.isVisible();
    }

    /**
     * Gets the task pane instance
     *
     * @return The JXTaskPane instance
     */
    public JXTaskPane getTaskPane() {
        return taskPane;
    }

    /**
     * Gets the popup menu instance
     *
     * @return The JPopupMenu instance
     */
    public JPopupMenu getPopupMenu() {
        return popupMenu;
    }

    /**
     * Enables or disables all tasks in the pane
     *
     * @param enabled true to enable, false to disable
     */
    public void setTasksEnabled(boolean enabled) {
        if (taskPane != null) {
            Component[] components = taskPane.getContentPane().getComponents();
            for (Component comp : components) {
                comp.setEnabled(enabled);
            }
        }
    }

    // ========== Context Management ==========

    /**
     * Sets the current context for the task pane.
     * This determines which tasks are visible and how they behave.
     *
     * @param context The context to activate, or null to hide the pane
     */
    public void setContext(TaskPaneContext context) {
        this.currentContext = context;

        if (context != null) {
            updateVisibility(context.getVisibleTasks());
            show();
        } else {
            hide();
        }
    }

    /**
     * Updates task visibility based on context's visible tasks
     *
     * @param visibleTasks Set of task action names that should be visible
     */
    private void updateVisibility(Set<String> visibleTasks) {
        setTaskVisible(diffTaskIndex, visibleTasks.contains(TASK_DIFF));
        setTaskVisible(commitPushTaskIndex, visibleTasks.contains(TASK_COMMIT_PUSH));
        setTaskVisible(pullTaskIndex, visibleTasks.contains(TASK_PULL));
        setTaskVisible(revertTaskIndex, visibleTasks.contains(TASK_REVERT));
        setTaskVisible(historyTaskIndex, visibleTasks.contains(TASK_HISTORY));
        setTaskVisible(importTaskIndex, visibleTasks.contains(TASK_IMPORT));
        setTaskVisible(saveLibrariesTaskIndex, visibleTasks.contains(TASK_SAVE_LIBRARIES));
    }

    /**
     * Helper to set visibility of a single task
     *
     * @param taskIndex The index of the task
     * @param visible   True to show, false to hide
     */
    private void setTaskVisible(int taskIndex, boolean visible) {
        if (taskIndex == -1) {
            return;
        }
        Frame parent = getFrame();
        parent.setVisibleTasks(taskPane, popupMenu, taskIndex, taskIndex, visible);
    }

    // ========== MC Core Callback Methods ==========

    /**
     * Called by MC Core when Diff task is clicked.
     * Delegates to the current context.
     */
    public void doVersionHistoryDiff() {
        if (currentContext != null) {
            try {
                currentContext.onDiff();
            } catch (Exception e) {
                getFrame().alertError(getFrame(), "Error executing diff: " + e.getMessage());
            }
        }
    }

    /**
     * Called by MC Core when Commit & Push task is clicked.
     * Delegates to the current context.
     */
    public void doVersionHistoryCommitPush() {
        if (currentContext != null) {
            try {
                currentContext.onCommitPush();
            } catch (Exception e) {
                getFrame().alertError(getFrame(), "Error executing commit & push: " + e.getMessage());
            }
        }
    }

    /**
     * Called by MC Core when Pull task is clicked.
     * Delegates to the current context.
     */
    public void doVersionHistoryPull() {
        if (currentContext != null) {
            try {
                currentContext.onPull();
            } catch (Exception e) {
                getFrame().alertError(getFrame(), "Error executing pull: " + e.getMessage());
            }
        }
    }

    /**
     * Called by MC Core when Revert task is clicked.
     * Delegates to the current context.
     */
    public void doVersionHistoryRevert() {
        if (currentContext != null) {
            try {
                currentContext.onRevert();
            } catch (Exception e) {
                getFrame().alertError(getFrame(), "Error executing revert: " + e.getMessage());
            }
        }
    }

    /**
     * Called by MC Core when History task is clicked.
     * Delegates to the current context.
     */
    public void doVersionHistoryHistory() {
        if (currentContext != null) {
            try {
                currentContext.onHistory();
            } catch (Exception e) {
                getFrame().alertError(getFrame(), "Error showing history: " + e.getMessage());
            }
        }
    }

    /**
     * Called by MC Core when Import task is clicked.
     * Delegates to the current context.
     */
    public void doVersionHistoryImport() {
        if (currentContext != null) {
            try {
                currentContext.onImport();
            } catch (Exception e) {
                getFrame().alertError(getFrame(), "Error importing: " + e.getMessage());
            }
        }
    }

    /**
     * Called by MC Core when Save Libraries task is clicked.
     * Delegates to the current context.
     */
    public void doVersionHistorySaveLibraries() {
        if (currentContext != null) {
            try {
                currentContext.onSaveLibraries();
            } catch (Exception e) {
                getFrame().alertError(getFrame(), "Error saving libraries: " + e.getMessage());
            }
        }
    }

    // ========== Cleanup ==========

    /**
     * Resets the singleton instance (cleanup).
     * Should be called when plugin is stopped.
     */
    public static synchronized void reset() {
        if (instance != null) {
            instance.remove();
            instance = null;
        }
    }

    /**
     * Removes the task pane from its container
     */
    public void remove() {
        if (taskPane != null && taskPane.getParent() != null) {
            Container parent = taskPane.getParent();
            parent.remove(taskPane);
            parent.revalidate();
            parent.repaint();
        }
    }
}
