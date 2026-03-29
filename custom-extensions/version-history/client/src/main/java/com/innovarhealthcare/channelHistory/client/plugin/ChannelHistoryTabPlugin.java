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
import com.innovarhealthcare.channelHistory.client.panel.ChannelHistoryTabPanel;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.mirth.connect.client.ui.AbstractChannelTabPanel;
import com.mirth.connect.plugins.ChannelTabPlugin;
public class ChannelHistoryTabPlugin extends ChannelTabPlugin {
    private ChannelHistoryTabPanel tabPanel;
    public ChannelHistoryTabPlugin(String name) {
        super(VersionControlConstants.PLUGIN_POINTNAME);
    }
    @Override
    public void start() {
        tabPanel = new ChannelHistoryTabPanel(parent);
    }
    @Override
    public AbstractChannelTabPanel getChannelTabPanel() {
        return tabPanel;
    }
    @Override
    public String getPluginPointName() {
        return VersionControlConstants.PLUGIN_POINTNAME;
    }
}
