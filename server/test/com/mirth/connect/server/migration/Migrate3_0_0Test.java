package com.mirth.connect.server.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

public class Migrate3_0_0Test {

    private static int countOccurrences(String str, String target) {
        return StringUtils.countMatches(str, target);
    }

    @Test
    public void testGetConfigurationPropertiesToAdd() {
        Migrate3_0_0 migrator = new Migrate3_0_0();

        Map<String, Object> props = migrator.getConfigurationPropertiesToAdd();
        assertNotNull(props);
        assertTrue(props.containsKey("database.max-connections"));
        assertEquals(20, props.get("database.max-connections"));
    }

    @Test
    public void testGetConfigurationPropertiesToRemove() {
        Migrate3_0_0 migrator = new Migrate3_0_0();

        String[] propsToRemove = migrator.getConfigurationPropertiesToRemove();
        assertNotNull(propsToRemove);
        assertTrue(Arrays.asList(propsToRemove).contains("jmx.port"));
    }

    @Test
    public void testUpdateConfigurationDerbyAppendsUpgrade() throws Exception {
        Migrate3_0_0 migrator = new Migrate3_0_0();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setProperty("database", "derby");
        configuration.setProperty("database.url", "jdbc:derby:mirthdb");

        migrator.updateConfiguration(configuration);

        String url = (String) configuration.getProperty("database.url");
        assertTrue("Expected ;upgrade=true appended", url.contains(";upgrade=true"));
    }

    @Test
    public void testUpdateConfigurationDerbyNoDoubleAppend() throws Exception {
        Migrate3_0_0 migrator = new Migrate3_0_0();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setProperty("database", "derby");
        configuration.setProperty("database.url", "jdbc:derby:mirthdb;upgrade=true");

        migrator.updateConfiguration(configuration);

        String url = (String) configuration.getProperty("database.url");
        assertEquals("Expected exactly one ;upgrade= occurrence", 1, countOccurrences(url, ";upgrade="));
    }

    @Test
    public void testUpdateConfigurationMysqlLeavesUrlUnchanged() throws Exception {
        Migrate3_0_0 migrator = new Migrate3_0_0();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setProperty("database", "mysql");
        configuration.setProperty("database.url", "jdbc:mysql://localhost/mirthdb");

        migrator.updateConfiguration(configuration);

        String url = (String) configuration.getProperty("database.url");
        assertEquals("MySQL URL must not be modified", "jdbc:mysql://localhost/mirthdb", url);
    }
}
