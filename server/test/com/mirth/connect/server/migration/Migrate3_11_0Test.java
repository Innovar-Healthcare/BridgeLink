package com.mirth.connect.server.migration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class Migrate3_11_0Test {

    @Test
    public void testGetConfigurationPropertiesToAdd() {
        Migrate3_11_0 migrator = new Migrate3_11_0();

        Map<String, Object> props = migrator.getConfigurationPropertiesToAdd();
        assertNotNull(props);
        assertTrue(props.containsKey("database.connection.maxretry"));
        assertTrue(props.containsKey("database.connection.retrywaitinmilliseconds"));
    }

    @Test
    public void testGetConfigurationPropertiesToRemoveIsNull() {
        Migrate3_11_0 migrator = new Migrate3_11_0();

        // Source returns null — verify this does not throw
        String[] propsToRemove = migrator.getConfigurationPropertiesToRemove();
        // null is acceptable per source
        assertTrue(propsToRemove == null);
    }
}
