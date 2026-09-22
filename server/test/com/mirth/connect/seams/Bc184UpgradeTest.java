/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.seams;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Calendar;
import java.util.Properties;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.ibatis.session.SqlSessionManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;

import com.mirth.commons.encryption.Digester;
import com.mirth.commons.encryption.EncryptionException;
import com.mirth.commons.encryption.Output;
import com.mirth.connect.model.Credentials;
import com.mirth.connect.model.EncryptionSettings;
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
 * Dependency-seam characterization suite (D-01, D-03, D-11) for the BouncyCastle
 * (bcprov/bcpkix/bcutil-jdk18on) upgrade, last verified on 1.86 (IRT-2441, Phase 26.15).
 * <p>
 * <b>History.</b> Phase 23 created this suite for the 1.78.1 to 1.84 upgrade (restoring the four
 * gap assertions from commit {@code 6a483ab9d}), recovered from {@code git show
 * 6a483ab9d^:server/test/com/mirth/connect/server/controllers/CertificateGenerationTest.java} and
 * {@code UserLoginPasswordVerifyTest.java}. Phase 26.15 extended it for the 1.86 upgrade: keystore
 * round-trip coverage (D-03), the PBKDF2 iteration-cap behaviour (D-11), and a runtime-version
 * floor.
 * <p>
 * <b>Complement contract:</b> {@link BcSeamTest} is LOCKED (Phase 23 D-12, Phase 26.15 D-02) and
 * characterizes the pre-upgrade baseline (the SHIPPED 1.78.1 jars): it stays green UNCHANGED across
 * the whole phase and is evidence of NO REGRESSION, not evidence FOR the shipped BouncyCastle. THIS
 * suite states what the shipped BouncyCastle MUST DO and is the actual assertion that the upgrade
 * behaves correctly.
 * <p>
 * <b>Per-assertion residue vs {@link BcSeamTest} (Phase 23 D-13, so a reviewer does not credit this
 * suite with a duplicate):</b>
 * <ul>
 * <li>{@link #certificateIsSha256WithRsaAndValid()}: {@code BcSeamTest
 * .generatesCertificateIntoEmptyKeystore} already asserts {@code SHA256withRSA} and calls
 * {@code checkValidity()}. This test's ONLY unique residue is the subject/issuer distinguished-name
 * pair ({@code CN=mirth-connect} / {@code CN=BridgeLink Certificate Authority}).</li>
 * <li>{@link #keystoreUsableForSslContext()}: largely subsumed by {@code BcSeamTest
 * .tlsHandshakeWithGeneratedCertificate}, which completes a REAL loopback TLS handshake. This test
 * only asserts {@code SSLContext.init(...)} does not throw and produces a non-null context; it is
 * restored because Phase 23 D-13 mandates it, not as evidence of an HTTPS handshake.</li>
 * <li>{@link #authorizeUserSucceedsWithRealDigester()}: genuinely new value: the only test that
 * drives {@code DefaultUserController.authorizeUser(username, plainPassword, serverURL)}
 * end-to-end with a real BC-provider {@link Digester}. <b>Pre-declared JDK-25-red by
 * construction:</b> it opens three {@code mockStatic} scopes ({@code ControllerFactory},
 * {@code StatementLock}, {@code SqlConfig}) against the bundled mockito 5.1.1 / byte-buddy 1.14.13,
 * which cannot mock/instrument concrete classes under JDK 25 (IRT-1488, ON HOLD). JDK 25 is not a
 * completion gate for a BouncyCastle upgrade (Phase 23 D-29.3, Phase 26.15 D-12).</li>
 * <li>{@link #crossProviderHashStillVerifies()}: the highest-value assertion carried over from
 * Phase 23: it is the only one that answers whether existing customer password hashes (produced by
 * the JDK's SunJCE provider) still verify through a BC-wired {@link Digester}.</li>
 * <li>{@link #keystoreRoundTripSurvivesForJceks()} and {@link #keystoreRoundTripSurvivesForPkcs12()}
 * (1.86, D-03): the keystore.type resolution path (JCEKS, PKCS12) never resolves to BouncyCastle; a
 * certificate and a BC-generated secret key survive a store/load round trip through the same
 * no-provider {@code KeyStore.getInstance(type)} form Core uses.</li>
 * <li>{@link #bouncyCastleRuntimeIsAtLeast186()} (1.86): pins the runtime BouncyCastle provider
 * version, so a stale test classpath cannot report false confidence for the other new
 * assertions.</li>
 * <li>{@link #digesterAcceptsDefaultIterationsAndRejectsAboveBcCap()} (1.86, D-11): the
 * production-wired {@link Digester} path accepts the default {@code digest.iterations} and rejects
 * an iteration count above BouncyCastle 1.86's raw JCA PBKDF2 cap (CVE-2026-17508).</li>
 * </ul>
 */
public class Bc184UpgradeTest {

    private static final String CERT_ALIAS = "mirthconnect";

    // ------------------------------------------------------------------------------------------
    // Test 1 (restored): generated certificate carries the expected subject/issuer DN
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
     * {@code generateDefaultCertificate} method via reflection using the supplied fresh BC
     * {@link BouncyCastleProvider}, and returns the populated keystore. The provider is
     * constructed by each caller (never shared or JVM-registered) so the two suites stay
     * non-interfering under {@code forkmode="perTest"}. Delegates to the four-argument overload
     * with keystore type {@code "JCEKS"} so existing callers are unaffected.
     */
    private KeyStore generateCertificateIntoFreshKeystore(Provider provider, char[] storePassword, char[] keyPassword) throws Exception {
        return generateCertificateIntoFreshKeystore("JCEKS", provider, storePassword, keyPassword);
    }

    /**
     * As {@link #generateCertificateIntoFreshKeystore(Provider, char[], char[])}, but takes the
     * keystore type explicitly, using the same no-provider {@code KeyStore.getInstance(keystoreType)}
     * form {@code DefaultConfigurationController} and {@code MirthWebServer} use (1.86, D-03).
     */
    private KeyStore generateCertificateIntoFreshKeystore(String keystoreType, Provider provider, char[] storePassword, char[] keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keystoreType);
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
     * char[])} method. Unwraps {@link InvocationTargetException} so a real BouncyCastle API break
     * is readable in the JUnit XML as the underlying exception, not an opaque reflection wrapper --
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
    // Test 3 (restored): end-to-end authorizeUser succeeds with a real BC-provider Digester
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
            assertEquals("Real BouncyCastle-provider Digester should authenticate through the digester.matches branch", LoginStatus.Status.SUCCESS, result.getStatus());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Test 4 (restored, D-14 applied): a pre-upgrade SunJCE-produced hash still verifies under a
    // BC-wired Digester (the highest-value assertion carried over from Phase 23, T-23-12).
    // ------------------------------------------------------------------------------------------

    @Test
    public void crossProviderHashStillVerifies() throws Exception {
        // Practical stand-in for cross-version verification (a hash produced before this
        // upgrade): PBKDF2 output must be provider-independent, so a hash produced by the JDK's
        // SunJCE provider must still verify true against a Digester wired with the BC provider.
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

    // ------------------------------------------------------------------------------------------
    // Test 5 (1.86, D-03): keystore round-trip through the no-provider keystore.type path, JCEKS and PKCS12
    // ------------------------------------------------------------------------------------------

    /**
     * Builds a keystore of the given type through the same no-provider
     * {@code KeyStore.getInstance(keystoreType)} form {@code DefaultConfigurationController} and
     * {@code MirthWebServer} use, populates it with the real generated certificate plus a
     * BC-generated AES secret-key entry (mirroring {@code configureEncryption}), stores it to a
     * byte array, reloads a fresh keystore of the same type from those bytes, and asserts the
     * certificate, private key and secret key all survive the round trip and that the provider
     * serving the keystore is never BC (the D-04 premise this suite pins).
     */
    private void assertKeystoreRoundTrip(String keystoreType) throws Exception {
        char[] storePassword = ("bcUpgradeStore" + keystoreType).toCharArray();
        char[] keyPassword = ("bcUpgradeKey" + keystoreType).toCharArray();
        Provider provider = new BouncyCastleProvider();

        // Register BC as a JCA provider for the life of this assertion so the "never BC"
        // check below is falsifiable: KeyStore.getInstance(keystoreType) can only resolve
        // to BC if BC is actually registered. Core itself never calls Security.addProvider,
        // so this is scoped to the test and reverted in finally (server/build.xml's <junit>
        // runs forkmode="perTest", so there is no cross-test leakage either way).
        Security.addProvider(provider);
        try {
            KeyStore keyStore = generateCertificateIntoFreshKeystore(keystoreType, provider, storePassword, keyPassword);
            assertNotEquals("keystore.type (" + keystoreType + ") must resolve to a JDK provider, never to BouncyCastle", "BC", keyStore.getProvider().getName());

            EncryptionSettings settings = new EncryptionSettings(new Properties());
            KeyGenerator keyGenerator = KeyGenerator.getInstance(settings.getEncryptionBaseAlgorithm(), provider);
            keyGenerator.init(settings.getEncryptionKeyLength());
            SecretKey secretKey = keyGenerator.generateKey();
            keyStore.setEntry(DefaultConfigurationController.SECRET_KEY_ALIAS, new KeyStore.SecretKeyEntry(secretKey), new KeyStore.PasswordProtection(keyPassword));

            X509Certificate before = (X509Certificate) keyStore.getCertificate(CERT_ALIAS);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            keyStore.store(out, storePassword);

            KeyStore reloaded = KeyStore.getInstance(keystoreType);
            reloaded.load(new ByteArrayInputStream(out.toByteArray()), storePassword);

            X509Certificate after = (X509Certificate) reloaded.getCertificate(CERT_ALIAS);
            assertArrayEquals("(" + keystoreType + ") certificate encoding must survive the store/load round trip", before.getEncoded(), after.getEncoded());
            after.checkValidity();
            assertEquals("(" + keystoreType + ") reloaded certificate subject should be CN=mirth-connect", "CN=mirth-connect", after.getSubjectX500Principal().getName());
            assertEquals("(" + keystoreType + ") reloaded private key algorithm should be RSA", "RSA", reloaded.getKey(CERT_ALIAS, keyPassword).getAlgorithm());
            assertArrayEquals("(" + keystoreType + ") secret-key entry must survive the store/load round trip", secretKey.getEncoded(), reloaded.getKey(DefaultConfigurationController.SECRET_KEY_ALIAS, keyPassword).getEncoded());

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(reloaded, keyPassword);
            assertTrue("(" + keystoreType + ") KeyManagerFactory should produce at least one KeyManager from the reloaded store", keyManagerFactory.getKeyManagers().length > 0);
        } finally {
            Security.removeProvider("BC");
        }
    }

    @Test
    public void keystoreRoundTripSurvivesForJceks() throws Exception {
        assertKeystoreRoundTrip("JCEKS");
    }

    @Test
    public void keystoreRoundTripSurvivesForPkcs12() throws Exception {
        assertKeystoreRoundTrip("PKCS12");
    }

    // ------------------------------------------------------------------------------------------
    // Test 6 (1.86): the BouncyCastle runtime on the test classpath is at least 1.86
    // ------------------------------------------------------------------------------------------

    @Test
    public void bouncyCastleRuntimeIsAtLeast186() {
        // BouncyCastleProvider is constructed with the double-valued Provider constructor, so
        // getVersionStr() is Double.toString(version) and drops trailing zeros: 1.90 comes back
        // as "1.9", not "1.90". Splitting on "." and comparing the minor part as an integer would
        // then misparse 1.90 as minor 9 (less than 86) and fail a correct upgrade. Compare as a
        // decimal instead, so "1.9" > "1.86" the way the version numbers actually order.
        BigDecimal observed = new BigDecimal(new BouncyCastleProvider().getVersionStr().trim());
        assertTrue("BouncyCastle provider must be at least 1.86 (CVE-2026-8763, CVE-2026-13506, fixed from 1.85); this suite was last verified on 1.86; observed version: " + observed, observed.compareTo(new BigDecimal("1.86")) >= 0);
    }

    // ------------------------------------------------------------------------------------------
    // Test 7 (1.86, D-11): PBKDF2 iteration cap on the admin-login Digester path
    // ------------------------------------------------------------------------------------------

    /**
     * Wires a {@link Digester} exactly as {@code DefaultConfigurationController.configureEncryption}
     * does (without the fallback digester), driven by the supplied {@link EncryptionSettings}.
     */
    private Digester createProductionWiredBcDigester(EncryptionSettings settings) {
        Digester digester = new Digester();
        digester.setProvider(new BouncyCastleProvider());
        digester.setAlgorithm(settings.getDigestAlgorithm());
        digester.setSaltSizeBytes(settings.getDigestSaltSize());
        digester.setIterations(settings.getDigestIterations());
        digester.setUsePBE(settings.getDigestUsePBE());
        digester.setKeySizeBits(settings.getDigestKeySize());
        digester.setFormat(Output.BASE64);
        return digester;
    }

    @Test
    public void digesterAcceptsDefaultIterationsAndRejectsAboveBcCap() {
        // Accept half: default settings still hash and verify.
        EncryptionSettings defaultSettings = new EncryptionSettings(new Properties());
        assertEquals("default digest.iterations should be EncryptionSettings.DEFAULT_DIGEST_ITERATIONS", EncryptionSettings.DEFAULT_DIGEST_ITERATIONS, defaultSettings.getDigestIterations());

        Digester defaultDigester = createProductionWiredBcDigester(defaultSettings);
        String syntheticPassword = "bc186-pbkdf2-cap-check";
        String hash = defaultDigester.digest(syntheticPassword);
        assertNotNull("default-iteration digest should produce a hash", hash);
        assertTrue("default-iteration digest should verify against the same password", defaultDigester.matches(syntheticPassword, hash));

        // Reject half: BouncyCastle 1.86 caps raw JCA PBKDF2 at 10,000,000 iterations.
        Properties aboveCapProperties = new Properties();
        aboveCapProperties.setProperty("digest.iterations", "10000001");
        EncryptionSettings aboveCapSettings = new EncryptionSettings(aboveCapProperties);
        assertEquals("digest.iterations should read through as configured", Integer.valueOf(10000001), aboveCapSettings.getDigestIterations());

        Digester aboveCapDigester = createProductionWiredBcDigester(aboveCapSettings);
        EncryptionException thrown = assertThrows(EncryptionException.class, () -> aboveCapDigester.digest(syntheticPassword));

        Throwable cause = thrown.getCause();
        StringBuilder causeChain = new StringBuilder();
        boolean foundInvalidKeySpecException = false;
        while (cause != null) {
            causeChain.append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append("; ");
            if (cause instanceof InvalidKeySpecException) {
                foundInvalidKeySpecException = true;
            }
            cause = cause.getCause();
        }
        assertTrue("BouncyCastle 1.86 caps raw JCA PBKDF2 at 10,000,000 iterations (CVE-2026-17508, org.bouncycastle.pbe.max_iteration_count); expected an InvalidKeySpecException in the cause chain, observed: " + causeChain, foundInvalidKeySpecException);
    }
}
