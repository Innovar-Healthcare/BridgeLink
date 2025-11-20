package com.mirth.connect.plugins.dynamiclookup.server.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.dbutils.DbUtils;

public class LookupDbUtil {
    private LookupDbUtil() {
    }

    public static boolean tableExists(Connection connection, String tableName) {
        ResultSet rs = null;
        try {
            DatabaseMetaData metaData = connection.getMetaData();

            // UPPER
            rs = metaData.getTables(null, null, tableName.toUpperCase(), new String[] { "TABLE" });
            if (rs.next()) {
                return true;
            }

            DbUtils.closeQuietly(rs);

            // lower
            rs = metaData.getTables(null, null, tableName.toLowerCase(), new String[] { "TABLE" });
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed checking table existence", e);
        } finally {
            DbUtils.closeQuietly(rs);
        }
    }

    public static boolean columnExists(Connection connection, String tableName, String columnName) {
        if (!tableExists(connection, tableName)) {
            return false;
        }

        ResultSet rs = null;
        try {
            DatabaseMetaData metaData = connection.getMetaData();

            // UPPER
            rs = metaData.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase());
            if (rs.next()) {
                return true;
            }

            DbUtils.closeQuietly(rs);

            // lower
            rs = metaData.getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase());
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed checking column existence", e);
        } finally {
            DbUtils.closeQuietly(rs);
        }
    }
}
