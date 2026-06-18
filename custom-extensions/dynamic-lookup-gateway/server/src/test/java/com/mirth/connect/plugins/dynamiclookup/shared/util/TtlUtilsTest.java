package com.mirth.connect.plugins.dynamiclookup.shared.util;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

public class TtlUtilsTest {

    @Test
    public void isWithinTtlSeconds_nullUpdatedAt_returnsTrue() {
        assertTrue(TtlUtils.isWithinTtlSeconds(null, 100));
    }

    @Test
    public void isWithinTtlSeconds_zeroTtl_returnsTrue() {
        assertTrue(TtlUtils.isWithinTtlSeconds(new Date(), 0));
    }

    @Test
    public void isWithinTtlSeconds_withinTtl_returnsTrue() {
        Date recent = new Date(System.currentTimeMillis() - 500);
        assertTrue(TtlUtils.isWithinTtlSeconds(recent, 10));
    }

    @Test
    public void isWithinTtlSeconds_expired_returnsFalse() {
        Date old = new Date(System.currentTimeMillis() - 20_000);
        assertFalse(TtlUtils.isWithinTtlSeconds(old, 5));
    }

    @Test
    public void hoursToSeconds_zero_returnsZero() {
        assertEquals(0L, TtlUtils.hoursToSeconds(0));
    }

    @Test
    public void hoursToSeconds_negative_returnsZero() {
        assertEquals(0L, TtlUtils.hoursToSeconds(-1));
    }

    @Test
    public void hoursToSeconds_one_returns3600() {
        assertEquals(3600L, TtlUtils.hoursToSeconds(1));
    }

    @Test
    public void hoursToSeconds_overflow_returnsMaxValue() {
        assertEquals(Long.MAX_VALUE, TtlUtils.hoursToSeconds(Long.MAX_VALUE / 3600 + 1));
    }

    @Test
    public void hoursMinutesToSeconds_oneHour30Min_returns5400() {
        assertEquals(5400L, TtlUtils.hoursMinutesToSeconds(1, 30));
    }

    @Test
    public void hoursMinutesToSeconds_zeroZero_returnsZero() {
        assertEquals(0L, TtlUtils.hoursMinutesToSeconds(0, 0));
    }

    @Test
    public void secondsToMillis_zero_returnsZero() {
        assertEquals(0L, TtlUtils.secondsToMillis(0));
    }

    @Test
    public void secondsToMillis_one_returns1000() {
        assertEquals(1000L, TtlUtils.secondsToMillis(1));
    }

    @Test
    public void secondsToMillis_overflow_returnsMaxValue() {
        assertEquals(Long.MAX_VALUE, TtlUtils.secondsToMillis(Long.MAX_VALUE / 1000 + 1));
    }
}
