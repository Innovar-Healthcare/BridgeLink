package com.mirth.connect.server.migration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.Test;

public class Migrate2_2_0Test {

    @Test
    public void testGetConfigurationPropertiesToAdd() {
        Migrate2_2_0 migrator = new Migrate2_2_0();

        Map<String, Object> props = migrator.getConfigurationPropertiesToAdd();
        assertNotNull(props);
        assertTrue(props.containsKey("password.retrylimit"));
        assertTrue(props.containsKey("password.lockoutperiod"));
        assertTrue(props.containsKey("password.expiration"));
        assertTrue(props.containsKey("password.graceperiod"));
        assertTrue(props.containsKey("password.reuseperiod"));
        assertTrue(props.containsKey("password.reuselimit"));
    }

    @Test
    public void testGetConfigurationPropertiesToRemove() {
        Migrate2_2_0 migrator = new Migrate2_2_0();

        String[] propsToRemove = migrator.getConfigurationPropertiesToRemove();
        assertNotNull(propsToRemove);
        assertTrue(Arrays.asList(propsToRemove).contains("keystore.storetype"));
        assertTrue(Arrays.asList(propsToRemove).contains("keystore.algorithm"));
    }

    @Test
    public void testUpdateConfigurationIsNoOp() throws Exception {
        Migrate2_2_0 migrator = new Migrate2_2_0();
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        // updateConfiguration is a no-op — must not throw
        migrator.updateConfiguration(configuration);
    }
}
