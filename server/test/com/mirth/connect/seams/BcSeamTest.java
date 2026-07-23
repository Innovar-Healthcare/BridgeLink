/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.seams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;

import com.mirth.commons.encryption.Digester;
import com.mirth.commons.encryption.Output;
import com.mirth.connect.server.controllers.DefaultConfigurationController;

/**
 * Dependency-seam characterization suite (D-09/D-10/D-11) for the SHIPPED bcprov/bcpkix/bcutil-
 * jdk18on 1.78.1 jars, as pinned by the v26.6.0 BouncyCastle upgrade rollback (commit
 * 6a483ab9d). Assertion logic for {@code generatesCertificateIntoEmptyKeystore} and
 * {@code passwordVerifyBehavior} is salvaged (D-11) from the reverted BC gap tests
 * ({@code git show 6a483ab9d^:server/test/.../CertificateGenerationTest.java} and
 * {@code UserLoginPasswordVerifyTest.java}), rewritten here to describe the CURRENT 1.78.1
 * behavior rather than the later target the original tests were written against.
 * <p>
 * Phase 23's BouncyCastle re-land MUST keep this suite green UNCHANGED.
 */
public class BcSeamTest {

    private static final String CERT_ALIAS = "mirthconnect";

    // ------------------------------------------------------------------------------------------
    // Test 1 (salvaged): certificate generation into an empty keystore
    // ------------------------------------------------------------------------------------------

    @Test
    public void generatesCertificateIntoEmptyKeystore() throws Exception {
        char[] storePassword = "bcSeamTestStorePass1".toCharArray();
        char[] keyPassword = "bcSeamTestKeyPass1".toCharArray();

        KeyStore keyStore = generateCertificateIntoFreshKeystore(storePassword, keyPassword);

        assertTrue("alias should be populated after generateDefaultCertificate", keyStore.containsAlias(CERT_ALIAS));

        KeyStore.Entry entry = keyStore.getEntry(CERT_ALIAS, new KeyStore.PasswordProtection(keyPassword));
        assertNotNull(entry);
        assertTrue("entry should be a PrivateKeyEntry", entry instanceof KeyStore.PrivateKeyEntry);

        PrivateKey privateKey = (PrivateKey) keyStore.getKey(CERT_ALIAS, keyPassword);
        assertNotNull("private key should be loadable with the key password", privateKey);
        assertEquals("RSA", privateKey.getAlgorithm());

        Certificate certificate = keyStore.getCertificate(CERT_ALIAS);
        assertNotNull(certificate);
        assertTrue("stored certificate should be an X509Certificate", certificate instanceof X509Certificate);

        X509Certificate x509Certificate = (X509Certificate) certificate;
        assertTrue("signature algorithm should be SHA256withRSA (was: " + x509Certificate.getSigAlgName() + ")", "SHA256withRSA".equalsIgnoreCase(x509Certificate.getSigAlgName()));
        // Throws CertificateExpiredException / CertificateNotYetValidException on failure.
        x509Certificate.checkValidity();
    }

    /**
     * Builds an empty in-memory JCEKS keystore, invokes the private
     * {@code generateDefaultCertificate} method via reflection using a fresh BC 1.78.1
     * {@link BouncyCastleProvider}, and returns the populated keystore.
     */
    private KeyStore generateCertificateIntoFreshKeystore(char[] storePassword, char[] keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, storePassword);

        // PRECONDITION: the empty keystore must NOT already contain the alias, otherwise the
        // containsAlias guard in generateDefaultCertificate would short-circuit and the
        // X509v3CertificateBuilder path (the actual subject of this seam) would never run.
        assertFalse("keystore must be empty before invoking generateDefaultCertificate", keyStore.containsAlias(CERT_ALIAS));

        Provider provider = new BouncyCastleProvider();
        // Constructed WITHOUT initialize() (salvage-source precedent) -- avoids Guice bootstrap;
        // generateDefaultCertificate reads no instance state, only its three parameters.
        DefaultConfigurationController controller = new DefaultConfigurationController();

        invokeGenerateDefaultCertificate(controller, provider, keyStore, keyPassword);

