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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.ibatis.session.SqlSessionManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;

import com.mirth.commons.encryption.Digester;
import com.mirth.commons.encryption.Output;
import com.mirth.connect.model.Credentials;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.model.PasswordRequirements;
import com.mirth.connect.model.User;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.DefaultConfigurationController;
import com.mirth.connect.server.controllers.DefaultUserController;
import com.mirth.connect.server.controllers.ExtensionController;
import com.mirth.connect.server.controllers.UserController;
import com.mirth.connect.server.util.SqlConfig;
import com.mirth.connect.server.util.StatementLock;

/**
 * Dependency-seam characterization suite (D-13/D-14) for the BC 1.84 bcprov/bcpkix/bcutil-jdk18on
 * jars landed by this plan (Phase 23-03). Restores the 4 gap-test assertions the v26.6.0
 * BouncyCastle upgrade revert (commit {@code 6a483ab9d}) deleted and never restored into
 * {@link BcSeamTest}, recovered from {@code git show
 * 6a483ab9d^:server/test/com/mirth/connect/server/controllers/CertificateGenerationTest.java} and
 * {@code UserLoginPasswordVerifyTest.java}, rewritten here against BC 1.84.
 * <p>
 * <b>Complement contract:</b> {@link BcSeamTest} is LOCKED (D-12) and characterizes what the
 * SHIPPED 1.78.1 jars did before this plan landed &mdash; it stays green UNCHANGED across the whole
 * phase and is evidence of NO REGRESSION, not evidence FOR 1.84. THIS suite states what 1.84 MUST
 * DO and is the actual assertion that the upgrade behaves correctly.
 * <p>
 * <b>Per-assertion residue vs {@link BcSeamTest} (D-13, so a reviewer does not credit SC-2 with a
 * duplicate):</b>
 * <ul>
 * <li>{@link #certificateIsSha256WithRsaAndValid()} &mdash; {@code BcSeamTest
 * .generatesCertificateIntoEmptyKeystore} already asserts {@code SHA256withRSA} and calls
 * {@code checkValidity()}. This test's ONLY unique residue is the subject/issuer distinguished-name
 * pair ({@code CN=mirth-connect} / {@code CN=BridgeLink Certificate Authority}).</li>
 * <li>{@link #keystoreUsableForSslContext()} &mdash; largely subsumed by {@code BcSeamTest
 * .tlsHandshakeWithGeneratedCertificate}, which completes a REAL loopback TLS handshake. This test
 * only asserts {@code SSLContext.init(...)} does not throw and produces a non-null context; it is
 * restored because D-13 mandates it, not as SC-2's HTTPS-handshake evidence.</li>
 * <li>{@link #authorizeUserSucceedsWithRealDigester()} &mdash; genuinely new value: the only test
 * that drives {@code DefaultUserController.authorizeUser(username, plainPassword, serverURL)}
 * end-to-end with a real BC-1.84-provider {@link Digester}. <b>Pre-declared JDK-25-red by
 * construction:</b> it opens three {@code mockStatic} scopes ({@code ControllerFactory},
 * {@code StatementLock}, {@code SqlConfig}) against the bundled mockito 5.1.1 / byte-buddy 1.14.13,
 * which cannot mock/instrument concrete classes under JDK 25 (IRT-1488, ON HOLD). This is expected,
 * not a Phase 23 regression, and JDK 25 is not a Phase 23 completion gate (D-29.3).</li>
 * <li>{@link #crossProviderHashStillVerifies()} &mdash; the highest-value assertion in the phase: it
 * is the only one that answers whether existing customer password hashes (produced by the JDK's
 * SunJCE provider before this upgrade) still verify through a BC-1.84-wired {@link Digester} after
 * it.</li>
 * </ul>
 */
public class Bc184UpgradeTest {

    private static final String CERT_ALIAS = "mirthconnect";

    // ------------------------------------------------------------------------------------------
    // Test 1 (restored): generated certificate carries the expected subject/issuer DN under 1.84
    // ------------------------------------------------------------------------------------------

