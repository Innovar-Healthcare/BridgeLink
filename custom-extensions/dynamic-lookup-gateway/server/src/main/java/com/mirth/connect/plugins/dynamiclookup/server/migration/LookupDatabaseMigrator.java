package com.mirth.connect.plugins.dynamiclookup.server.migration;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.dynamiclookup.server.util.DatabaseDialect;
import com.mirth.connect.plugins.dynamiclookup.server.util.DatabaseDialect.DatabaseType;
import com.mirth.connect.server.util.DatabaseUtil;

public final class LookupDatabaseMigrator {
	private final Logger logger = LogManager.getLogger(this.getClass());

	private final SqlSessionManager sqlSessionManager;

	public LookupDatabaseMigrator(SqlSessionManager sqlSessionManager) {
		this.sqlSessionManager = sqlSessionManager;
	}

	public void initializeDatabase() throws Exception {
		logger.info("Initializing database schema...");

		SqlSession checkSession = sqlSessionManager.openSession();
		boolean tablesExist = false;
		try {
			tablesExist = DatabaseUtil.tableExists(checkSession.getConnection(), "LOOKUP_GROUP");
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
			return;
		}

		// Use the migration manager to handle both fresh installs and migrations
		logger.info("Tables exist - Checking for schema migrations...");
		(new LookupSchemaMigrator(sqlSessionManager)).migrate();
		logger.info("Tables exist - Database schema migration check completed");
	}

	/**
	 * Get migration script based on database type
	 */
	private String getCreateSchemaScriptPath(DatabaseType dbType) {
		// Load appropriate script based on database type
		String scriptPath;
		switch (dbType) {
		case POSTGRESQL:
			scriptPath = "/sql/postgres/create_lookup_tables.sql";
			break;
		case MYSQL:
			scriptPath = "/sql/mysql/create_lookup_tables.sql";
			break;
		case SQLSERVER:
			scriptPath = "/sql/sqlserver/create_lookup_tables.sql";
			break;
		case ORACLE:
			scriptPath = "/sql/oracle/create_lookup_tables.sql";
			break;
		case DERBY:
		default:
			scriptPath = "/sql/derby/create_lookup_tables.sql";
			break;
		}

		return scriptPath;
	}
}
