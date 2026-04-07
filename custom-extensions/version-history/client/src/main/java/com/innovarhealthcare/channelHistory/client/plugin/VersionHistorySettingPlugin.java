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
import com.innovarhealthcare.channelHistory.client.panel.VersionHistorySettingPanel;
import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.plugins.SettingsPanelPlugin;
/**
 * @author Jim(Zi Min) Weng
 * @create 2024-04-19 12:21 PM
 */
public class VersionHistorySettingPlugin extends SettingsPanelPlugin {
    private VersionHistorySettingPanel settingPanel;
    public VersionHistorySettingPlugin(String name) {
        super("Version History Plugin");
        try {
            this.settingPanel = new VersionHistorySettingPanel("Version History", this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public AbstractSettingsPanel getSettingsPanel() {
        return settingPanel;
    }
    @Override
    public String getPluginPointName() {
        return "Version History Plugin";
    }
    @Override
    public void start() {
    }
    @Override
    public void stop() {
    }
    @Override
    public void reset() {
    }
}