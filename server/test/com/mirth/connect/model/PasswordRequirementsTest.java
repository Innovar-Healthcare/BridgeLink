/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 model POJO coverage — PasswordRequirements constructors and getter/setter.
 */

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasswordRequirementsTest {

    // ------------------------------------------------------------------
    // No-arg constructor: all int fields default to 0, boolean to false
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_allZeroAndFalse() {
        PasswordRequirements r = new PasswordRequirements();
        assertEquals(0, r.getMinLength());
        assertEquals(0, r.getMinUpper());
        assertEquals(0, r.getMinLower());
        assertEquals(0, r.getMinNumeric());
        assertEquals(0, r.getMinSpecial());
        assertEquals(0, r.getRetryLimit());
        assertEquals(0, r.getLockoutPeriod());
        assertEquals(0, r.getExpiration());
        assertEquals(0, r.getGracePeriod());
        assertEquals(0, r.getReusePeriod());
        assertEquals(0, r.getReuseLimit());
        assertFalse(r.getAllowUsernameEnumeration());
    }

    // ------------------------------------------------------------------
    // Full constructor (12-arg): sets all fields
    // ------------------------------------------------------------------

    @Test
    public void fullConstructor_setsAllFields() {
        PasswordRequirements r = new PasswordRequirements(8, 2, 2, 1, 1, 5, 30, 90, 7, 180, 3, true);
        assertEquals(8, r.getMinLength());
        assertEquals(2, r.getMinUpper());
        assertEquals(2, r.getMinLower());
        assertEquals(1, r.getMinNumeric());
        assertEquals(1, r.getMinSpecial());
        assertEquals(5, r.getRetryLimit());
        assertEquals(30, r.getLockoutPeriod());
        assertEquals(90, r.getExpiration());
        assertEquals(7, r.getGracePeriod());
        assertEquals(180, r.getReusePeriod());
        assertEquals(3, r.getReuseLimit());
        assertTrue(r.getAllowUsernameEnumeration());
    }

    // ------------------------------------------------------------------
    // Deprecated 11-arg constructor: allowUsernameEnumeration defaults false
    // ------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    @Test
    public void deprecatedConstructor_allowUsernameEnumerationFalse() {
        PasswordRequirements r = new PasswordRequirements(6, 1, 1, 1, 0, 3, 15, 60, 5, 120, 2);
        assertEquals(6, r.getMinLength());
        assertFalse(r.getAllowUsernameEnumeration());
    }

    // ------------------------------------------------------------------
    // Setter/getter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setMinLength_getMinLength_roundTrip() {
        PasswordRequirements r = new PasswordRequirements();
        r.setMinLength(12);
        assertEquals(12, r.getMinLength());
    }

    @Test
    public void setMinUpper_getMinUpper_roundTrip() {
        PasswordRequirements r = new PasswordRequirements();
        r.setMinUpper(3);
        assertEquals(3, r.getMinUpper());
    }

    @Test
    public void setMinLower_getMinLower_roundTrip() {
        PasswordRequirements r = new PasswordRequirements();
        r.setMinLower(4);
        assertEquals(4, r.getMinLower());
    }

    @Test
    public void setMinNumeric_getMinNumeric_roundTrip() {
        PasswordRequirements r = new PasswordRequirements();
        r.setMinNumeric(2);
        assertEquals(2, r.getMinNumeric());
    }

    @Test
    public void setMinSpecial_getMinSpecial_roundTrip() {
        PasswordRequirements r = new PasswordRequirements();
        r.setMinSpecial(1);
        assertEquals(1, r.getMinSpecial());
    }

    @Test
    public void setRetryLimit_getRetryLimit_roundTrip() {
        PasswordRequirements r = new PasswordRequirements();
        r.setRetryLimit(5);
        assertEquals(5, r.getRetryLimit());
    }

    @Test
    public void setLockoutPeriod_getLockoutPeriod_roundTrip() {
        PasswordRequirements r = new PasswordRequirements();
        r.setLockoutPeriod(60);
        assertEquals(60, r.getLockoutPeriod());
    }

    @Test
    public void setExpiration_getExpiration_roundTrip() {
        PasswordRequirements r = new PasswordRequirements();
        r.setExpiration(90);
        assertEquals(90, r.getExpiration());
    }

    @Test
    public void setGracePeriod_getGracePeriod_roundTrip() {
        PasswordRequirements r = new PasswordRequirements();
        r.setGracePeriod(7);
        assertEquals(7, r.getGracePeriod());
    }

    @Test
    public void setReusePeriod_getReusePeriod_roundTrip() {
        PasswordRequirements r = new PasswordRequirements();
        r.setReusePeriod(180);
        assertEquals(180, r.getReusePeriod());
    }

    @Test
    public void setReuseLimit_getReuseLimit_roundTrip() {
        PasswordRequirements r = new PasswordRequirements();
        r.setReuseLimit(5);
        assertEquals(5, r.getReuseLimit());
    }

    @Test
    public void setAllowUsernameEnumeration_trueFalse() {
        PasswordRequirements r = new PasswordRequirements();
        r.setAllowUsernameEnumeration(true);
        assertTrue(r.getAllowUsernameEnumeration());
        r.setAllowUsernameEnumeration(false);
        assertFalse(r.getAllowUsernameEnumeration());
    }

    // ------------------------------------------------------------------
    // Zero-value boundary: all setters accept 0
    // ------------------------------------------------------------------

    @Test
    public void allSettersAcceptZero() {
        PasswordRequirements r = new PasswordRequirements(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, true);
        r.setMinLength(0);
        r.setMinUpper(0);
        r.setMinLower(0);
        r.setMinNumeric(0);
        r.setMinSpecial(0);
        r.setRetryLimit(0);
        r.setLockoutPeriod(0);
        r.setExpiration(0);
        r.setGracePeriod(0);
        r.setReusePeriod(0);
        r.setReuseLimit(0);
        assertEquals(0, r.getMinLength());
        assertEquals(0, r.getRetryLimit());
        assertEquals(0, r.getExpiration());
    }
}
