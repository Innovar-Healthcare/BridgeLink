/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.commons.encryption.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.security.SecureRandom;
import java.util.Arrays;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;

import com.mirth.commons.encryption.Digester;
import com.mirth.commons.encryption.EncryptionException;
import com.mirth.commons.encryption.Output;
import com.mirth.commons.encryption.PBEEncryptor;

/**
 * Regression test for GitHub issue #135 (SEC-02).
 *
 * <p>Asserts that BridgeLink no longer pins its salt-generator to the {@code "SHA1PRNG"}
 * algorithm, which is rejected by FIPS-enabled JVM 17 (RHEL 9 / AlmaLinux 9 with
 * {@code update-crypto-policies --set FIPS}) and causes {@code Digester.initialize()} and
 * {@code PBEEncryptor.initialize()} to throw {@code NoSuchAlgorithmException} at server
 * startup.
 *
 * <p>The fix is to construct {@link SecureRandom} via the no-arg constructor so the JVM
 * selects a provider-appropriate algorithm (NativePRNG on non-FIPS Linux, a FIPS-approved
 * DRBG on FIPS JVMs).
 */
public class SecureRandomAlgorithmTest {

    /**
     * JDK-only assertion: the default-provider {@code SecureRandom} on JDK 17 must not
     * resolve to {@code SHA1PRNG}. This test exercises {@link SecureRandom} directly
     * and does NOT depend on the BridgeLink production-code edit; it passes both before
     * and after the fix and serves as a guardrail against regressions on the JDK side.
     */
    @Test
    public void newSecureRandomDoesNotResolveToSHA1PRNG() {
        SecureRandom sr = new SecureRandom();
        assertNotNull("new SecureRandom() must not return null", sr.getAlgorithm());
        assertNotEquals("SHA1PRNG", sr.getAlgorithm());
    }

    /**
     * Before the fix lands: {@code Digester.initialize()} hard-codes
     * {@code SecureRandom.getInstance("SHA1PRNG")}, so the salt generator's algorithm name
     * IS {@code "SHA1PRNG"} and this test FAILS. After the fix lands, the algorithm name
     * is a JDK-default value and this test passes.
     */
    @Test
    public void digesterSaltGeneratorIsNotSHA1PRNGAfterInitialize() throws EncryptionException {
        Digester digester = new Digester();
        digester.setProvider(new BouncyCastleProvider());
        digester.setAlgorithm("PBKDF2WithHmacSHA256");
        digester.setIterations(1000);
        digester.setUsePBE(true);
        digester.setKeySizeBits(128);
        digester.setFormat(Output.BASE64);
        digester.initialize();

        SecureRandom saltGenerator = digester.getSaltGenerator();
        assertNotNull("Digester.initialize() must populate saltGenerator", saltGenerator);
        assertNotEquals("SHA1PRNG", saltGenerator.getAlgorithm());
    }

    /**
     * Before the fix lands: {@code PBEEncryptor.initialize()} hard-codes
     * {@code SecureRandom.getInstance("SHA1PRNG")}, so the salt generator's algorithm name
     * IS {@code "SHA1PRNG"} and this test FAILS. After the fix lands, the algorithm name
     * is a JDK-default value and this test passes.
     */
    @Test
    public void pbeEncryptorSaltGeneratorIsNotSHA1PRNGAfterInitialize() throws EncryptionException {
        PBEEncryptor encryptor = new PBEEncryptor();
        encryptor.setAlgorithm("PBEWithMD5AndDES");
        encryptor.setPassword("test-only-not-real");
        encryptor.initialize();

        SecureRandom saltGenerator = encryptor.getSaltGenerator();
        assertNotNull("PBEEncryptor.initialize() must populate saltGenerator", saltGenerator);
        assertNotEquals("SHA1PRNG", saltGenerator.getAlgorithm());
    }

    /**
     * Probabilistic randomness sanity check (Pitfall 2 mitigation in the threat model):
     * two successive {@code new SecureRandom()} instances must produce different output.
     * A deterministic-seed RNG misconfiguration (which would silently weaken every salt
     * BridgeLink generates) is the most obvious failure mode this catches.
     */
    @Test
    public void twoCallsToNewSecureRandomProduceDifferentBytes() {
        SecureRandom a = new SecureRandom();
        SecureRandom b = new SecureRandom();
        byte[] ba = new byte[16];
        byte[] bb = new byte[16];
        a.nextBytes(ba);
        b.nextBytes(bb);
        assertFalse("Two independent SecureRandom instances must not produce identical output",
                Arrays.equals(ba, bb));
    }
}
