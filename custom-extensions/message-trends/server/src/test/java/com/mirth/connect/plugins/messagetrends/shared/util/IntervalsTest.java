package com.mirth.connect.plugins.messagetrends.shared.util;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

public class IntervalsTest {

    @Test
    public void minutesOf_1minute() {
        assertEquals(1, Intervals.minutesOf("1minute"));
    }

    @Test
    public void minutesOf_5minute() {
        assertEquals(5, Intervals.minutesOf("5minute"));
    }

    @Test
    public void minutesOf_15minute() {
        assertEquals(15, Intervals.minutesOf("15minute"));
    }

    @Test
    public void minutesOf_60minute() {
        assertEquals(60, Intervals.minutesOf("60minute"));
    }

    @Test
    public void minutesOf_daily() {
        assertEquals(1440, Intervals.minutesOf("daily"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void minutesOf_null_throws() {
        Intervals.minutesOf(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void minutesOf_blank_throws() {
        Intervals.minutesOf("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void minutesOf_invalid_throws() {
        Intervals.minutesOf("hourly");
    }

    @Test
    public void isValid_validCodes() {
        assertTrue(Intervals.isValid("1minute"));
        assertTrue(Intervals.isValid("5minute"));
        assertTrue(Intervals.isValid("15minute"));
        assertTrue(Intervals.isValid("60minute"));
        assertTrue(Intervals.isValid("daily"));
    }

    @Test
    public void isValid_null_returnsFalse() {
        assertFalse(Intervals.isValid(null));
    }

    @Test
    public void isValid_invalid_returnsFalse() {
        assertFalse(Intervals.isValid("weekly"));
    }

    @Test
    public void canonicalOfMinutes_1() {
        assertEquals("1minute", Intervals.canonicalOfMinutes(1));
    }

    @Test
    public void canonicalOfMinutes_5() {
        assertEquals("5minute", Intervals.canonicalOfMinutes(5));
    }

    @Test
    public void canonicalOfMinutes_1440() {
        assertEquals("daily", Intervals.canonicalOfMinutes(1440));
    }

    @Test(expected = IllegalArgumentException.class)
    public void canonicalOfMinutes_invalid_throws() {
        Intervals.canonicalOfMinutes(99);
    }

    @Test
    public void canonicalCodes_nonNullSize5() {
        List<String> codes = Intervals.canonicalCodes();
        assertNotNull(codes);
        assertEquals(5, codes.size());
    }
}
