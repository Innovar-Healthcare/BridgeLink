package com.mirth.connect.server.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.Test;

import com.mirth.connect.client.core.Version;

public class Migrate4_4_0Test {

    /**
     * Starting version is before v4_4_0 and digest.algorithm is already set:
     * Expects digest.iterations=1000 and digest.usepbe=0 to be written.
     */
    @Test
    public void testUpdateConfigurationDigestAlgorithmPresent() throws Exception {
        Migrate4_4_0 migrator = new Migrate4_4_0();
        migrator.setStartingVersion(Version.v4_0_0);

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setProperty("digest.algorithm", "SHA256");

        migrator.updateConfiguration(configuration);

        assertEquals("1000", configuration.getString("digest.iterations"));
        assertEquals("0", configuration.getString("digest.usepbe"));
        assertNull(configuration.getString("digest.fallback.algorithm"));
    }

    /**
     * Starting version is before v4_4_0 and digest.algorithm is absent:
     * Expects digest.fallback.algorithm=SHA256, digest.fallback.iterations=1000, digest.fallback.usepbe=0.
     */
    @Test
    public void testUpdateConfigurationDigestAlgorithmAbsent() throws Exception {
        Migrate4_4_0 migrator = new Migrate4_4_0();
        migrator.setStartingVersion(Version.v4_0_0);

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        // No digest.algorithm set

        migrator.updateConfiguration(configuration);

        assertEquals("SHA256", configuration.getString("digest.fallback.algorithm"));
        assertEquals("1000", configuration.getString("digest.fallback.iterations"));
        assertEquals("0", configuration.getString("digest.fallback.usepbe"));
        assertNull(configuration.getString("digest.iterations"));
    }

    /**
     * Starting version equals v4_4_0 (version guard fires):
     * Expects no changes — digest.iterations and digest.fallback.algorithm remain null.
     */
    @Test
    public void testUpdateConfigurationVersionGuardSkips() throws Exception {
        Migrate4_4_0 migrator = new Migrate4_4_0();
        migrator.setStartingVersion(Version.v4_4_0);

        PropertiesConfiguration configuration = new PropertiesConfiguration();

        migrator.updateConfiguration(configuration);

        assertNull(configuration.getString("digest.iterations"));
        assertNull(configuration.getString("digest.fallback.algorithm"));
    }

    /**
     * Starting version is null (fresh install path — treated as pre-v4_4_0):
     * Expects fallback properties to be set.
     */
    @Test
    public void testUpdateConfigurationStartingVersionNullActsAsPreVersion() throws Exception {
        Migrate4_4_0 migrator = new Migrate4_4_0();
        // null starting version means getStartingVersion() == null, which evaluates as pre-v4_4_0
        migrator.setStartingVersion(null);

        PropertiesConfiguration configuration = new PropertiesConfiguration();

        migrator.updateConfiguration(configuration);

        // null startingVersion branch: condition is (null == null) || (null < v4_4_0)
        // Source: getStartingVersion() == null evaluates true, so branch fires
        assertEquals("SHA256", configuration.getString("digest.fallback.algorithm"));
    }

    @Test
    public void testGetConfigurationPropertiesToAddReturnsNull() {
        Migrate4_4_0 migrator = new Migrate4_4_0();
        assertNull(migrator.getConfigurationPropertiesToAdd());
    }

    @Test
    public void testGetConfigurationPropertiesToRemoveReturnsNull() {
        Migrate4_4_0 migrator = new Migrate4_4_0();
        assertNull(migrator.getConfigurationPropertiesToRemove());
    }
}
