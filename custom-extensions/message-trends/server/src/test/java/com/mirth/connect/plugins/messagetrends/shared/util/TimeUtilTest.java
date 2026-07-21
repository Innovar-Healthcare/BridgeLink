package com.mirth.connect.plugins.messagetrends.shared.util;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

public class TimeUtilTest {

    @Test
    public void toDateFromEpochSeconds_null_returnsNull() {
        assertNull(TimeUtil.toDateFromEpochSeconds(null, "field"));
    }

    @Test
    public void toDateFromEpochSeconds_valid_returnsDate() {
        Date d = TimeUtil.toDateFromEpochSeconds(1_700_000_000L, "ts");
        assertNotNull(d);
        assertEquals(1_700_000_000_000L, d.getTime());
    }

    @Test(expected = IllegalArgumentException.class)
    public void toDateFromEpochSeconds_millis_throws() {
        TimeUtil.toDateFromEpochSeconds(1_000_000_000_000L, "ts");
    }

    @Test
    public void toEpochSeconds_null_returnsNull() {
        assertNull(TimeUtil.toEpochSeconds(null));
    }

    @Test
    public void toEpochSeconds_date_returnsSeconds() {
        Date d = new Date(1_700_000_000_000L);
        Long result = TimeUtil.toEpochSeconds(d);
        assertNotNull(result);
        assertEquals(1_700_000_000L, result.longValue());
    }
}
