/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.server.controller;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.messagetrends.server.dao.MessageStatisticsTimeseriesDao;
import com.mirth.connect.plugins.messagetrends.server.dao.impl.MyBatisMessageStatisticsTimeseriesDao;
import com.mirth.connect.plugins.messagetrends.server.exception.MessageTrendsException;
import com.mirth.connect.plugins.messagetrends.server.maintenance.MessageTrendsConfig;
import com.mirth.connect.plugins.messagetrends.server.maintenance.MessageTrendsScheduler;
import com.mirth.connect.plugins.messagetrends.server.maintenance.MessageTrendsSchedulerFactory;
import com.mirth.connect.plugins.messagetrends.server.migration.MessageTrendsDatabaseMigrator;
import com.mirth.connect.plugins.messagetrends.server.service.MessageTrendsService;
import com.mirth.connect.plugins.messagetrends.server.util.DatabaseDialect;
import com.mirth.connect.plugins.messagetrends.server.util.DatabaseDialect.DatabaseType;
import com.mirth.connect.plugins.messagetrends.server.util.SqlSessionManagerProvider;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageTrendsProperties;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ExtensionController;

public class MessageTrendsController {
	private final Logger logger = LogManager.getLogger(this.getClass());
	private final MessageTrendsService messageTrendsService = MessageTrendsService.getInstance();

	private MessageTrendsScheduler messageTrendsScheduler;
	private MessageTrendsConfig messageTrendsConfig;

	private final ScheduledExecutorService restoreExec = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "MessageTrends Restore Reconcile");
		t.setDaemon(true);
		return t;
	});

	private ScheduledFuture<?> pendingRestore;

	private static MessageTrendsController instance = null;

	public static MessageTrendsController getInstance() {
		synchronized (MessageTrendsController.class) {
			if (instance == null) {
				instance = new MessageTrendsController();
			}
			return instance;
		}
	}

	public void init(Properties properties) throws MessageTrendsException {
		try {
			SqlSessionManager sqlSessionManager = getSqlSessionManager();

			// Initialize database if needed
			new MessageTrendsDatabaseMigrator(sqlSessionManager).initializeDatabase();

			// Create DAO instances
			DatabaseType dbType = DatabaseDialect.determineDatabaseType(sqlSessionManager);
			MessageStatisticsTimeseriesDao messageStatisticsDao = new MyBatisMessageStatisticsTimeseriesDao(sqlSessionManager, dbType);

			// Initialize service
			messageTrendsService.init(messageStatisticsDao);

			// Initialize config
			MessageTrendsProperties messageTrendsProperties = MessageTrendsProperties.fromProperties(properties);
			messageTrendsConfig = MessageTrendsConfig.defaultConfig().withEnabled(messageTrendsProperties.isEnabled());

			// Initialize Scheduler
			if (messageTrendsConfig.isEnabled()) {
				messageTrendsScheduler = MessageTrendsSchedulerFactory.build(messageTrendsService, ConfigurationController.getInstance().getServerId(), messageTrendsConfig);
			}

			logger.info("Message Trends initialized. enabled={}", messageTrendsProperties.isEnabled());
		} catch (Exception e) {
			throw new MessageTrendsException(e);
		}
	}

	public void update(Properties properties) throws MessageTrendsException {
		// Reconcile job pending -> cancel
		if (pendingRestore != null) {
			pendingRestore.cancel(false);
			pendingRestore = null;
		}

		try {
			MessageTrendsProperties messageTrendsProperties = MessageTrendsProperties.fromProperties(properties);
			boolean newEnabled = messageTrendsProperties.isEnabled();
			boolean oldEnabled = messageTrendsConfig.isEnabled();

			if (newEnabled == oldEnabled) {
				return;
			}

			messageTrendsConfig = MessageTrendsConfig.defaultConfig().withEnabled(newEnabled);

			if (messageTrendsScheduler != null) {
				messageTrendsScheduler.stop();
				messageTrendsScheduler = null;
			}

			if (newEnabled) {
				messageTrendsScheduler = MessageTrendsSchedulerFactory.buildAndStart(messageTrendsService, ConfigurationController.getInstance().getServerId(), messageTrendsConfig);
			}
		} catch (Exception e) {
			throw new MessageTrendsException(e);
		}
	}

	public void start() throws MessageTrendsException {
		try {
			if (messageTrendsScheduler != null) {
				messageTrendsScheduler.start();
			}
		} catch (Exception e) {
			throw new MessageTrendsException(e);
		}
	}

	public void stop() throws MessageTrendsException {
		try {
			if (messageTrendsScheduler != null) {
				messageTrendsScheduler.stop();
				messageTrendsScheduler = null;
			}
		} catch (Exception e) {
			throw new MessageTrendsException(e);
		} finally {
			// Cancel any pending restore
			if (pendingRestore != null) {
				pendingRestore.cancel(false);
				pendingRestore = null;
			}
		}
	}

	public void onRestoreTriggered() {
		// Cancel any previous pending task
		if (pendingRestore != null && !pendingRestore.isDone()) {
			pendingRestore.cancel(false);
			pendingRestore = null;
		}

		pendingRestore = restoreExec.schedule(() -> {
			try {
				// Read persisted properties from MC core after DB write
				Properties persisted = ExtensionController.getInstance().getPluginProperties("Message Trends Management System"); // plugin point name

				if (persisted != null) {
					update(persisted);
				}
			} catch (Exception e) {
				logger.error("Failed to reconcile after restore", e);
			}
		}, 5, TimeUnit.SECONDS);

		logger.debug("Scheduled reconcile after restore (5s delay).");
	}

	/**
	 * Get SqlSessionManager from Mirth's context
	 */
	private SqlSessionManager getSqlSessionManager() {
		return SqlSessionManagerProvider.get();
	}
}
