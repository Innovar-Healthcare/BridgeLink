/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 migration coverage — Migrate3_3_0 ConfigurationMigrator methods (no DB).
 */

package com.mirth.connect.server.migration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.Test;

/**
 * Tests the ConfigurationMigrator methods on Migrate3_3_0.
 * The migrate() method uses executeScript() + JDBC — NOT tested here.
 * Only getConfigurationPropertiesToAdd(), getConfigurationPropertiesToRemove(),
 * and updateConfiguration() are covered (no DB connection required).
 */
public class Migrate3_3_0Test {

    // ------------------------------------------------------------------
    // getConfigurationPropertiesToAdd: adds server.startupdeploy
    // ------------------------------------------------------------------

    @Test
    public void testGetConfigurationPropertiesToAdd_containsStartupDeploy() {
        Migrate3_3_0 migrator = new Migrate3_3_0();
        Map<String, Object> props = migrator.getConfigurationPropertiesToAdd();
        assertNotNull(props);
        assertTrue(props.containsKey("server.startupdeploy"));
    }

    @Test
    public void testGetConfigurationPropertiesToAdd_startupDeployIsTrue() {
        Migrate3_3_0 migrator = new Migrate3_3_0();
        Map<String, Object> props = migrator.getConfigurationPropertiesToAdd();
        // Value is a MutablePair<Object, String>; left/key is Boolean true
        Object value = props.get("server.startupdeploy");
        assertNotNull(value);
        // MutablePair.toString() includes the key
        assertTrue(value.toString().contains("true"));
    }

    // ------------------------------------------------------------------
    // getConfigurationPropertiesToRemove: returns null
    // ------------------------------------------------------------------

    @Test
    public void testGetConfigurationPropertiesToRemove_returnsNull() {
        Migrate3_3_0 migrator = new Migrate3_3_0();
        assertNull(migrator.getConfigurationPropertiesToRemove());
    }

    // ------------------------------------------------------------------
    // updateConfiguration: no-op — does not modify configuration
    // ------------------------------------------------------------------

    @Test
    public void testUpdateConfiguration_noOp_doesNotModifyConfig() throws Exception {
        Migrate3_3_0 migrator = new Migrate3_3_0();
        PropertiesConfiguration config = new PropertiesConfiguration();
        config.setProperty("some.key", "some-value");

        migrator.updateConfiguration(config);

        // updateConfiguration is a no-op in Migrate3_3_0
        assertTrue(config.containsKey("some.key"));
        assertTrue("some-value".equals(config.getString("some.key")));
    }
}
