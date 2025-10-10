/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.plugin;

import static com.mirth.connect.plugins.dynamiclookup.shared.interfaces.LookupTableServletInterface.PERMISSION_ACCESS;

import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.api.util.OperationUtil;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.MergePropertiesInterface;
import com.mirth.connect.plugins.ServicePlugin;
import com.mirth.connect.plugins.dynamiclookup.server.controller.LookupTableController;
import com.mirth.connect.plugins.dynamiclookup.server.exception.LookupTableException;
import com.mirth.connect.plugins.dynamiclookup.shared.interfaces.LookupTableServletInterface;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-05-13 10:25 AM
 */

public class LookupTableServicePlugin implements ServicePlugin, MergePropertiesInterface {
	private final Logger logger = LogManager.getLogger(this.getClass());
	private final LookupTableController lookupTableController = LookupTableController.getInstance();

	@Override
	public void init(Properties properties) {
		try {
			lookupTableController.init(properties);
		} catch (LookupTableException e) {
			logger.error("Error initializing Lookup Table Management System plugin", e);
			throw new RuntimeException("Failed to initialize plugin: " + e.getMessage(), e);
		}
	}

	@Override
	public void update(Properties properties) {
		try {
			lookupTableController.update(properties);
		} catch (LookupTableException e) {
			logger.error("Failed to update Lookup Table Management System plugin", e);
		}
	}

	@Override
	public Properties getDefaultProperties() {
		return new Properties();
	}

	@Override
	public ExtensionPermission[] getExtensionPermissions() {
		ExtensionPermission viewPermission = new ExtensionPermission("Lookup Table Management System", PERMISSION_ACCESS, "Allows to accessing Lookup Table", OperationUtil.getOperationNamesForPermission(PERMISSION_ACCESS, LookupTableServletInterface.class), new String[] {});

		return new ExtensionPermission[] { viewPermission };
	}

	@Override
	public String getPluginPointName() {
		return "Lookup Table Management System";
	}

	@Override
	public void start() {
		try {
			lookupTableController.start();
		} catch (LookupTableException e) {
			logger.error("Failed to start Lookup Table Management System plugin", e);
		}
	}

	@Override
	public void stop() {
		try {
			lookupTableController.stop();
		} catch (LookupTableException e) {
			logger.error("Failed to stop Lookup Table Management System plugin", e);
		}
	}

	@Override
	public void modifyPropertiesOnRestore(Properties properties) throws ControllerException {
		lookupTableController.onRestoreTriggered();
	}
}