        return keyStore;
    }

    /**
     * Reflection hook into the private {@code generateDefaultCertificate(Provider, KeyStore,
     * char[])} method. Unwraps {@link InvocationTargetException} so a real BC API failure
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

    // ------------------------------------------------------------------------------------------
    // Test 2: sign/verify round trip over the generated key pair through the BC provider
    // ------------------------------------------------------------------------------------------

    @Test
    public void signAndVerifyRoundTrip() throws Exception {
        char[] storePassword = "bcSeamTestStorePass2".toCharArray();
        char[] keyPassword = "bcSeamTestKeyPass2".toCharArray();

        KeyStore keyStore = generateCertificateIntoFreshKeystore(storePassword, keyPassword);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(CERT_ALIAS, keyPassword);
        Certificate certificate = keyStore.getCertificate(CERT_ALIAS);

        Provider provider = new BouncyCastleProvider();
        byte[] sampleBytes = "BcSeamTest sign/verify sample payload".getBytes("UTF-8");

        Signature signer = Signature.getInstance("SHA256withRSA", provider);
        signer.initSign(privateKey);
        signer.update(sampleBytes);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA", provider);
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(sampleBytes);
        assertTrue("signature over the original bytes should verify", verifier.verify(signature));

        // Tampered data must NOT verify
        byte[] tamperedBytes = "BcSeamTest sign/verify TAMPERED payload".getBytes("UTF-8");
        Signature tamperedVerifier = Signature.getInstance("SHA256withRSA", provider);
        tamperedVerifier.initVerify(certificate.getPublicKey());
        tamperedVerifier.update(tamperedBytes);
        assertFalse("signature over tampered bytes must not verify", tamperedVerifier.verify(signature));
    }

    // ------------------------------------------------------------------------------------------
    // Test 3: TLS handshake using the generated keystore/certificate
    // ------------------------------------------------------------------------------------------

    @Test
    public void tlsHandshakeWithGeneratedCertificate() throws Exception {
        char[] storePassword = "bcSeamTestStorePass3".toCharArray();
        char[] keyPassword = "bcSeamTestKeyPass3".toCharArray();

        KeyStore keyStore = generateCertificateIntoFreshKeystore(storePassword, keyPassword);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyPassword);

        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagerFactory.getKeyManagers(), null, null);

        // Test-only trust-all client context: scoped to this ephemeral localhost handshake only,
        // never used outside this test method (ASVS V6 -- characterization, no hand-rolled crypto
        // beyond the standard javax.net.ssl API surface).
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }

            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}

            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
        } };
        SSLContext clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, trustAllCerts, new java.security.SecureRandom());

        SSLServerSocketFactory serverSocketFactory = serverContext.getServerSocketFactory();
        try (SSLServerSocket serverSocket = (SSLServerSocket) serverSocketFactory.createServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<String> serverCipherSuite = executor.submit(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        try (SSLSocket accepted = (SSLSocket) serverSocket.accept()) {
                            accepted.startHandshake();
                            return accepted.getSession().getCipherSuite();
                        }
                    }
                });

                SSLSocketFactory clientSocketFactory = clientContext.getSocketFactory();
                try (SSLSocket clientSocket = (SSLSocket) clientSocketFactory.createSocket(InetAddress.getLoopbackAddress(), port)) {
                    clientSocket.startHandshake();
                    assertNotNull("client-side handshake should complete with a non-null cipher suite", clientSocket.getSession().getCipherSuite());
                }

                String cipherSuite = serverCipherSuite.get(10, TimeUnit.SECONDS);
                assertNotNull("server-side handshake should complete with a non-null cipher suite", cipherSuite);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Test 4 (salvaged): password-hash verify behavior through a real BC-provider Digester
    // ------------------------------------------------------------------------------------------

    @Test
    public void passwordVerifyBehavior() throws Exception {
        Digester digester = new Digester();
        digester.setProvider(new BouncyCastleProvider());
        digester.setFormat(Output.BASE64);

        String plainPassword = "correct-horse-battery-staple";
        String wrongPassword = "incorrect-horse-battery-staple";
        String hash = digester.digest(plainPassword);

        assertTrue("matches() should return true for the matching plaintext/hash pair", digester.matches(plainPassword, hash));
        assertFalse("matches() should return false for a mismatched plaintext", digester.matches(wrongPassword, hash));
    }
}
