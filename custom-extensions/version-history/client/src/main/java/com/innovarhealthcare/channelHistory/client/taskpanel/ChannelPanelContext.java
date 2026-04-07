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

import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_IMPORT;

/**
 * Context for Channel Panel view (viewing channel list).
 * Shows: Import task only
 */
public class ChannelPanelContext extends BaseTaskPaneContext {

    private final ChannelOperations operations;

    public ChannelPanelContext(ChannelOperations operations) {
        this.operations = operations;
    }

    @Override
    public void onImport() {
        operations.importChannel();
    }

    @Override
    public Set<String> getVisibleTasks() {
        return Set.of(TASK_IMPORT);
    }
}