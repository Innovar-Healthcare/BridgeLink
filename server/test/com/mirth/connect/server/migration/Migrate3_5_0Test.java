package com.mirth.connect.server.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

public class Migrate3_5_0Test {

    // One of the 3DES cipher suites that should be filtered out
    private static final String TRIPLE_DES_SUITE = "SSL_RSA_WITH_3DES_EDE_CBC_SHA";

    // A safe AES cipher suite that should NOT be removed
    private static final String AES_SUITE = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384";

    @Test
    public void testGetConfigurationPropertiesToAdd() {
        Migrate3_5_0 migrator = new Migrate3_5_0();

        Map<String, Object> props = migrator.getConfigurationPropertiesToAdd();
        assertNotNull(props);
        assertTrue(props.containsKey("https.ephemeraldhkeysize"));
        assertTrue(props.containsKey("server.api.accesscontrolalloworigin"));
        assertTrue(props.containsKey("server.api.accesscontrolallowmethods"));
    }

    @Test
    public void testGetConfigurationPropertiesToRemoveIsNull() {
        Migrate3_5_0 migrator = new Migrate3_5_0();
        assertNull(migrator.getConfigurationPropertiesToRemove());
    }

    /**
     * When https.ciphersuites is not set, updateConfiguration must be a no-op.
     */
    @Test
    public void testUpdateConfigurationCipherSuiteNotSet() throws Exception {
        Migrate3_5_0 migrator = new Migrate3_5_0();
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        migrator.updateConfiguration(configuration);

        assertFalse(configuration.containsKey("https.ciphersuites"));
        assertFalse(configuration.containsKey("https.ciphersuites.old"));
    }

    /**
     * When https.ciphersuites is empty, updateConfiguration must be a no-op.
     */
    @Test
    public void testUpdateConfigurationCipherSuiteEmpty() throws Exception {
        Migrate3_5_0 migrator = new Migrate3_5_0();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setProperty("https.ciphersuites", "");

        migrator.updateConfiguration(configuration);

        // Empty array — ArrayUtils.isNotEmpty returns false, no change
        assertEquals("", StringUtils.join(configuration.getStringArray("https.ciphersuites"), ','));
        assertFalse(configuration.containsKey("https.ciphersuites.old"));
    }

    /**
     * When https.ciphersuites contains a 3DES suite alongside an AES suite,
     * the 3DES suite must be removed and the AES suite retained.
     */
    @Test
    public void testUpdateConfigurationTripleDesSuiteRemoved() throws Exception {
        Migrate3_5_0 migrator = new Migrate3_5_0();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String customValue = AES_SUITE + "," + TRIPLE_DES_SUITE;
        configuration.setProperty("https.ciphersuites", customValue);

        migrator.updateConfiguration(configuration);

        String newValue = StringUtils.join(configuration.getStringArray("https.ciphersuites"), ',');
        assertTrue("AES suite should remain", newValue.contains(AES_SUITE));
        assertFalse("3DES suite should be removed", newValue.contains(TRIPLE_DES_SUITE));
    }

    /**
     * When only 3DES suites that were removed are custom (non-default), the old value
     * must be preserved in https.ciphersuites.old.
     */
    @Test
    public void testUpdateConfigurationOldValuePreservedForCustomCiphers() throws Exception {
        Migrate3_5_0 migrator = new Migrate3_5_0();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        // A custom value that differs from both old and new defaults and includes a 3DES suite
        String customValue = AES_SUITE + "," + TRIPLE_DES_SUITE;
        configuration.setProperty("https.ciphersuites", customValue);

        migrator.updateConfiguration(configuration);

        // The old value should be saved since it differs from the new value and was non-default
        assertTrue("Old value should be stored", configuration.containsKey("https.ciphersuites.old"));
        String oldValue = StringUtils.join(configuration.getStringArray("https.ciphersuites.old"), ',');
        assertEquals(customValue, oldValue);
    }

    /**
     * When https.ciphersuites contains no 3DES suites, the value must not change.
     */
    @Test
    public void testUpdateConfigurationNoTripleDesNoChange() throws Exception {
        Migrate3_5_0 migrator = new Migrate3_5_0();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String onlyAes = AES_SUITE + ",TLS_RSA_WITH_AES_256_GCM_SHA384";
        configuration.setProperty("https.ciphersuites", onlyAes);

        migrator.updateConfiguration(configuration);

        String resultValue = StringUtils.join(configuration.getStringArray("https.ciphersuites"), ',');
        assertEquals("Value should be unchanged when no 3DES suites present", onlyAes, resultValue);
        assertFalse("No old value entry when nothing changed", configuration.containsKey("https.ciphersuites.old"));
    }
}
