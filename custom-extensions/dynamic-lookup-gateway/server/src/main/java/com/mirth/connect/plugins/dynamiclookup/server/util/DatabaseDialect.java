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
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;

import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo.DatabaseType;

/**
 * Utility for determining the underlying database type.
 */
public final class DatabaseDialect {

    private DatabaseDialect() {
        // Utility class
    }

    public static DatabaseInfo determineDatabase(SqlSessionManager sqlSessionManager) throws SQLException {
        SqlSession session = sqlSessionManager.openSession();

        try {
            Connection conn = session.getConnection();
            DatabaseMetaData meta = conn.getMetaData();

            String productName = meta.getDatabaseProductName();
            String versionString = meta.getDatabaseProductVersion();
            int major = meta.getDatabaseMajorVersion();
            int minor = meta.getDatabaseMinorVersion();

            String name = (productName == null ? "" : productName).toLowerCase();

            DatabaseType type;

            if (name.contains("postgresql")) {
                type = DatabaseType.POSTGRESQL;
            } else if (name.contains("mysql") || name.contains("mariadb")) {
                type = DatabaseType.MYSQL;
            } else if (name.contains("microsoft") || name.contains("sql server")) {
                type = DatabaseType.SQLSERVER;
            } else if (name.contains("oracle")) {
                type = DatabaseType.ORACLE;
            } else if (name.contains("derby")) {
                type = DatabaseType.DERBY;
            } else {
                type = DatabaseType.DERBY;
            }

            return new DatabaseInfo(type, major, minor, productName, versionString);

        } finally {
            try {
                session.close();
            } catch (Exception ignore) {
            }
        }
    }

}
