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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;

import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;

public class SqlScriptRunner {
	public static String loadScript(String scriptPath) {
		if (scriptPath == null || scriptPath.isEmpty()) {
			throw new IllegalArgumentException("scriptPath is null/empty");
		}

		try (InputStream is = SqlScriptRunner.class.getResourceAsStream(scriptPath)) {
			if (is == null) {
				throw new RuntimeException("Failed to load migration script: script not found");
			}

			return IOUtils.toString(is, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Failed to load migration script: " + scriptPath + " (not found)");
		}
	}

	public static void runWithSemicolon(SqlSessionManager sqlSessionManager, String script) {
		SqlSession session = sqlSessionManager.openSession();
		boolean commitSuccess = false;
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
			commitSuccess = true;
		} catch (Exception e) {
			throw new RuntimeException("Failed to execute SQL script: " + e.getMessage(), e);
		} finally {
			if (!commitSuccess) {
				try {
					session.rollback();
				} catch (Exception ignored) {
				}
			}
			session.close();
		}
	}
}
