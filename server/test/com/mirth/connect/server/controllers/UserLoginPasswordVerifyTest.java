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
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.security.Provider;
import java.security.Security;
import java.util.Calendar;

import org.apache.ibatis.session.SqlSessionManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;

import com.mirth.commons.encryption.Digester;
import com.mirth.commons.encryption.Output;
import com.mirth.connect.model.Credentials;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.model.PasswordRequirements;
import com.mirth.connect.model.User;
import com.mirth.connect.server.util.SqlConfig;
import com.mirth.connect.server.util.StatementLock;

/**
 * BC GAP 2 regression (10-IMPACT-ANALYSIS.md &sect;4): proves that admin login password-verify
 * succeeds through a REAL {@link Digester} wired with the BC 1.84 {@link BouncyCastleProvider}
 * inside {@link DefaultUserController}.
 * <p>
 * The existing {@code DefaultUserControllerTest#testCheckPassword} deliberately asserts
 * {@code NullPointerException} because the real {@code Digester} is never wired in that test
 * ("can't easily mock the Digester dependency"). This class supersedes that gap: the
 * {@link ControllerFactory} static factory is mocked so that
 * {@code ControllerFactory.getFactory().createConfigurationController().getDigester()} returns a
 * REAL {@code Digester} instance, configured the same way production does in
 * {@code DefaultConfigurationController.configureEncryption} (BC provider, BASE64 output, class
 * defaults for PBKDF2WithHmacSHA256/600000 iterations/256-bit key/8-byte salt). No test in this
 * class asserts {@code NullPointerException} as "coverage" for the login path.
 */
public class UserLoginPasswordVerifyTest {

    private Digester createRealBcDigester() {
        Digester digester = new Digester();
        digester.setProvider(new BouncyCastleProvider());
        digester.setFormat(Output.BASE64);
        return digester;
    }

    @Test
    public void checkPasswordSucceedsWithRealDigester() {
        try (var mockedControllerFactory = mockStatic(ControllerFactory.class)) {
            ControllerFactory mockFactory = mock(ControllerFactory.class);
            ConfigurationController mockConfigController = mock(ConfigurationController.class);
            Digester digester = createRealBcDigester();

            mockedControllerFactory.when(ControllerFactory::getFactory).thenReturn(mockFactory);
            when(mockFactory.createConfigurationController()).thenReturn(mockConfigController);
            when(mockConfigController.getDigester()).thenReturn(digester);

            DefaultUserController userController = new DefaultUserController();

            String plainPassword = "correct-horse-battery-staple";
            String wrongPassword = "incorrect-horse-battery-staple";
            String hash = digester.digest(plainPassword);

            assertTrue("checkPassword should return true for the matching plaintext/hash pair", userController.checkPassword(plainPassword, hash));
            assertFalse("checkPassword should return false for a mismatched plaintext", userController.checkPassword(wrongPassword, hash));
        }
    }

    @Test
    public void authorizeUserSucceedsWithRealDigester() throws Exception {
        String username = "bcTestAdmin";
        String plainPassword = "bc-1.84-real-digester-pw";
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

    @Test
    public void crossProviderHashStillVerifies() throws Exception {
        // Practical stand-in for cross-version verification (pre-1.84-produced hash): PBKDF2
        // output must be provider-independent, so a hash produced by the JDK's SunJCE provider
        // must still verify true against a Digester wired with the BC 1.84 provider.
        Provider sunJceProvider = Security.getProvider("SunJCE");
        assumeTrue("SunJCE provider must be available on the build JDK for this cross-provider check; "
                + "cross-version login verify falls back to the plan 10-03 migration harness (logs in against a pre-upgrade-era hash) if this is skipped.", sunJceProvider != null);

        Digester sunJceDigester = new Digester();
        sunJceDigester.setProvider(sunJceProvider);
        sunJceDigester.setFormat(Output.BASE64);

        Digester bcDigester = createRealBcDigester();

        String plainPassword = "cross-provider-pbkdf2-check";
        String sunJceHash = sunJceDigester.digest(plainPassword);

        assertTrue("BC-provider Digester should verify a hash produced by the SunJCE provider (PBKDF2WithHmacSHA256 output is provider-independent)", bcDigester.matches(plainPassword, sunJceHash));
    }
}
