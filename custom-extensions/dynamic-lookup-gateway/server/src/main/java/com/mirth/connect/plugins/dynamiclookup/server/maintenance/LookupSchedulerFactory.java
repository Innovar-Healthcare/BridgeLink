/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.maintenance;

import java.util.Objects;

import com.mirth.connect.plugins.dynamiclookup.server.service.LookupService;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupProperties;

/**
 * Factory that wires runners and returns a ready-to-use LookupSchedulerFactory.
 * Keeps the plugin class minimal by centralizing construction logic here.
 */
public final class LookupSchedulerFactory {
	private LookupSchedulerFactory() {
		// utility
	}

	/**
	 * Build a scheduler using default runtime config
	 * MessageTrendsConfig.defaultConfig() `enabled` externally before starting.
	 */
	public static LookupScheduler build(LookupService service) {
		return build(service, LookupProperties.getDefault());
	}

	public static LookupScheduler build(LookupService service, LookupProperties props) {
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(props, "props");

		// --- Wire runners from config ---
		PurgeRunner purgeRunner = new PurgeRunner(service, props.getAuditPruneRetentionDays());

		// --- Create orchestrator ---
		return new LookupScheduler(purgeRunner);
	}
}
