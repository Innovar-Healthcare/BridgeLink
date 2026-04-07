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

/**
 * Operations adapter for ChannelHistoryTabPanel.
 * Wraps the tab panel's action methods to provide operations for ChannelEditContext.
 */
public class ChannelHistoryOperations {

    private final Runnable diffAction;
    private final Runnable commitPushAction;
    private final Runnable pullAction;
    private final Runnable revertAction;

    /**
     * Creates operations from action runnables
     *
     * @param diffAction       Action to show diff window
     * @param commitPushAction Action to commit and push changes
     * @param pullAction       Action to pull/reload history
     * @param revertAction     Action to revert to selected version
     */
    public ChannelHistoryOperations(Runnable diffAction, Runnable commitPushAction, Runnable pullAction, Runnable revertAction) {
        this.diffAction = diffAction;
        this.commitPushAction = commitPushAction;
        this.pullAction = pullAction;
        this.revertAction = revertAction;
    }

    /**
     * Shows diff window comparing current channel with repository version
     */
    public void showDiff() {
        if (diffAction != null) {
            diffAction.run();
        }
    }

    /**
     * Commits current changes and pushes to repository
     */
    public void commitAndPush() {
        if (commitPushAction != null) {
            commitPushAction.run();
        }
    }

    /**
     * Pulls latest changes from repository (reloads history)
     */
    public void pull() {
        if (pullAction != null) {
            pullAction.run();
        }
    }

    /**
     * Reverts channel to selected version from history
     */
    public void revert() {
        if (revertAction != null) {
            revertAction.run();
        }
    }
}