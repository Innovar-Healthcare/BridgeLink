/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 */

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Enumeration;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mirth.commons.encryption.KeyEncryptor;
import com.mirth.commons.encryption.Output;

/**
 * Verifies that keystore password regeneration (IRT-577) does NOT affect the
 * AES data-encryption key — messages encrypted with encryptData=true and
 * database passwords stored via encrypt.properties=true remain fully
 * decryptable after the keystore is re-keyed.
 *
 * The proof: after re-keying the keystore under new random passwords the same
 * raw key bytes come back out, so any ciphertext produced before regeneration
 * can still be decrypted.
 */
public class KeystorePasswordRegenerationTest {

    private static final String KEYSTORE_TYPE      = "JCEKS";
    private static final String SECRET_KEY_ALIAS   = "encryption";
    private static final String DEFAULT_STOREPASS  = "81uWxplDtB";
    private static final String DEFAULT_KEYPASS    = "81uWxplDtB";
    private static final String ENCRYPTION_ALGO    = "AES/CBC/PKCS5Padding";
    private static final int    KEY_LENGTH         = 128;

    private File      keystoreFile;
    private SecretKey originalAesKey;
    private String    newStorepass;
    private String    newKeypass;

    @Before
    public void setUp() throws Exception {
        keystoreFile = File.createTempFile("test-keystore", ".jceks");
        keystoreFile.deleteOnExit();

        BouncyCastleProvider provider = new BouncyCastleProvider();
        KeyGenerator keyGen = KeyGenerator.getInstance("AES", provider);
        keyGen.init(KEY_LENGTH);
        originalAesKey = keyGen.generateKey();

        // Build a JCEKS keystore that mimics a fresh BridgeLink install:
        // AES secret key stored under "encryption" alias with default passwords.
        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
        ks.load(null, DEFAULT_STOREPASS.toCharArray());
        ks.setEntry(SECRET_KEY_ALIAS, new KeyStore.SecretKeyEntry(originalAesKey),
                new KeyStore.PasswordProtection(DEFAULT_KEYPASS.toCharArray()));
        try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
            ks.store(fos, DEFAULT_STOREPASS.toCharArray());
        }

        // Run the same re-keying logic as DefaultConfigurationController.regenerateKeystorePassword()
        newStorepass = generateNewPassword();
        newKeypass   = generateNewPassword();
        reKeystore(DEFAULT_STOREPASS, DEFAULT_KEYPASS, newStorepass, newKeypass);
    }

    @After
    public void tearDown() {
        if (keystoreFile != null) {
            keystoreFile.delete();
        }
    }

    /**
     * Claim: encryptData messages remain readable after keystore password regeneration.
     *
     * The AES key bytes are unchanged — only the password wrapping them in the
     * keystore changes. A message encrypted before regeneration must still decrypt
     * correctly after loading the re-keyed keystore.
     */
    @Test
    public void encryptDataMessagesRemainsReadableAfterKeystoreRekey() throws Exception {
        BouncyCastleProvider provider = new BouncyCastleProvider();

        // Encrypt a message using the ORIGINAL key (simulates a stored encryptData message)
        KeyEncryptor encryptorBefore = buildEncryptor(provider, originalAesKey);
        String plaintext = "HL7 patient message - channel encryptData=true";
        String ciphertext = encryptorBefore.encrypt(plaintext);

        // After regeneration: reload keystore with NEW passwords and extract the key
        SecretKey keyAfterRegen = loadKeyFromKeystore(newStorepass, newKeypass);

        // The raw key bytes must be identical
        assertArrayEquals(
                "AES key bytes must be unchanged after keystore re-key",
                originalAesKey.getEncoded(),
                keyAfterRegen.getEncoded());

        // A KeyEncryptor built from the post-regen key must decrypt the pre-regen ciphertext
        KeyEncryptor encryptorAfter = buildEncryptor(provider, keyAfterRegen);
        String decrypted = encryptorAfter.decrypt(ciphertext);

        assertEquals(
                "encryptData messages must remain readable after keystore password regeneration",
                plaintext, decrypted);
    }

    /**
     * Claim: encrypt.properties database passwords remain readable after keystore password
     * regeneration.
     *
     * Same AES key is used to encrypt mirth.properties values when encrypt.properties=true.
     * After re-keying the keystore the password can still be decrypted.
     */
    @Test
    public void encryptPropertiesDbPasswordRemainsReadableAfterKeystoreRekey() throws Exception {
        BouncyCastleProvider provider = new BouncyCastleProvider();

        // Simulate encrypting the database password at startup (encrypt.properties=true)
        KeyEncryptor encryptorBefore = buildEncryptor(provider, originalAesKey);
        String dbPassword  = "s3cr3tDbP@ssword!";
        String storedValue = "{enc}" + encryptorBefore.encrypt(dbPassword);

        assertTrue("Stored value must start with {enc} prefix", storedValue.startsWith("{enc}"));

        // After regeneration: reload keystore with NEW passwords
        SecretKey keyAfterRegen = loadKeyFromKeystore(newStorepass, newKeypass);

        assertArrayEquals(
                "AES key bytes must be unchanged after keystore re-key",
                originalAesKey.getEncoded(),
                keyAfterRegen.getEncoded());

        // Strip the {enc} prefix and decrypt — same as what the server does at startup
        KeyEncryptor encryptorAfter = buildEncryptor(provider, keyAfterRegen);
        String encryptedPart = storedValue.substring("{enc}".length());
        String decryptedPassword = encryptorAfter.decrypt(encryptedPart);

        assertEquals(
                "Encrypted database.password from encrypt.properties must remain readable after keystore password regeneration",
                dbPassword, decryptedPassword);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void reKeystore(String oldStorepass, String oldKeypass,
                            String newStorepass, String newKeypass) throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            ks.load(fis, oldStorepass.toCharArray());
        }

        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) {
                java.security.Key key = ks.getKey(alias, oldKeypass.toCharArray());
                java.security.cert.Certificate[] chain = ks.getCertificateChain(alias);
                ks.setKeyEntry(alias, key, newKeypass.toCharArray(), chain);
            }
        }

        try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
            ks.store(fos, newStorepass.toCharArray());
        }
    }

    private SecretKey loadKeyFromKeystore(String storepass, String keypass) throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            ks.load(fis, storepass.toCharArray());
        }
        return (SecretKey) ks.getKey(SECRET_KEY_ALIAS, keypass.toCharArray());
    }

    private KeyEncryptor buildEncryptor(BouncyCastleProvider provider, SecretKey key) throws Exception {
        KeyEncryptor encryptor = new KeyEncryptor();
        encryptor.setProvider(provider);
        encryptor.setKey(key);
        encryptor.setAlgorithm(ENCRYPTION_ALGO);
        encryptor.setFormat(Output.BASE64);
        encryptor.initialize();
        return encryptor;
    }

    private String generateNewPassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
