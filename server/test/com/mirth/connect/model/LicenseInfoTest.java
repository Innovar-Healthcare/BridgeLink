/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 model POJO coverage — LicenseInfo getter/setter round-trips.
 */

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class LicenseInfoTest {

    // ------------------------------------------------------------------
    // Default constructor: boolean fields false, numeric null, sets empty
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_booleansFalse() {
        LicenseInfo li = new LicenseInfo();
        assertFalse(li.isActivated());
        assertFalse(li.isOnline());
        assertFalse(li.isError());
        assertFalse(li.isWarning());
        assertFalse(li.isPadlock());
        assertFalse(li.isPadlockWarning());
        assertFalse(li.isExpired());
        assertFalse(li.isKeyNotFound());
        assertFalse(li.isUnauthorized());
    }

    @Test
    public void defaultConstructor_numericsNull() {
        LicenseInfo li = new LicenseInfo();
        assertNull(li.getExpirationDate());
        assertNull(li.getWarningPeriod());
        assertNull(li.getGracePeriod());
    }

    @Test
    public void defaultConstructor_setsEmpty() {
        LicenseInfo li = new LicenseInfo();
        assertNotNull(li.getExtensions());
        assertTrue(li.getExtensions().isEmpty());
        assertNotNull(li.getUnpermittedExtensions());
        assertTrue(li.getUnpermittedExtensions().isEmpty());
        assertNotNull(li.getDownloadedExtensions());
        assertTrue(li.getDownloadedExtensions().isEmpty());
    }

    // ------------------------------------------------------------------
    // Boolean getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setActivated_isActivated_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setActivated(true);
        assertTrue(li.isActivated());
        li.setActivated(false);
        assertFalse(li.isActivated());
    }

    @Test
    public void setOnline_isOnline_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setOnline(true);
        assertTrue(li.isOnline());
    }

    @Test
    public void setError_isError_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setError(true);
        assertTrue(li.isError());
    }

    @Test
    public void setWarning_isWarning_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setWarning(true);
        assertTrue(li.isWarning());
    }

    @Test
    public void setPadlock_isPadlock_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setPadlock(true);
        assertTrue(li.isPadlock());
    }

    @Test
    public void setPadlockWarning_isPadlockWarning_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setPadlockWarning(true);
        assertTrue(li.isPadlockWarning());
    }

    @Test
    public void setExpired_isExpired_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setExpired(true);
        assertTrue(li.isExpired());
    }

    @Test
    public void setKeyNotFound_isKeyNotFound_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setKeyNotFound(true);
        assertTrue(li.isKeyNotFound());
    }

    @Test
    public void setUnauthorized_isUnauthorized_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setUnauthorized(true);
        assertTrue(li.isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Long getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setExpirationDate_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setExpirationDate(1000L);
        assertEquals(Long.valueOf(1000L), li.getExpirationDate());
    }

    @Test
    public void setWarningPeriod_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setWarningPeriod(30L);
        assertEquals(Long.valueOf(30L), li.getWarningPeriod());
    }

    @Test
    public void setGracePeriod_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setGracePeriod(7L);
        assertEquals(Long.valueOf(7L), li.getGracePeriod());
    }

    // ------------------------------------------------------------------
    // String reason fields
    // ------------------------------------------------------------------

    @Test
    public void setErrorReason_getErrorReason_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setErrorReason("License expired");
        assertEquals("License expired", li.getErrorReason());
    }

    @Test
    public void setWarningReason_getWarningReason_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        li.setWarningReason("Expiring soon");
        assertEquals("Expiring soon", li.getWarningReason());
    }

    // ------------------------------------------------------------------
    // Set getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setExtensions_getExtensions_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        Set<String> extensions = new HashSet<String>();
        extensions.add("ext1");
        extensions.add("ext2");
        li.setExtensions(extensions);
        assertEquals(2, li.getExtensions().size());
        assertTrue(li.getExtensions().contains("ext1"));
        assertTrue(li.getExtensions().contains("ext2"));
    }

    @Test
    public void setUnpermittedExtensions_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        Set<String> unpermitted = new HashSet<String>();
        unpermitted.add("blocked-ext");
        li.setUnpermittedExtensions(unpermitted);
        assertEquals(1, li.getUnpermittedExtensions().size());
        assertTrue(li.getUnpermittedExtensions().contains("blocked-ext"));
    }

    @Test
    public void setDownloadedExtensions_roundTrip() {
        LicenseInfo li = new LicenseInfo();
        Set<String> downloaded = new HashSet<String>();
        downloaded.add("ext-a");
        downloaded.add("ext-b");
        downloaded.add("ext-c");
        li.setDownloadedExtensions(downloaded);
        assertEquals(3, li.getDownloadedExtensions().size());
    }

    // ------------------------------------------------------------------
    // INSTANCE singleton: is non-null
    // ------------------------------------------------------------------

    @Test
    public void instanceSingleton_isNotNull() {
        assertNotNull(LicenseInfo.INSTANCE);
    }
}
