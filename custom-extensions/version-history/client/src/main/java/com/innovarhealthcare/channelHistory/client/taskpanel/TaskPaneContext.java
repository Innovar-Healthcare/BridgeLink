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

import java.util.Set;

/**
 * Strategy interface for task pane behaviors.
 * Each context represents different user workflows (Channel edit, Code template edit, etc.)
 */
public interface TaskPaneContext {

    /**
     * Called when user clicks Diff task
     */
    void onDiff();

    /**
     * Called when user clicks Commit & Push task
     */
    void onCommitPush();

    /**
     * Called when user clicks Pull task
     */
    void onPull();

    /**
     * Called when user clicks Revert task
     */
    void onRevert();

    /**
     * Called when user clicks History task
     */
    void onHistory();

    /**
     * Called when user clicks Import task
     */
    void onImport();

    /**
     * Called when user clicks Save Libraries task
     */
    void onSaveLibraries();

    /**
     * Returns set of task action names that should be visible for this context
     *
     * @return Set of task action constants (e.g., TASK_HISTORY, TASK_IMPORT)
     */
    Set<String> getVisibleTasks();
}
