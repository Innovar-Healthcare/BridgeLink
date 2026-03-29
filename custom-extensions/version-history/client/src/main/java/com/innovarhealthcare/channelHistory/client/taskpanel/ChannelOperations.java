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

import com.innovarhealthcare.channelHistory.client.dialog.ImportChannelDialog;
import com.mirth.connect.client.ui.Frame;

/**
 * Business operations for Channel context.
 */
public class ChannelOperations {

    private final Frame parent;

    public ChannelOperations(Frame parent) {
        this.parent = parent;
    }

    public void showDiff() {
    }

    public void commitAndPush() {
    }

    public void pull() {
    }

    public void revert() {
    }

    public void importChannel() {
        new ImportChannelDialog(parent);
    }
}
