package com.mirth.connect.plugins.messagetrends.server.maintenance;

import static org.junit.Assert.*;

import org.junit.Test;

public class MessageTrendsConfigTest {

    @Test
    public void defaultConfig_notNull() {
        assertNotNull(MessageTrendsConfig.defaultConfig());
    }

    @Test
    public void defaultConfig_enabled_isFalse() {
        assertFalse(MessageTrendsConfig.defaultConfig().isEnabled());
    }

    @Test
    public void defaultConfig_flushEnabled_isTrue() {
        assertTrue(MessageTrendsConfig.defaultConfig().isFlushEnabled());
    }

    @Test
    public void defaultConfig_rollupEnabled_isTrue() {
        assertTrue(MessageTrendsConfig.defaultConfig().isRollupEnabled());
    }

    @Test
    public void defaultConfig_rollupFixedRateSeconds_is120() {
        assertEquals(120, MessageTrendsConfig.defaultConfig().getRollupFixedRateSeconds());
    }

    @Test
    public void defaultConfig_purgeEnabled_isTrue() {
        assertTrue(MessageTrendsConfig.defaultConfig().isPurgeEnabled());
    }

    @Test
    public void defaultConfig_purgeFixedRateSeconds_is3600() {
        assertEquals(3600, MessageTrendsConfig.defaultConfig().getPurgeFixedRateSeconds());
    }

    @Test
    public void defaultConfig_purgeThrottleMs_is1000() {
        assertEquals(1000L, MessageTrendsConfig.defaultConfig().getPurgeThrottleMs());
    }

    @Test
    public void defaultConfig_clock_notNull() {
        assertNotNull(MessageTrendsConfig.defaultConfig().getClock());
    }

    @Test
    public void defaultConfig_retentionByBucket_has5Entries() {
        assertEquals(5, MessageTrendsConfig.defaultConfig().getRetentionByBucket().size());
    }

    @Test
    public void withEnabled_true_isEnabled() {
        assertTrue(MessageTrendsConfig.defaultConfig().withEnabled(true).isEnabled());
    }

    @Test
    public void withEnabled_false_doesNotMutateOriginal() {
        MessageTrendsConfig original = MessageTrendsConfig.defaultConfig().withEnabled(true);
        MessageTrendsConfig copy = original.withEnabled(false);
        // original must remain true; copy must be false
        assertTrue(original.isEnabled());
        assertFalse(copy.isEnabled());
    }
}
