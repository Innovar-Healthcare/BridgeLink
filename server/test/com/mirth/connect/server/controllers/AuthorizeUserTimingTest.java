/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.controllers;

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

import java.lang.reflect.Field;

import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.mirth.commons.encryption.Digester;
import com.mirth.commons.encryption.Output;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.Credentials;
import com.mirth.connect.model.EncryptionSettings;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.model.PasswordRequirements;
import com.mirth.connect.model.User;
import com.mirth.connect.plugins.AuthorizationPlugin;
import com.mirth.connect.server.util.SqlConfig;
import com.mirth.connect.server.util.StatementLock;
import com.mirth.connect.test.SlowTest;

/**
 * Regression test for issue #125 (SEC-04): username enumeration via timing (CWE-208)
 * and message (CWE-204).
 *
 * <p>Verifies three behaviours required by the locked plan:
 * <ol>
 *   <li>{@code DUMMY_HASH} is a valid PBKDF2 base64 hash — calling
 *       {@code digester.matches(plain, DUMMY_HASH)} returns {@code false} without
 *       throwing. Guards against pitfall: malformed base64 / wrong salt size
 *       causes 500 instead of 401.</li>
 *   <li>The fail message for known-bad-password and unknown-user paths is byte-equal
 *       to the canonical {@code INCORRECT_CREDENTIALS_MESSAGE}.</li>
 *   <li>The wall-clock mean delta between known-username login and unknown-username
 *       login is {@code <= 20ms} across 50 samples per side. Discards the first 5
 *       samples per side as JIT warmup.</li>
 * </ol>
 *
 * <p>The timing-parity test bears {@code @Category(SlowTest.class)} because the
 * full 50-sample run performs ~100 PBKDF2-at-600000-iter operations and runs
 * in ~10s on a typical CI runner.
 */
public class AuthorizeUserTimingTest {

    private static final Logger logger = LogManager.getLogger(AuthorizeUserTimingTest.class);
    private static final String INCORRECT_CREDENTIALS_MESSAGE = "Incorrect username or password.";
    private static final String CORRECT_PASSWORD = "correctpassword";
    private static final String WRONG_PASSWORD = "wrongpassword";
    private static final String VALID_USERNAME = "validuser";
    private static final String UNKNOWN_USERNAME = "nonexistentuser";

    private Digester productionLikeDigester;
    private String hashedCorrectPassword;

    @Before
    public void setUp() throws Exception {
        // Build a Digester configured identically to production:
        //   PBKDF2WithHmacSHA256, 600000 iter, BouncyCastle, PBE, 256-bit key,
        //   8-byte salt, BASE64 output. Matches DigesterTest#createDigester.
        productionLikeDigester = new Digester();
        productionLikeDigester.setProvider(new BouncyCastleProvider());
        productionLikeDigester.setAlgorithm("PBKDF2WithHmacSHA256");
        productionLikeDigester.setIterations(EncryptionSettings.DEFAULT_DIGEST_ITERATIONS);
        productionLikeDigester.setUsePBE(true);
        productionLikeDigester.setKeySizeBits(256);
        productionLikeDigester.setSaltSizeBytes(8);
        productionLikeDigester.setFormat(Output.BASE64);

        // Pre-compute the hash of the "correct" password the validuser path will
        // succeed against. Done once in @Before so the test itself does not pay
        // the 100ms PBKDF2 cost on every sample.
        hashedCorrectPassword = productionLikeDigester.digest(CORRECT_PASSWORD);
        assertNotNull("Digester.digest must return a non-null hash for setup", hashedCorrectPassword);
    }

    /**
     * Reflective access to the private static {@code DUMMY_HASH} constant on
     * {@link DefaultUserController}. We do NOT add a public accessor to
     * production code; reflection keeps the constant private.
     */
    private static String readDummyHashConstant() throws Exception {
        Field f = DefaultUserController.class.getDeclaredField("DUMMY_HASH");
        f.setAccessible(true);
        return (String) f.get(null);
    }

    // ===== Test 1: DUMMY_HASH safety =====

    /**
     * The DUMMY_HASH constant must be a base64-encoded byte array of length
     * (saltSizeBytes + derived-key-bytes). If the constant is malformed,
     * {@code digester.matches(...)} throws {@code EncryptionException} wrapping
     * {@code ArrayIndexOutOfBoundsException} (see Digester.matches → doMatches
     * → Base64.decodeBase64). That would make every unknown-user login a 500
     * instead of a 401. This test guards against that pitfall.
     */
    @Test
    public void dummyHashIsConsumableWithoutThrowing() throws Exception {
        String dummyHash = readDummyHashConstant();
        assertNotNull("DUMMY_HASH must be declared on DefaultUserController", dummyHash);
        assertFalse("DUMMY_HASH must not be empty", dummyHash.isEmpty());

        // Must return false (random plaintext does not match the dummy hash)
        // and must NOT throw.
        boolean matched = productionLikeDigester.matches("any-plaintext-attacker-might-submit", dummyHash);
        assertFalse("digester.matches with random plaintext against DUMMY_HASH must return false", matched);
    }

