/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.server.migration;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.messagetrends.server.util.DatabaseDialect;
import com.mirth.connect.plugins.messagetrends.server.util.DatabaseDialect.DatabaseType;
import com.mirth.connect.server.util.DatabaseUtil;

public final class MessageTrendsDatabaseMigrator {
	private final Logger logger = LogManager.getLogger(this.getClass());

	private final SqlSessionManager sqlSessionManager;

	public MessageTrendsDatabaseMigrator(SqlSessionManager sqlSessionManager) {
		this.sqlSessionManager = sqlSessionManager;
	}

	public void initializeDatabase() throws Exception {
		logger.info("Initializing database schema...");

		SqlSession checkSession = sqlSessionManager.openSession();
		boolean tablesExist = false;
		try {
			tablesExist = DatabaseUtil.tableExists(checkSession.getConnection(), "message_statistics_timeseries");
		} finally {
			checkSession.close();
		}

		// Check if tables already exist
		if (!tablesExist) {
			// Fresh install - create tables with latest schema
			DatabaseType dbType = DatabaseDialect.determineDatabaseType(sqlSessionManager);
			String sqlScript = SqlScriptRunner.loadScript(getCreateSchemaScriptPath(dbType));
			SqlScriptRunner.runWithSemicolon(sqlSessionManager, sqlScript);
			logger.info("Database schema initialized successfully");
		}
	}

	/**
	 * Get migration script based on database type
	 */
	private String getCreateSchemaScriptPath(DatabaseType dbType) {
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

		return scriptPath;
	}
}
