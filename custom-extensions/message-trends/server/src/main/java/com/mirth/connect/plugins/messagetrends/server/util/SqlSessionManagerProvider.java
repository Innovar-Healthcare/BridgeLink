package com.mirth.connect.plugins.messagetrends.server.util;

import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.server.util.SqlConfig;

public class SqlSessionManagerProvider {
	private static final Logger logger = LogManager.getLogger(SqlSessionManagerProvider.class);

	private static SqlSessionManager sessionManager;

	public static synchronized SqlSessionManager get() {
		if (sessionManager != null) {
			return sessionManager;
		}

		sessionManager = SqlConfig.getInstance().getSqlSessionManager();
		return sessionManager;
	}
}