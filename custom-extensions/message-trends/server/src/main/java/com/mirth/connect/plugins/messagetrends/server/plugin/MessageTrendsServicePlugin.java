/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.server.plugin;

import static com.mirth.connect.plugins.messagetrends.shared.interfaces.MessageTrendsServletInterface.PERMISSION_ACCESS;

import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.api.util.OperationUtil;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.MergePropertiesInterface;
import com.mirth.connect.plugins.ServicePlugin;
import com.mirth.connect.plugins.messagetrends.server.controller.MessageTrendsController;
import com.mirth.connect.plugins.messagetrends.server.exception.MessageTrendsException;
import com.mirth.connect.plugins.messagetrends.shared.interfaces.MessageTrendsServletInterface;

public class MessageTrendsServicePlugin implements ServicePlugin, MergePropertiesInterface {
	private final Logger logger = LogManager.getLogger(this.getClass());
	private final MessageTrendsController messageTrendsController = MessageTrendsController.getInstance();

	@Override
	public void init(Properties properties) {
		try {
			messageTrendsController.init(properties);
		} catch (MessageTrendsException e) {
			logger.error("Failed to initialize Message Trends Management System plugin", e);
		}
	}

	@Override
	public void update(Properties properties) {
		try {
			messageTrendsController.update(properties);
		} catch (MessageTrendsException e) {
			logger.error("Failed to update Message Trends Management System plugin", e);
		}
	}

	@Override
	public void start() {
		try {
			messageTrendsController.start();
		} catch (MessageTrendsException e) {
			logger.error("Failed to start Message Trends Management System plugin", e);
		}
	}

	@Override
	public void stop() {
		try {
			messageTrendsController.stop();
		} catch (MessageTrendsException e) {
			logger.error("Failed to stop Message Trends Management System plugin", e);
		}
	}

	@Override
	public Properties getDefaultProperties() {
		return new Properties();
	}

	@Override
	public ExtensionPermission[] getExtensionPermissions() {
		ExtensionPermission viewPermission = new ExtensionPermission("Message Trends Management System", PERMISSION_ACCESS, "Allows to accessing Message Trends", OperationUtil.getOperationNamesForPermission(PERMISSION_ACCESS, MessageTrendsServletInterface.class), new String[] {});

		return new ExtensionPermission[] { viewPermission };
	}

	@Override
	public String getPluginPointName() {
		return "Message Trends Management System";
	}

	@Override
	public void modifyPropertiesOnRestore(Properties properties) throws ControllerException {
		messageTrendsController.onRestoreTriggered();
	}
}