    // ===== Test 2: message identity (CWE-204) =====

    /**
     * The error message returned for a known username with a wrong password must
     * be byte-equal to the message returned for an unknown username. Both must
     * be exactly {@code INCORRECT_CREDENTIALS_MESSAGE}.
     */
    @Test
    public void messageIdenticalForKnownBadPasswordAndUnknownUser() throws Exception {
        DefaultUserController controller = newControllerWithMockedDeps();

        String knownMessage = invokeAuthorizeAndGetMessage(controller, VALID_USERNAME, WRONG_PASSWORD);
        String unknownMessage = invokeAuthorizeAndGetMessage(controller, UNKNOWN_USERNAME, WRONG_PASSWORD);

        assertEquals("Known-bad-password message must be the canonical generic message",
                INCORRECT_CREDENTIALS_MESSAGE, knownMessage);
        assertEquals("Unknown-user message must be the canonical generic message",
                INCORRECT_CREDENTIALS_MESSAGE, unknownMessage);
        assertEquals("Known-bad-password and unknown-user messages must be byte-equal",
                knownMessage, unknownMessage);
    }

    // ===== Test 3: timing parity (CWE-208) =====

    /**
     * Measures wall-clock {@code authorizeUser} latency for 50 samples each of
     * known and unknown usernames, discards the first 5 per side as JIT warmup,
     * and asserts the absolute mean delta is at most 20ms. This number matches
     * ROADMAP Success Criterion 4 verbatim and CONTEXT.md §#125.
     *
     * <p>Marked {@code @Category(SlowTest.class)} — full run is ~10s.
     */
    @Test
    @Category(SlowTest.class)
    public void meanResponseDeltaUnder20msOver50Samples() throws Exception {
        DefaultUserController controller = newControllerWithMockedDeps();

        final int samples = 50;
        final int warmupDiscard = 5;
        long sumKnownNanos = 0L;
        long sumUnknownNanos = 0L;

        for (int i = 0; i < samples; i++) {
            long known = timedAuthorizeNanos(controller, VALID_USERNAME, WRONG_PASSWORD);
            long unknown = timedAuthorizeNanos(controller, UNKNOWN_USERNAME, WRONG_PASSWORD);
            if (i >= warmupDiscard) {
                sumKnownNanos += known;
                sumUnknownNanos += unknown;
            }
        }

        long effective = samples - warmupDiscard;
        double meanKnownMs = (sumKnownNanos / (double) effective) / 1_000_000.0;
        double meanUnknownMs = (sumUnknownNanos / (double) effective) / 1_000_000.0;
        double deltaMs = Math.abs(meanKnownMs - meanUnknownMs);

        logger.info(String.format(
                "AuthorizeUserTimingTest samples=%d (effective=%d) meanKnown=%.2fms meanUnknown=%.2fms delta=%.2fms",
                samples, effective, meanKnownMs, meanUnknownMs, deltaMs));

        assertTrue(String.format(
                "Mean response-time delta must be <= 20ms (was %.2fms; mean known=%.2fms, mean unknown=%.2fms)",
                deltaMs, meanKnownMs, meanUnknownMs),
                deltaMs <= 20.0);
    }

    // ===== Test plumbing =====

    private long timedAuthorizeNanos(DefaultUserController controller, String user, String pw) {
        long start = System.nanoTime();
        try {
            controller.authorizeUser(user, pw, "http://localhost/test");
        } catch (Exception e) {
            // Treat ControllerException as a timing sample too — production
            // would still consume the same PBKDF2 work in the error path.
        }
        return System.nanoTime() - start;
    }

    private String invokeAuthorizeAndGetMessage(DefaultUserController controller, String user, String pw)
            throws ControllerException {
        LoginStatus status = controller.authorizeUser(user, pw, "http://localhost/test");
        assertNotNull("authorizeUser must return a LoginStatus", status);
        return status.getMessage();
    }

    /**
     * Builds a DefaultUserController whose required collaborators (SqlConfig,
     * StatementLock, ControllerFactory) are stubbed to drive the validuser /
     * unknownuser paths deterministically. The stubs are installed in a
     * try-with-resources elsewhere; here we wire them onto the current call
     * scope by entering a mockStatic block that lives for the duration of the
     * surrounding @Test method via the controller instance retaining no
     * static-mock references.
     *
     * <p>Implementation note: each @Test that calls this helper installs the
     * mockStatic blocks itself; this helper just builds the controller and
     * arranges per-test stubs. See {@link #installMocks()}.
     */
    private DefaultUserController newControllerWithMockedDeps() throws Exception {
        // Install the static mocks. The mocks live as instance fields so the
        // surrounding test method's lifecycle keeps them open until the
        // @After teardown (implicitly, when the @Test method returns and the
        // controller goes out of scope). For simplicity we install them once
        // per test via this builder.
        installMocks();
        return new DefaultUserController();
    }

