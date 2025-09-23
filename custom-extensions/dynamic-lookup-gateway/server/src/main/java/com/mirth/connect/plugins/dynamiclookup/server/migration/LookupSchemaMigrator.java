package com.mirth.connect.plugins.dynamiclookup.server.migration;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import org.apache.commons.dbutils.DbUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.dynamiclookup.server.util.DatabaseDialect;
import com.mirth.connect.plugins.dynamiclookup.server.util.DatabaseDialect.DatabaseType;

public class LookupSchemaMigrator {
	private final Logger logger = LogManager.getLogger(this.getClass());

	private final SqlSessionManager sqlSessionManager;

	public LookupSchemaMigrator(SqlSessionManager sqlSessionManager) {
		this.sqlSessionManager = sqlSessionManager;
	}

	public void migrate() {
		if (needUpdateV101()) {
			String path = "/sql/sqlserver/fixes/V101_convert_varchar_to_nvarchar.sql";
			String sqlScript = SqlScriptRunner.loadScript(path);

			SqlScriptRunner.runWithGo(sqlSessionManager, sqlScript);
		}
	}

	private boolean needUpdateV101() {
		try {
			DatabaseType dbType = DatabaseDialect.determineDatabaseType(sqlSessionManager);
			if (dbType != DatabaseType.SQLSERVER) {
				return false;
			}

			SqlSession session = sqlSessionManager.openSession();
			ResultSet rs = null;
			try {
				DatabaseMetaData metaData = session.getConnection().getMetaData();

				rs = metaData.getColumns(null, null, "LOOKUP_GROUP", "NAME");
				if (!rs.next()) {
					DbUtils.closeQuietly(rs);
					rs = metaData.getColumns(null, null, "lookup_group", "name");
					if (!rs.next()) {
						return false;
					}
				}

				String typeName = rs.getString("TYPE_NAME");
				return typeName == null || !"NVARCHAR".equalsIgnoreCase(typeName);
			} catch (Exception e) {
				logger.warn("needUpdateV101() check failed: {}", e.getMessage());
				return false;
			} finally {
				DbUtils.closeQuietly(rs);
				try {
					session.close();
				} catch (Exception ignore) {
				}
			}

		} catch (Exception e) {
			// TODO: handle exception
		}

		return false;
	}
}
