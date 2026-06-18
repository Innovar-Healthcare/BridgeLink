/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 model POJO coverage — EncryptionSettings getter/setter + Properties round-trip.
 */

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;

public class EncryptionSettingsTest {

    // ------------------------------------------------------------------
    // Default constructor: all fields are null
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_allFieldsNull() {
        EncryptionSettings s = new EncryptionSettings();
        assertNull(s.getEncryptExport());
        assertNull(s.getEncryptProperties());
        assertNull(s.getEncryptionAlgorithm());
        assertNull(s.getEncryptionCharset());
        assertNull(s.getEncryptionFallbackAlgorithm());
        assertNull(s.getEncryptionFallbackCharset());
        assertNull(s.getEncryptionKeyLength());
        assertNull(s.getDigestAlgorithm());
        assertNull(s.getDigestSaltSize());
        assertNull(s.getDigestIterations());
        assertNull(s.getDigestUsePBE());
        assertNull(s.getDigestKeySize());
        assertNull(s.getDigestFallbackAlgorithm());
        assertNull(s.getDigestFallbackSaltSize());
        assertNull(s.getDigestFallbackIterations());
        assertNull(s.getDigestFallbackUsePBE());
        assertNull(s.getDigestFallbackKeySize());
        assertNull(s.getSecurityProvider());
        assertNull(s.getSecretKey());
    }

    // ------------------------------------------------------------------
    // Properties constructor: loads default values from empty Properties
    // ------------------------------------------------------------------

    @Test
    public void propertiesConstructor_emptyProperties_usesDefaults() {
        EncryptionSettings s = new EncryptionSettings(new Properties());
        // setProperties uses default when property is absent
        assertEquals(EncryptionSettings.DEFAULT_ENCRYPTION_ALGORITHM, s.getEncryptionAlgorithm());
        assertEquals(EncryptionSettings.DEFAULT_ENCRYPTION_CHARSET, s.getEncryptionCharset());
        assertEquals(EncryptionSettings.DEFAULT_ENCRYPTION_KEY_LENGTH, s.getEncryptionKeyLength());
        assertEquals(EncryptionSettings.DEFAULT_DIGEST_ALGORITHM, s.getDigestAlgorithm());
        assertEquals(EncryptionSettings.DEFAULT_DIGEST_SALT_SIZE, s.getDigestSaltSize());
        assertEquals(EncryptionSettings.DEFAULT_DIGEST_ITERATIONS, s.getDigestIterations());
        assertEquals(EncryptionSettings.DEFAULT_DIGEST_USE_PBE, s.getDigestUsePBE());
        assertEquals(EncryptionSettings.DEFAULT_DIGEST_KEY_SIZE, s.getDigestKeySize());
        assertEquals(EncryptionSettings.DEFAULT_SECURITY_PROVIDER, s.getSecurityProvider());
    }

    // ------------------------------------------------------------------
    // Properties constructor: explicit values override defaults
    // ------------------------------------------------------------------

    @Test
    public void propertiesConstructor_explicitValues_overrideDefaults() {
        Properties p = new Properties();
        p.setProperty("encryption.algorithm", "AES/ECB/NoPadding");
        p.setProperty("encryption.charset", "ISO-8859-1");
        p.setProperty("encryption.keylength", "256");
        p.setProperty("digest.algorithm", "SHA256");
        p.setProperty("digest.saltsizeinbytes", "16");
        p.setProperty("digest.iterations", "100000");
        p.setProperty("digest.usepbe", "1");
        p.setProperty("digest.keysizeinbits", "512");

        EncryptionSettings s = new EncryptionSettings(p);

        assertEquals("AES/ECB/NoPadding", s.getEncryptionAlgorithm());
        assertEquals("ISO-8859-1", s.getEncryptionCharset());
        assertEquals(Integer.valueOf(256), s.getEncryptionKeyLength());
        assertEquals("SHA256", s.getDigestAlgorithm());
        assertEquals(Integer.valueOf(16), s.getDigestSaltSize());
        assertEquals(Integer.valueOf(100000), s.getDigestIterations());
        assertTrue(s.getDigestUsePBE());
        assertEquals(Integer.valueOf(512), s.getDigestKeySize());
    }

    // ------------------------------------------------------------------
    // getEncryptionBaseAlgorithm: extracts before slash
    // ------------------------------------------------------------------

    @Test
    public void getEncryptionBaseAlgorithm_withSlash_returnsBase() {
        EncryptionSettings s = new EncryptionSettings();
        s.setEncryptionAlgorithm("AES/CBC/PKCS5Padding");
        assertEquals("AES", s.getEncryptionBaseAlgorithm());
    }

    @Test
    public void getEncryptionBaseAlgorithm_noSlash_returnsWholeString() {
        EncryptionSettings s = new EncryptionSettings();
        s.setEncryptionAlgorithm("AES");
        assertEquals("AES", s.getEncryptionBaseAlgorithm());
    }

    @Test
    public void getEncryptionBaseAlgorithm_null_returnsNull() {
        EncryptionSettings s = new EncryptionSettings();
        s.setEncryptionAlgorithm(null);
        assertNull(s.getEncryptionBaseAlgorithm());
    }

