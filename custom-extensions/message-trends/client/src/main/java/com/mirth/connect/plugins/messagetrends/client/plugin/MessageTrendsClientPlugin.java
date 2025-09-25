/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.client.plugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.SettingsPanelPlugin;
import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsSettingPanel;

public class MessageTrendsClientPlugin extends SettingsPanelPlugin {
	private final Logger logger = LogManager.getLogger(this.getClass());
	private MessageTrendsSettingPanel settingPanel;

	public MessageTrendsClientPlugin(String name) {
		super(name);
		try {
			this.settingPanel = new MessageTrendsSettingPanel("Message Trends", this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public MessageTrendsSettingPanel getSettingsPanel() {
		return settingPanel;
	}

	@Override
	public String getPluginPointName() {
		return "Message Trends Management System";
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