    @Test
    public void certificateIsSha256WithRsaAndValid() throws Exception {
        char[] storePassword = "bc184StorePass1".toCharArray();
        char[] keyPassword = "bc184KeyPass1".toCharArray();

        Provider provider = new BouncyCastleProvider();
        KeyStore keyStore = generateCertificateIntoFreshKeystore(provider, storePassword, keyPassword);

        Certificate certificate = keyStore.getCertificate(CERT_ALIAS);
        assertNotNull(certificate);
        assertTrue("stored certificate should be an X509Certificate", certificate instanceof X509Certificate);

        X509Certificate x509Certificate = (X509Certificate) certificate;

        assertTrue("signature algorithm should be SHA256withRSA (was: " + x509Certificate.getSigAlgName() + ")", "SHA256withRSA".equalsIgnoreCase(x509Certificate.getSigAlgName()));

        // Throws CertificateExpiredException / CertificateNotYetValidException on failure.
        x509Certificate.checkValidity();

        // Unique residue vs BcSeamTest (see class Javadoc): the subject/issuer DN pair.
        assertEquals("CN=mirth-connect", x509Certificate.getSubjectX500Principal().getName());
        assertEquals("CN=BridgeLink Certificate Authority", x509Certificate.getIssuerX500Principal().getName());
    }

    /**
     * Builds an empty in-memory JCEKS keystore, invokes the private
     * {@code generateDefaultCertificate} method via reflection using the supplied fresh BC 1.84
     * {@link BouncyCastleProvider}, and returns the populated keystore. The provider is
     * constructed by each caller (never shared or JVM-registered) so the two suites stay
     * non-interfering under {@code forkmode="perTest"}.
     */
    private KeyStore generateCertificateIntoFreshKeystore(Provider provider, char[] storePassword, char[] keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, storePassword);

        // PRECONDITION: the empty keystore must NOT already contain the alias, otherwise the
        // containsAlias guard inside generateDefaultCertificate would short-circuit and the
        // X509v3CertificateBuilder path (the actual subject of this seam) would never run.
        assertFalse("keystore must be empty before invoking generateDefaultCertificate", keyStore.containsAlias(CERT_ALIAS));

        // Constructed WITHOUT initialize() (salvage-source precedent) -- avoids Guice bootstrap;
        // generateDefaultCertificate reads no instance state, only its three parameters.
        DefaultConfigurationController controller = new DefaultConfigurationController();

        invokeGenerateDefaultCertificate(controller, provider, keyStore, keyPassword);