    // mockStatic handles must remain open for the duration of the @Test —
    // we keep them as instance fields and close them via try-with-resources
    // inside each @Test would be ideal; for brevity here we leak them
    // intentionally and rely on Mockito's test-isolation cleanup.
    private org.mockito.MockedStatic<SqlConfig> sqlConfigStatic;
    private org.mockito.MockedStatic<StatementLock> statementLockStatic;
    private org.mockito.MockedStatic<ControllerFactory> controllerFactoryStatic;

    @org.junit.After
    public void tearDown() {
        if (sqlConfigStatic != null) sqlConfigStatic.close();
        if (statementLockStatic != null) statementLockStatic.close();
        if (controllerFactoryStatic != null) controllerFactoryStatic.close();
    }

    private void installMocks() throws Exception {
        // StatementLock — readLock/readUnlock are no-ops in tests.
        StatementLock mockLock = mock(StatementLock.class);
        statementLockStatic = mockStatic(StatementLock.class);
        statementLockStatic.when(() -> StatementLock.getInstance(anyString())).thenReturn(mockLock);

        // SqlConfig — selectOne dispatched on statement name.
        SqlConfig mockSqlConfig = mock(SqlConfig.class);
        SqlSessionManager mockSessionManager = mock(SqlSessionManager.class);
        when(mockSqlConfig.getReadOnlySqlSessionManager()).thenReturn(mockSessionManager);
        when(mockSqlConfig.getSqlSessionManager()).thenReturn(mockSessionManager);

        // User.getUser → known user for VALID_USERNAME, null for UNKNOWN_USERNAME.
        when(mockSessionManager.selectOne(eq("User.getUser"), any())).thenAnswer(inv -> {
            Object param = inv.getArgument(1);
            if (param instanceof User) {
                String requested = ((User) param).getUsername();
                if (VALID_USERNAME.equals(requested)) {
                    User u = new User();
                    u.setId(42);
                    u.setUsername(VALID_USERNAME);
                    return u;
                }
            }
            return null;
        });

        // User.getLatestUserCredentials → Credentials with the real PBKDF2 hash
        // of CORRECT_PASSWORD for the known user.
        Credentials creds = new Credentials();
        creds.setPassword(hashedCorrectPassword);
        when(mockSessionManager.selectOne(eq("User.getLatestUserCredentials"), any())).thenReturn(creds);

        sqlConfigStatic = mockStatic(SqlConfig.class);
        sqlConfigStatic.when(SqlConfig::getInstance).thenReturn(mockSqlConfig);

        // ControllerFactory — provides ExtensionController (auth plugin null)
        // and ConfigurationController (digester + password requirements).
        ConfigurationController mockConfigController = mock(ConfigurationController.class);
        when(mockConfigController.getDigester()).thenReturn(productionLikeDigester);
        PasswordRequirements pr = new PasswordRequirements();
        // Force allowUsernameEnumeration=false so we exercise the safe error path.
        pr.setAllowUsernameEnumeration(false);
        // Bypass the strike/lockout path so the wrong-password branch does NOT
        // invoke LoginRequirementsChecker.incrementStrikes(); combined with the
        // UserController stub below, this isolates the test from the hardened
        // PasswordRequirements defaults (retryLimit=5, lockoutPeriod=5) added
        // by issue #125. See CR-01 in 01-REVIEW.md.
        pr.setRetryLimit(0);
        pr.setLockoutPeriod(0);
        when(mockConfigController.getPasswordRequirements()).thenReturn(pr);

        ExtensionController mockExtController = mock(ExtensionController.class);
        when(mockExtController.getAuthorizationPlugin()).thenReturn((AuthorizationPlugin) null);

        // UserController stub — required so LoginRequirementsChecker's
        // constructor (which calls ControllerFactory.getFactory().createUserController())
        // does not return null and subsequently NPE in any code path that
        // dereferences userController. Belt-and-suspenders alongside
        // retryLimit=0 above.
        UserController mockUserController = mock(UserController.class);

        ControllerFactory mockFactory = mock(ControllerFactory.class);
        when(mockFactory.createConfigurationController()).thenReturn(mockConfigController);
        when(mockFactory.createExtensionController()).thenReturn(mockExtController);
        when(mockFactory.createUserController()).thenReturn(mockUserController);

        controllerFactoryStatic = mockStatic(ControllerFactory.class);
        controllerFactoryStatic.when(ControllerFactory::getFactory).thenReturn(mockFactory);
    }
}
