package com.mirth.connect.plugins.dynamiclookup.server.config;

import com.mirth.connect.client.core.PropertiesConfigurationUtil;
import com.mirth.connect.server.tools.ClassPathResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

import org.apache.commons.configuration2.PropertiesConfiguration;

public class DatabaseSettingsLoader {
    private static final Logger logger = LogManager.getLogger(DatabaseSettingsLoader.class);
    private static final String CONFIG_PATH = "dynamic-lookup.properties";

    public static DatabaseSettings load() {
        DatabaseSettings settings = new DatabaseSettings();

        try {
            PropertiesConfiguration config = PropertiesConfigurationUtil.create(new File(ClassPathResource.getResourceURI(CONFIG_PATH)));

            settings.setUseExternalDb(config.getBoolean("useExternalDb", false));
            settings.setDatabase(config.getString("database", ""));
            settings.setUrl(config.getString("database.url", ""));
            settings.setUsername(config.getString("database.username", ""));
            settings.setPassword(config.getString("database.password", ""));
            settings.setDriver(config.getString("database.driver", ""));
            settings.setMaxConnections(config.getInt("database.max-connections", 20));
            settings.setMaxRetry(config.getInt("database.connection.maxretry", 2));

            logger.info("Loaded database settings from {}", CONFIG_PATH);
        } catch (Exception e) {
            logger.warn("Failed to load database settings from {}", CONFIG_PATH, e);

            settings.setUseExternalDb(false);
        }

        return settings;
    }
}

