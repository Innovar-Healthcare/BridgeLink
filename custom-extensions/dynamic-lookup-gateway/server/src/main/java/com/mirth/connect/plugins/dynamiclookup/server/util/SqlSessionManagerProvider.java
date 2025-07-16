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

import com.mirth.connect.donkey.model.DatabaseConstants;
import com.mirth.connect.model.converters.DocumentSerializer;
import com.mirth.connect.plugins.dynamiclookup.server.config.DatabaseSettings;
import com.mirth.connect.plugins.dynamiclookup.server.config.DatabaseSettingsLoader;
import com.mirth.connect.server.util.SqlConfig;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.BufferedReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Properties;

public class SqlSessionManagerProvider {

    private static final Logger logger = LogManager.getLogger(SqlSessionManagerProvider.class);

    private static SqlSessionManager sessionManager;

    public static synchronized SqlSessionManager get() {
        if (sessionManager != null) {
            return sessionManager;
        }

        DatabaseSettings settings = DatabaseSettingsLoader.load();

        if (!settings.isUseExternalDb()) {
            sessionManager = SqlConfig.getInstance().getSqlSessionManager();
            return sessionManager;
        }

        try {
            // Reuse Mirth’s logic but build our own
            SqlSessionFactory factory = createFactory(settings.getDatabase(), settings);
            sessionManager = SqlSessionManager.newInstance(factory);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create external SqlSessionManager", e);
        }

        return sessionManager;
    }

    private static SqlSessionFactory createFactory(String database, DatabaseSettings settings) throws Exception {
        // Load and parse sqlmap-config.xml
        BufferedReader reader = new BufferedReader(Resources.getResourceAsReader("config/sqlmap-config.xml"));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        Document document = factory.newDocumentBuilder().parse(new InputSource(reader));
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        // Serialize updated XML document back to reader
        DocumentSerializer serializer = new DocumentSerializer();
        Reader finalReader = new StringReader(serializer.toXML(document));

        // Build properties from database settings
        Properties props = settings.getProperties();
        props.setProperty(DatabaseConstants.DATABASE, database);

        return new SqlSessionFactoryBuilder().build(finalReader, "external", props);
    }

}