        return keyStore;
    }

    /**
     * Reflection hook into the private {@code generateDefaultCertificate(Provider, KeyStore,
     * char[])} method. Unwraps {@link InvocationTargetException} so a real BC 1.84 API break is
     * readable in the JUnit XML as the underlying exception, not an opaque reflection wrapper --
     * do NOT skip the unwrap.
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
    // Test 2 (restored): generated keystore is usable to build a KeyManagerFactory/SSLContext
    // ------------------------------------------------------------------------------------------

    @Test
    public void keystoreUsableForSslContext() throws Exception {
        char[] storePassword = "bc184StorePass2".toCharArray();
        char[] keyPassword = "bc184KeyPass2".toCharArray();

        Provider provider = new BouncyCastleProvider();
        KeyStore keyStore = generateCertificateIntoFreshKeystore(provider, storePassword, keyPassword);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyPassword);
        assertNotNull(keyManagerFactory.getKeyManagers());
        assertTrue("KeyManagerFactory should produce at least one KeyManager from the generated entry", keyManagerFactory.getKeyManagers().length > 0);

        // Largely subsumed by BcSeamTest.tlsHandshakeWithGeneratedCertificate (a real loopback
        // handshake) -- see class Javadoc residue note. Restored because D-13 mandates it.
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        assertNotNull(sslContext);
    }

    // ------------------------------------------------------------------------------------------
    // Test 3 (restored): end-to-end authorizeUser succeeds with a real BC-1.84 Digester
    // ------------------------------------------------------------------------------------------

    private Digester createRealBcDigester() {
        Digester digester = new Digester();
        digester.setProvider(new BouncyCastleProvider());
        digester.setFormat(Output.BASE64);
        return digester;
    }

    @Test
    public void authorizeUserSucceedsWithRealDigester() throws Exception {
        String username = "bc184TestAdmin";
        String plainPassword = "bc-184-real-digester-pw";
        String serverURL = "https://localhost:8443";

        try (var mockedControllerFactory = mockStatic(ControllerFactory.class);
                var mockedStatementLock = mockStatic(StatementLock.class);
                var mockedSqlConfig = mockStatic(SqlConfig.class)) {

            ControllerFactory mockFactory = mock(ControllerFactory.class);
            ConfigurationController mockConfigController = mock(ConfigurationController.class);
            ExtensionController mockExtensionController = mock(ExtensionController.class);
            UserController mockUserController = mock(UserController.class);

            Digester digester = createRealBcDigester();
            String hash = digester.digest(plainPassword);

            mockedControllerFactory.when(ControllerFactory::getFactory).thenReturn(mockFactory);
            when(mockFactory.createConfigurationController()).thenReturn(mockConfigController);
            when(mockFactory.createExtensionController()).thenReturn(mockExtensionController);
            when(mockFactory.createUserController()).thenReturn(mockUserController);

            when(mockConfigController.getDigester()).thenReturn(digester);
            // Real defaults: expiration 0 (no expiration checks), retryLimit 0 (lockout disabled)
            when(mockConfigController.getPasswordRequirements()).thenReturn(new PasswordRequirements());

            // No secondary/authorization plugins configured - LoginStatus passes through unchanged
            when(mockExtensionController.getAuthorizationPlugin()).thenReturn(null);
            when(mockExtensionController.getMultiFactorAuthenticationPlugin()).thenReturn(null);

            StatementLock mockLock = mock(StatementLock.class);
            mockedStatementLock.when(() -> StatementLock.getInstance(anyString())).thenReturn(mockLock);

            SqlConfig mockSqlConfig = mock(SqlConfig.class);
            SqlSessionManager mockSessionManager = mock(SqlSessionManager.class);
            mockedSqlConfig.when(SqlConfig::getInstance).thenReturn(mockSqlConfig);
            when(mockSqlConfig.getReadOnlySqlSessionManager()).thenReturn(mockSessionManager);
            when(mockSqlConfig.getSqlSessionManager()).thenReturn(mockSessionManager);

            User storedUser = new User();
            storedUser.setId(42);
            storedUser.setUsername(username);
            // No grace period start, no strike data -> not locked out, not expired

            Credentials credentials = new Credentials();
            credentials.setPassword(hash);
            credentials.setPasswordDate(Calendar.getInstance());

            when(mockSessionManager.selectOne(eq("User.getUser"), any())).thenReturn(storedUser);
            when(mockSessionManager.selectOne(eq("User.getLatestUserCredentials"), any())).thenReturn(credentials);

            // Construct INSIDE the mockStatic scope - authorizeUser lazily caches extensionController
            DefaultUserController userController = new DefaultUserController();

            LoginStatus result = userController.authorizeUser(username, plainPassword, serverURL);

            assertNotNull("authorizeUser should return a LoginStatus", result);
            assertEquals("Real BC-1.84 Digester should authenticate through the digester.matches branch", LoginStatus.Status.SUCCESS, result.getStatus());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Test 4 (restored, D-14 applied): a pre-upgrade SunJCE-produced hash still verifies under a
    // BC-1.84-wired Digester -- the highest-value assertion in the phase (T-23-12).
    // ------------------------------------------------------------------------------------------

    @Test
    public void crossProviderHashStillVerifies() throws Exception {
        // Practical stand-in for cross-version verification (pre-1.84-produced hash): PBKDF2
        // output must be provider-independent, so a hash produced by the JDK's SunJCE provider
        // must still verify true against a Digester wired with the BC 1.84 provider.
        Provider sunJceProvider = Security.getProvider("SunJCE");
        // PRECONDITION (D-14): SunJCE ships with every supported JDK, and server/build.xml's
        // test-run target already passes --add-exports=java.base/com.sun.crypto.provider=ALL-UNNAMED,
        // so the build actively guarantees reachability -- an absent provider here is a broken build,
        // not a skippable environment. The recovered original guarded this with a JUnit skip-on-
        // missing-precondition helper, which would report PASS on a skip (the exact Phase 25.1
        // plan-07 defect); replaced with a hard assertNotNull so an absent provider can never
        // report PASS. There is no in-repo precedent for that skip-based idiom in a seam suite --
        // zero occurrences across all three com.mirth.connect.seams classes -- and that absence
        // is itself the convention.
        assertNotNull("SunJCE must be present -- it ships with every supported JDK", sunJceProvider);

        Digester sunJceDigester = new Digester();
        sunJceDigester.setProvider(sunJceProvider);
        sunJceDigester.setFormat(Output.BASE64);

        Digester bcDigester = createRealBcDigester();

        // Never rewrite the digest algorithm: PBKDF2WithHmacSHA256, 600000 iterations, 256-bit
        // key, 8-byte salt (Digester class defaults) is the production configuration; changing
        // any parameter would silently invalidate every existing customer password hash.
        String plainPassword = "cross-provider-pbkdf2-check";
        String sunJceHash = sunJceDigester.digest(plainPassword);

        assertTrue("BC-provider Digester should verify a hash produced by the SunJCE provider (PBKDF2WithHmacSHA256 output is provider-independent)", bcDigester.matches(plainPassword, sunJceHash));
    }
}
