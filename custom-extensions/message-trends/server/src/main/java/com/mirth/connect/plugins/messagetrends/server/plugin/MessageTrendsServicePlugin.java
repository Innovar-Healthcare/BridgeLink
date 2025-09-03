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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.api.util.OperationUtil;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.ServicePlugin;
import com.mirth.connect.plugins.messagetrends.server.dao.MessageStatisticsTimeseriesDao;
import com.mirth.connect.plugins.messagetrends.server.dao.impl.MyBatisMessageStatisticsTimeseriesDao;
import com.mirth.connect.plugins.messagetrends.server.maintenance.MessageTrendsConfig;
import com.mirth.connect.plugins.messagetrends.server.maintenance.MessageTrendsScheduler;
import com.mirth.connect.plugins.messagetrends.server.maintenance.MessageTrendsSchedulerFactory;
import com.mirth.connect.plugins.messagetrends.server.service.MessageTrendsService;
import com.mirth.connect.plugins.messagetrends.server.util.DatabaseDialect;
import com.mirth.connect.plugins.messagetrends.server.util.DatabaseDialect.DatabaseType;
import com.mirth.connect.plugins.messagetrends.server.util.SqlSessionManagerProvider;
import com.mirth.connect.plugins.messagetrends.shared.interfaces.MessageTrendsServletInterface;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.util.DatabaseUtil;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-08-23 10:25 AM
 */

public class MessageTrendsServicePlugin implements ServicePlugin {
	private final Logger logger = LogManager.getLogger(this.getClass());
	private final MessageTrendsService messageTrendsService = MessageTrendsService.getInstance();
	private MessageTrendsScheduler messageTrendsScheduler;
	private MessageTrendsConfig messageTrendsConfig;

	@Override
	public void init(Properties properties) {
		logger.info("Initializing Message Trends Management System plugin...");
		try {
			// Initialize database if needed
			DatabaseType dbType = initializeDatabase();

			// Create DAO instances
			SqlSessionManager sqlSessionManager = getSqlSessionManager();
			MessageStatisticsTimeseriesDao messageStatisticsDao = new MyBatisMessageStatisticsTimeseriesDao(sqlSessionManager, dbType);

			messageTrendsService.init(messageStatisticsDao);

			boolean enabled = Boolean.parseBoolean(properties.getProperty("messagetrends.enabled", "false"));
			messageTrendsConfig = MessageTrendsConfig.defaultConfig().withEnabled(enabled);

			logger.info(properties);
			logger.info("Message Trends Management System plugin initialized successfully");
		} catch (Exception e) {
			logger.error("Error initializing Message Trends Management System plugin", e);
			throw new RuntimeException("Failed to initialize plugin: " + e.getMessage(), e);
		}
	}

	@Override
	public void update(Properties properties) {
		boolean enabled = Boolean.parseBoolean(properties.getProperty("messagetrends.enabled", "false"));
		messageTrendsConfig = MessageTrendsConfig.defaultConfig().withEnabled(enabled);
		String serverId = ConfigurationController.getInstance().getServerId();

		if (enabled && messageTrendsScheduler == null) {
			messageTrendsScheduler = MessageTrendsSchedulerFactory.buildAndStart(messageTrendsService, serverId, messageTrendsConfig);
		} else if (!enabled && messageTrendsScheduler != null) {
			messageTrendsScheduler.stop();
			messageTrendsScheduler = null;
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
	public void start() {
		if (messageTrendsConfig == null) {
			messageTrendsConfig = MessageTrendsConfig.defaultConfig();
		}

		if (messageTrendsConfig.isEnabled()) {
			String serverId = ConfigurationController.getInstance().getServerId();
			this.messageTrendsScheduler = MessageTrendsSchedulerFactory.build(messageTrendsService, serverId, messageTrendsConfig);
			this.messageTrendsScheduler.start();
			logger.info("MessageTrendsScheduler started (enabled=true).");
		} else {
			logger.info("MessageTrendsScheduler is disabled by configuration.");
		}
	}

	@Override
	public void stop() {
		// Stop scheduler and cleanup
		if (messageTrendsScheduler != null) {
			try {
				messageTrendsScheduler.stop();
			} catch (Throwable t) {
				logger.warn("MessageTrendsScheduler stop failed (non-fatal)", t);
			} finally {
				messageTrendsScheduler = null;
			}
		}
	}

	/**
	 * Initialize database schema if needed
	 */
	private DatabaseType initializeDatabase() throws Exception {
		logger.info("Initializing database schema...");
		SqlSessionManager sqlSessionManager = getSqlSessionManager();
		DatabaseType dbType = DatabaseDialect.determineDatabaseType(sqlSessionManager);
		SqlSession session = sqlSessionManager.openSession();
		try {
			// Check if tables already exist
			if (!DatabaseUtil.tableExists(session.getConnection(), "message_statistics_timeseries")) {
				// Create tables based on database type
				String migrationScript = getMigrationScript(dbType);
				executeSqlScript(session, migrationScript);
				logger.info("Database schema initialized successfully");
			} else {
				logger.info("Database schema already exists, skipping initialization");
			}
		} finally {
			session.close();
		}

		return dbType;
	}

	/**
	 * Get migration script based on database type
	 */
	private String getMigrationScript(DatabaseType dbType) {
		// Load appropriate script based on database type
		String scriptPath;
		switch (dbType) {
		case POSTGRESQL:
			scriptPath = "/sql/postgres/create-message-trends-tables.sql";
			break;
		case MYSQL:
			scriptPath = "/sql/mysql/create-message-trends-tables.sql";
			break;
		case SQLSERVER:
			scriptPath = "/sql/sqlserver/create-message-trends-tables.sql";
			break;
		case ORACLE:
			scriptPath = "/sql/oracle/create-message-trends-tables.sql";
			break;
		case DERBY:
		default:
			scriptPath = "/sql/derby/create-message-trends-tables.sql";
			break;
		}
		try (InputStream is = getClass().getResourceAsStream(scriptPath)) {
			if (is == null) {
				throw new RuntimeException("Failed to load migration script: script not found");
			}

			return IOUtils.toString(is, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Failed to load migration script: " + scriptPath, e);
		}
	}

	/**
	 * Execute SQL script
	 */
	private void executeSqlScript(SqlSession session, String script) {
		String[] statements = script.split(";");

		try {
			Statement statement = session.getConnection().createStatement();

			for (String statementString : statements) {
				statementString = statementString.trim();
				if (!statementString.isEmpty()) {
					statement.execute(statementString);
				}
			}
			session.commit();
		} catch (Exception e) {
			session.rollback();
			throw new RuntimeException("Failed to execute SQL script: " + e.getMessage(), e);
		}
	}

	/**
	 * Get SqlSessionManager from Mirth's context
	 */
	private SqlSessionManager getSqlSessionManager() {
		return SqlSessionManagerProvider.get();
	}
}