    @Test
    public void getEncryptionBaseAlgorithm_blank_returnsBlank() {
        EncryptionSettings s = new EncryptionSettings();
        s.setEncryptionAlgorithm("   ");
        // StringUtils.isNotBlank("   ") is false, so returns encryptionAlgorithm unchanged
        assertEquals("   ", s.getEncryptionBaseAlgorithm());
    }

    // ------------------------------------------------------------------
    // Individual getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void getterSetter_encryptExport() {
        EncryptionSettings s = new EncryptionSettings();
        s.setEncryptExport(true);
        assertTrue(s.getEncryptExport());
        s.setEncryptExport(false);
        assertFalse(s.getEncryptExport());
    }

    @Test
    public void getterSetter_encryptProperties() {
        EncryptionSettings s = new EncryptionSettings();
        s.setEncryptProperties(true);
        assertTrue(s.getEncryptProperties());
    }

    @Test
    public void getterSetter_encryptionFallbackFields() {
        EncryptionSettings s = new EncryptionSettings();
        s.setEncryptionFallbackAlgorithm("DES");
        s.setEncryptionFallbackCharset("UTF-16");
        assertEquals("DES", s.getEncryptionFallbackAlgorithm());
        assertEquals("UTF-16", s.getEncryptionFallbackCharset());
    }

    @Test
    public void getterSetter_digestFallbackFields() {
        EncryptionSettings s = new EncryptionSettings();
        s.setDigestFallbackAlgorithm("MD5");
        s.setDigestFallbackSaltSize(4);
        s.setDigestFallbackIterations(500);
        s.setDigestFallbackUsePBE(false);
        s.setDigestFallbackKeySize(128);

        assertEquals("MD5", s.getDigestFallbackAlgorithm());
        assertEquals(Integer.valueOf(4), s.getDigestFallbackSaltSize());
        assertEquals(Integer.valueOf(500), s.getDigestFallbackIterations());
        assertFalse(s.getDigestFallbackUsePBE());
        assertEquals(Integer.valueOf(128), s.getDigestFallbackKeySize());
    }

    @Test
    public void getterSetter_secretKey() {
        EncryptionSettings s = new EncryptionSettings();
        byte[] key = {1, 2, 3, 4};
        s.setSecretKey(key);
        assertEquals(4, s.getSecretKey().length);
        assertEquals(1, s.getSecretKey()[0]);
    }

    @Test
    public void getterSetter_securityProvider() {
        EncryptionSettings s = new EncryptionSettings();
        s.setSecurityProvider("org.bouncycastle.jce.provider.BouncyCastleProvider");
        assertEquals("org.bouncycastle.jce.provider.BouncyCastleProvider", s.getSecurityProvider());
    }

    // ------------------------------------------------------------------
    // getProperties: round-trips all fields through getProperties()
    // ------------------------------------------------------------------

    @Test
    public void getProperties_roundTrip_encryptionFields() {
        EncryptionSettings s = new EncryptionSettings();
        s.setEncryptExport(true);
        s.setEncryptProperties(false);
        s.setEncryptionAlgorithm("AES/CBC/PKCS5Padding");
        s.setEncryptionCharset("UTF-8");
        s.setEncryptionFallbackAlgorithm("AES");
        s.setEncryptionFallbackCharset("UTF-8");
        s.setEncryptionKeyLength(128);

        Properties p = s.getProperties();
        assertNotNull(p);
        assertTrue(p.containsKey("encryption.export"));
        assertTrue(p.containsKey("encryption.algorithm"));
        assertTrue(p.containsKey("encryption.charset"));
        assertTrue(p.containsKey("encryption.keylength"));
    }

    @Test
    public void getProperties_roundTrip_digestFields() {
        EncryptionSettings s = new EncryptionSettings();
        s.setDigestAlgorithm("PBKDF2WithHmacSHA256");
        s.setDigestSaltSize(8);
        s.setDigestIterations(600000);
        s.setDigestUsePBE(true);
        s.setDigestKeySize(256);
        s.setDigestFallbackAlgorithm("SHA256");
        s.setDigestFallbackSaltSize(8);
        s.setDigestFallbackIterations(1000);
        s.setDigestFallbackUsePBE(false);
        s.setDigestFallbackKeySize(256);
        s.setSecurityProvider("org.bouncycastle.jce.provider.BouncyCastleProvider");

        Properties p = s.getProperties();
        assertTrue(p.containsKey("digest.algorithm"));
        assertTrue(p.containsKey("digest.saltsizeinbytes"));
        assertTrue(p.containsKey("digest.iterations"));
        assertTrue(p.containsKey("digest.usepbe"));
        assertTrue(p.containsKey("digest.keysizeinbits"));
        assertTrue(p.containsKey("digest.fallback.algorithm"));
        assertTrue(p.containsKey("security.provider"));
    }

    @Test
    public void getProperties_emptyWhenAllNull() {
        EncryptionSettings s = new EncryptionSettings();
        Properties p = s.getProperties();
        assertNotNull(p);
        assertTrue(p.isEmpty());
    }

    // ------------------------------------------------------------------
    // toAuditString: returns non-null non-empty string
    // ------------------------------------------------------------------

    @Test
    public void toAuditString_returnsNonEmpty() {
        EncryptionSettings s = new EncryptionSettings();
        s.setEncryptionAlgorithm("AES/CBC/PKCS5Padding");
        String str = s.toAuditString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
    }
}
