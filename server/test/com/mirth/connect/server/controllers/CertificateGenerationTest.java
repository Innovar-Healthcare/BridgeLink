/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;

/**
 * BC GAP 1 regression (10-IMPACT-ANALYSIS.md &sect;4): proves that fresh self-signed
 * certificate generation still works against the BouncyCastle 1.84 jars.
 * <p>
 * {@code DefaultConfigurationController.generateDefaultCertificate(Provider, KeyStore, char[])}
 * is the exact BC API surface most likely to drift across the 1.78.1&rarr;1.84 jump
 * ({@code X509v3CertificateBuilder} / {@code JcaContentSignerBuilder} /
 * {@code JcaX509CertificateConverter}). The method is {@code private} and reads no
 * instance state &mdash; only its three parameters plus a logger &mdash; so it is invoked here via
 * reflection ({@code getDeclaredMethod} + {@code setAccessible(true)}) against a controller built
 * with the empty public no-arg constructor ({@code new DefaultConfigurationController()},
 * which deliberately does NOT call {@code initialize()}).
 * <p>
 * <b>Why the existing "coverage" never reaches this code:</b>
 * {@code DataPrunerTests#init()} calls {@code initializeSecuritySettings()} in a
 * {@code @BeforeClass}, but it runs against the committed {@code server/keystore.jks} /
 * {@code keystore.properties} fixtures, which already contain the {@code "mirthconnect"} alias.
 * {@code generateDefaultCertificate}'s {@code if (!keyStore.containsAlias(certificateAlias))}
 * guard therefore short-circuits and the X.509 builder path is never exercised. Every test below
 * asserts {@code !containsAlias("mirthconnect")} on a brand-new, empty, in-memory keystore
 * BEFORE invoking &mdash; the inverse of the DataPrunerTests situation &mdash; to guarantee the
 * cert-building branch is actually reached.
 */
public class CertificateGenerationTest {

    private static final String CERT_ALIAS = "mirthconnect";

    /**
     * Builds an empty in-memory JCEKS keystore, invokes the private
     * {@code generateDefaultCertificate} method via reflection using a fresh BC 1.84
     * {@link BouncyCastleProvider}, and returns the populated keystore.
     */
    private KeyStore generateCertificateIntoFreshKeystore(char[] storePassword, char[] keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, storePassword);

        // PRECONDITION: the empty keystore must NOT already contain the alias, otherwise the
        // containsAlias guard in generateDefaultCertificate would short-circuit and the
        // X509v3CertificateBuilder path (the actual subject of this regression) would never run.
        assertFalse("keystore must be empty before invoking generateDefaultCertificate", keyStore.containsAlias(CERT_ALIAS));

        Provider provider = new BouncyCastleProvider();
        DefaultConfigurationController controller = new DefaultConfigurationController();

        invokeGenerateDefaultCertificate(controller, provider, keyStore, keyPassword);

        return keyStore;
    }

    /**
     * Reflection hook into the private {@code generateDefaultCertificate(Provider, KeyStore,
     * char[])} method. Unwraps {@link InvocationTargetException} so a real BC 1.84 API failure
     * surfaces as the underlying exception in the JUnit report, not a reflection wrapper.
     */
    private void invokeGenerateDefaultCertificate(DefaultConfigurationController controller, Provider provider, KeyStore keyStore, char[] keyPassword) throws Exception {
        Method method = DefaultConfigurationController.class.getDeclaredMethod("generateDefaultCertificate", Provider.class, KeyStore.class, char[].class);
        method.setAccessible(true);

        try {
            method.invoke(controller, provider, keyStore, keyPassword);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    @Test
    public void generatesCertificateIntoEmptyKeystore() throws Exception {
        char[] storePassword = "certGenTestStorePass1".toCharArray();
        char[] keyPassword = "certGenTestKeyPass1".toCharArray();

        KeyStore keyStore = generateCertificateIntoFreshKeystore(storePassword, keyPassword);

        assertTrue("alias should be populated after generateDefaultCertificate", keyStore.containsAlias(CERT_ALIAS));

        KeyStore.Entry entry = keyStore.getEntry(CERT_ALIAS, new KeyStore.PasswordProtection(keyPassword));
        assertNotNull(entry);
        assertTrue("entry should be a PrivateKeyEntry", entry instanceof KeyStore.PrivateKeyEntry);

        PrivateKey privateKey = (PrivateKey) keyStore.getKey(CERT_ALIAS, keyPassword);
        assertNotNull("private key should be loadable with the key password", privateKey);
        assertEquals("RSA", privateKey.getAlgorithm());
    }

    @Test
    public void certificateIsSha256WithRsaAndValid() throws Exception {
        char[] storePassword = "certGenTestStorePass2".toCharArray();
        char[] keyPassword = "certGenTestKeyPass2".toCharArray();

        KeyStore keyStore = generateCertificateIntoFreshKeystore(storePassword, keyPassword);

        Certificate certificate = keyStore.getCertificate(CERT_ALIAS);
        assertNotNull(certificate);
        assertTrue("stored certificate should be an X509Certificate", certificate instanceof X509Certificate);

        X509Certificate x509Certificate = (X509Certificate) certificate;

        assertTrue("signature algorithm should be SHA256withRSA (was: " + x509Certificate.getSigAlgName() + ")", "SHA256withRSA".equalsIgnoreCase(x509Certificate.getSigAlgName()));

        // Throws CertificateExpiredException / CertificateNotYetValidException on failure.
        x509Certificate.checkValidity();

        assertEquals("CN=mirth-connect", x509Certificate.getSubjectX500Principal().getName());
        assertEquals("CN=BridgeLink Certificate Authority", x509Certificate.getIssuerX500Principal().getName());
    }

    @Test
    public void keystoreUsableForSslContext() throws Exception {
        char[] storePassword = "certGenTestStorePass3".toCharArray();
        char[] keyPassword = "certGenTestKeyPass3".toCharArray();

        KeyStore keyStore = generateCertificateIntoFreshKeystore(storePassword, keyPassword);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyPassword);
        assertNotNull(keyManagerFactory.getKeyManagers());
        assertTrue("KeyManagerFactory should produce at least one KeyManager from the generated entry", keyManagerFactory.getKeyManagers().length > 0);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        assertNotNull(sslContext);
    }
}
