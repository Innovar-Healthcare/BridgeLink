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

import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_COMMIT_PUSH;
import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_HISTORY;

/**
 * Context for Global Script history operations in the left task panel.
 * Extends BaseTaskPaneContext to provide diff, commit/push, and history actions.
 */
public class GlobalScriptEditContext extends BaseTaskPaneContext {

    private final GlobalScriptOperations operations;

    /**
     * Creates context with global script history operations
     *
     * @param operations Operations to perform when tasks are clicked
     */
    public GlobalScriptEditContext(GlobalScriptOperations operations) {
        this.operations = operations;
    }

    /**
     * Handles commit/push task click - saves current MC scripts to Git
     */
    @Override
    public void onCommitPush() {
        operations.commitAndPush();
    }

    /**
     * Handles history task click - opens history dialog with commit list
     */
    @Override
    public void onHistory() {
        operations.showHistory();
    }

    /**
     * Defines which tasks are visible in the left panel for Global Scripts
     * Order: Diff → Commit & Push → History
     *
     * @return Set of visible task names
     */
    @Override
    public Set<String> getVisibleTasks() {
        return Set.of(TASK_COMMIT_PUSH, TASK_HISTORY);
    }
}
