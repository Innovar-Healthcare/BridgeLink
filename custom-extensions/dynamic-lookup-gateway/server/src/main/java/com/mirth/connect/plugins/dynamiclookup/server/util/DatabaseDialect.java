/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.util;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;

/**
 * Utility for determining the underlying database type.
 */
public final class DatabaseDialect {

	private DatabaseDialect() {
		// Utility class
	}

	/**
	 * Supported database types for SQL dialect branching.
	 */
	public enum DatabaseType {
		DERBY, POSTGRESQL, MYSQL, // Includes MySQL and MariaDB
		SQLSERVER, ORACLE
	}

	/**
	 * Determine the database type by inspecting the JDBC connection metadata.
	 * <p>
	 * Notes: - MySQL and MariaDB are treated as MYSQL. - If the product name cannot
	 * be matched, fallback is DERBY (safe for 2-step upsert).
	 *
	 * @param sqlSessionManager the MyBatis SqlSessionManager
	 * @return detected {@link DatabaseType}
	 * @throws SQLException if a JDBC error occurs while reading metadata
	 */
	public static DatabaseType determineDatabaseType(SqlSessionManager sqlSessionManager) throws SQLException {
		SqlSession session = sqlSessionManager.openSession();
		try {
			Connection conn = session.getConnection();
			String product = conn.getMetaData().getDatabaseProductName();
			String name = (product == null ? "" : product).toLowerCase();

			if (name.contains("postgresql")) {
				return DatabaseType.POSTGRESQL;
			} else if (name.contains("mysql") || name.contains("mariadb")) {
				return DatabaseType.MYSQL;
			} else if (name.contains("microsoft") || name.contains("sql server")) {
				return DatabaseType.SQLSERVER;
			} else if (name.contains("oracle")) {
				return DatabaseType.ORACLE;
			} else if (name.contains("derby")) {
				return DatabaseType.DERBY;
			} else {
				// Fallback to DERBY
				return DatabaseType.DERBY;
			}
		} finally {
			session.close();
		}
	}
}
