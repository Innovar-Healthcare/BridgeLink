/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.config;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

public class DatabaseSettings {
    private static Map<String, String> DEFAULT_DRIVER_MAP = new HashMap<>();

    static {
        DEFAULT_DRIVER_MAP.put("derby", "org.apache.derby.jdbc.EmbeddedDriver");
        DEFAULT_DRIVER_MAP.put("mysql", "com.mysql.cj.jdbc.Driver");
        DEFAULT_DRIVER_MAP.put("oracle", "oracle.jdbc.OracleDriver");
        DEFAULT_DRIVER_MAP.put("postgres", "org.postgresql.Driver");
        DEFAULT_DRIVER_MAP.put("sqlserver", "net.sourceforge.jtds.jdbc.Driver");
    }

    private boolean useExternalDb;
    private String database;
    private String url;
    private String username;
    private String password;
    private String driver;
    private int maxConnections;
    private int maxRetry;

    // Getters and setters

    public boolean isUseExternalDb() {
        return useExternalDb;
    }

    public void setUseExternalDb(boolean useExternalDb) {
        this.useExternalDb = useExternalDb;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    private String getMappedDatabaseDriver() {
        if (StringUtils.isBlank(driver)) {
            return MapUtils.getString(DEFAULT_DRIVER_MAP, getDatabase());
        } else {
            return driver;
        }
    }

    public Properties getProperties() {
        Properties properties = new Properties();

        if (getMappedDatabaseDriver() != null) {
            properties.setProperty("driver", getMappedDatabaseDriver());
        }
        if (url != null) {
            properties.setProperty("url", url);
        }
        if (username != null) {
            properties.setProperty("username", username);
        }
        if (password != null) {
            properties.setProperty("password", password);
        }

        properties.setProperty("database.max-connections", String.valueOf(maxConnections));
        properties.setProperty("database.connection.maxretry", String.valueOf(maxRetry));

        return properties;
    }

    @Override
    public String toString() {
        return "DatabaseSettings{" +
                "useExternalDb=" + useExternalDb +
                ", database='" + database + '\'' +
                ", url='" + url + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", driver='" + driver + '\'' +
                ", maxConnections=" + maxConnections +
                ", maxRetry=" + maxRetry +
                '}';
    }
}

