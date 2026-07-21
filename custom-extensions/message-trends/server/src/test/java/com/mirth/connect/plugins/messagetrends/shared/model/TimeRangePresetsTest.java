package com.mirth.connect.plugins.messagetrends.shared.model;

import static org.junit.Assert.*;

import java.time.Duration;
import java.util.Map;

import org.junit.Test;

public class TimeRangePresetsTest {

    @Test
    public void presets_nonNullSize15() {
        assertNotNull(TimeRangePresets.PRESETS);
        assertEquals(15, TimeRangePresets.PRESETS.size());
    }

    @Test
    public void presetToDuration_last1h_equalsOneHour() {
        assertEquals(Duration.ofHours(1), TimeRangePresets.PRESET_TO_DURATION.get("last_1h"));
    }

    @Test
    public void presetToDuration_last1095d_equals1095Days() {
        assertEquals(Duration.ofDays(1095), TimeRangePresets.PRESET_TO_DURATION.get("last_1095d"));
    }

    @Test
    public void presetToLabel_last1h_equalsLastOneHour() {
        assertEquals("Last 1 Hour", TimeRangePresets.PRESET_TO_LABEL.get("last_1h"));
    }

    @Test
    public void toDuration_last7d_equals7Days() {
        assertEquals(Duration.ofDays(7), TimeRangePresets.toDuration("last_7d"));
    }

    @Test
    public void toDuration_unknown_returnsNull() {
        assertNull(TimeRangePresets.toDuration("unknown"));
    }

    @Test
    public void getDefaultRetention_size5_contains1440() {
        Map<Integer, Duration> retention = TimeRangePresets.getDefaultRetention();
        assertNotNull(retention);
        assertEquals(5, retention.size());
        assertEquals(Duration.ofDays(1095), retention.get(1440));
    }

    @Test
    public void presetToLabel_size15() {
        assertEquals(15, TimeRangePresets.PRESET_TO_LABEL.size());
    }
}
