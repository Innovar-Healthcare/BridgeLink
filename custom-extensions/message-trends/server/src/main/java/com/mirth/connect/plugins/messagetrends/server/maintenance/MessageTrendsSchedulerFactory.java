/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.util.Objects;

import com.mirth.connect.plugins.messagetrends.server.service.MessageTrendsService;

/**
 * Factory that wires runners and returns a ready-to-use MessageTrendsScheduler.
 * Keeps the plugin class minimal by centralizing construction logic here.
 */
public final class MessageTrendsSchedulerFactory {

	private MessageTrendsSchedulerFactory() {
		// utility
	}

	/**
	 * Build a scheduler using default runtime config
	 * MessageTrendsConfig.defaultConfig() `enabled` externally before starting.
	 */
	public static MessageTrendsScheduler build(MessageTrendsService service, String serverId) {
		return build(service, serverId, MessageTrendsConfig.defaultConfig());
	}

	/**
	 * Build a scheduler using an explicit runtime config snapshot. The scheduler
	 * will be created with runners configured from the provided config. This method
	 * does NOT start the scheduler.
	 */
	public static MessageTrendsScheduler build(MessageTrendsService service, String serverId, MessageTrendsConfig config) {
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(config, "config");

		// --- Wire runners from config ---
		MinuteFlushRunner flushRunner = new MinuteFlushRunner(service, config.getClock(), serverId);
		flushRunner.setEnabled(config.isFlushEnabled());

		RollupRunner rollupRunner = new RollupRunner(service, config.getClock(), config.getRollupFixedRateSeconds(), serverId);
		rollupRunner.setEnabled(config.isRollupEnabled());

		PurgeRunner purgeRunner = new PurgeRunner(service, config.getClock(), config.getRetentionByBucket(), config.getPurgeFixedRateSeconds(), config.getPurgeThrottleMs());
		purgeRunner.setEnabled(config.isPurgeEnabled());

		// --- Create orchestrator ---
		return new MessageTrendsScheduler(flushRunner, rollupRunner, purgeRunner);
	}

	/**
	 * Convenience: build and start in one call. Returns the started scheduler
	 * instance.
	 */
	public static MessageTrendsScheduler buildAndStart(MessageTrendsService service, String serverId, MessageTrendsConfig config) {
		MessageTrendsScheduler scheduler = build(service, serverId, config);
		scheduler.start();
		return scheduler;
	}
}
