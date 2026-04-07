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
import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_DIFF;
import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_PULL;
import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_REVERT;

/**
 * Context for Channel History tab (when viewing channel history).
 * Shows: Diff, Commit & Push, Pull, Revert tasks
 * Works with ChannelHistoryOperations to perform version control actions.
 */
public class ChannelHistoryContext extends BaseTaskPaneContext {

    private final ChannelHistoryOperations operations;

    /**
     * Creates context with channel history operations
     *
     * @param operations Operations to perform when tasks are clicked
     */
    public ChannelHistoryContext(ChannelHistoryOperations operations) {
        this.operations = operations;
    }

    @Override
    public void onDiff() {
        operations.showDiff();
    }

    @Override
    public void onCommitPush() {
        operations.commitAndPush();
    }

    @Override
    public void onPull() {
        operations.pull();
    }

    @Override
    public void onRevert() {
        operations.revert();
    }

    @Override
    public Set<String> getVisibleTasks() {
        return Set.of(TASK_DIFF, TASK_COMMIT_PUSH, TASK_PULL, TASK_REVERT);
    }
}