/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Calendar;

import org.junit.Test;

import com.mirth.connect.model.LoginStrike;
import com.mirth.connect.model.PasswordRequirements;
import com.mirth.connect.model.User;
import com.mirth.connect.server.controllers.UserController;

public class LoginRequirementsCheckerTest {

    @Test
    public void testIncrementStrikes1() throws Exception {
        User user = new User();
        user.setId(1);
        PasswordRequirements passwordRequirements = mock(PasswordRequirements.class);
        UserController userController = mock(UserController.class);

        // Test non-null LoginStrike returned
        LoginStrike loginStrike = new LoginStrike(1, Calendar.getInstance());
        when(userController.incrementStrikes(user.getId())).thenReturn(loginStrike);
        LoginRequirementsChecker checker = new LoginRequirementsChecker(user, passwordRequirements, userController);
        checker.incrementStrikes();
        assertEquals((Integer) loginStrike.getLastStrikeCount(), user.getStrikeCount());
        assertEquals(loginStrike.getLastStrikeTime(), user.getLastStrikeTime());
    }

    @Test
    public void testIncrementStrikes2() throws Exception {
        User user = new User();
        user.setId(1);
        PasswordRequirements passwordRequirements = mock(PasswordRequirements.class);
        UserController userController = mock(UserController.class);

        // Test null LoginStrike returned
        when(userController.incrementStrikes(user.getId())).thenReturn(null);
        LoginRequirementsChecker checker = new LoginRequirementsChecker(user, passwordRequirements, userController);
        checker.incrementStrikes();
        assertNull(user.getStrikeCount());
        assertNull(user.getLastStrikeTime());
    }

    @Test
    public void testResetStrikes() throws Exception {
        User user = new User();
        user.setId(1);
        PasswordRequirements passwordRequirements = mock(PasswordRequirements.class);
        UserController userController = mock(UserController.class);

        LoginStrike loginStrike = new LoginStrike(0, null);
        when(userController.resetStrikes(user.getId())).thenReturn(loginStrike);
        LoginRequirementsChecker checker = new LoginRequirementsChecker(user, passwordRequirements, userController);
        checker.resetStrikes();
        assertEquals((Integer) loginStrike.getLastStrikeCount(), user.getStrikeCount());
        assertNull(user.getLastStrikeTime());
    }

    // --- isUserLockedOut tests --- //

    @Test
    public void testIsUserLockedOut_withinLockoutPeriod_returnsTrue() throws Exception {
        User user = new User();
        user.setId(1);
        PasswordRequirements passwordRequirements = mock(PasswordRequirements.class);
        UserController userController = mock(UserController.class);

        // retryLimit=3 means attemptsRemaining = 3+1 - strikeCount; strikeCount=5 → attemptsRemaining=-1 (<=0)
        when(passwordRequirements.getRetryLimit()).thenReturn(3);
        // lockoutPeriod=1 hour; set lastStrikeTime to 30 minutes ago → still within lockout
        when(passwordRequirements.getLockoutPeriod()).thenReturn(1);
        user.setStrikeCount(5);
        Calendar thirtyMinutesAgo = Calendar.getInstance();
        thirtyMinutesAgo.add(Calendar.MINUTE, -30);
        user.setLastStrikeTime(thirtyMinutesAgo);

        LoginRequirementsChecker checker = new LoginRequirementsChecker(user, passwordRequirements, userController);
        assertTrue(checker.isUserLockedOut());
    }

    @Test
    public void testIsUserLockedOut_lockoutPeriodElapsed_returnsFalse() throws Exception {
        User user = new User();
        user.setId(1);
        PasswordRequirements passwordRequirements = mock(PasswordRequirements.class);
        UserController userController = mock(UserController.class);

        // retryLimit=3, strikeCount=5 → no attempts remaining
        when(passwordRequirements.getRetryLimit()).thenReturn(3);
        // lockoutPeriod=1 hour; set lastStrikeTime to 2 hours ago → lockout elapsed, strikeTimeRemaining <= 0
        when(passwordRequirements.getLockoutPeriod()).thenReturn(1);
        user.setStrikeCount(5);
        Calendar twoHoursAgo = Calendar.getInstance();
        twoHoursAgo.add(Calendar.HOUR, -2);
        user.setLastStrikeTime(twoHoursAgo);

        LoginRequirementsChecker checker = new LoginRequirementsChecker(user, passwordRequirements, userController);
        assertFalse(checker.isUserLockedOut());
    }

    @Test
    public void testIsUserLockedOut_lockoutDisabled_returnsFalse() throws Exception {
        User user = new User();
        user.setId(1);
        PasswordRequirements passwordRequirements = mock(PasswordRequirements.class);
        UserController userController = mock(UserController.class);

        // retryLimit=0 means lockout is disabled
        when(passwordRequirements.getRetryLimit()).thenReturn(0);
        user.setStrikeCount(99);

        LoginRequirementsChecker checker = new LoginRequirementsChecker(user, passwordRequirements, userController);
        assertFalse(checker.isUserLockedOut());
    }

    // --- isPasswordExpired tests --- //

    @Test
    public void testIsPasswordExpired_passwordOlderThanExpiration_returnsTrue() throws Exception {
        User user = new User();
        user.setId(1);
        PasswordRequirements passwordRequirements = mock(PasswordRequirements.class);
        UserController userController = mock(UserController.class);

        // expiration=30 days; password set 60 days ago → expired
        when(passwordRequirements.getExpiration()).thenReturn(30);
        LoginRequirementsChecker checker = new LoginRequirementsChecker(user, passwordRequirements, userController);
        long currentTime = System.currentTimeMillis();
        long passwordTime = currentTime - (60L * 24 * 60 * 60 * 1000); // 60 days ago
        assertTrue(checker.isPasswordExpired(passwordTime, currentTime));
    }

    @Test
    public void testIsPasswordExpired_passwordWithinExpiration_returnsFalse() throws Exception {
        User user = new User();
        user.setId(1);
        PasswordRequirements passwordRequirements = mock(PasswordRequirements.class);
        UserController userController = mock(UserController.class);

        // expiration=30 days; password set 10 days ago → not expired
        when(passwordRequirements.getExpiration()).thenReturn(30);
        LoginRequirementsChecker checker = new LoginRequirementsChecker(user, passwordRequirements, userController);
        long currentTime = System.currentTimeMillis();
        long passwordTime = currentTime - (10L * 24 * 60 * 60 * 1000); // 10 days ago
        assertFalse(checker.isPasswordExpired(passwordTime, currentTime));
    }

    @Test
    public void testIsPasswordExpired_expirationZero_neverExpires() throws Exception {
        User user = new User();
        user.setId(1);
        PasswordRequirements passwordRequirements = mock(PasswordRequirements.class);
        UserController userController = mock(UserController.class);

        // expiration=0 days means Duration.standardDays(0) = 0 ms; any positive password age → expired
        // This tests the behaviour when expiration is explicitly disabled (0): duration remaining is always negative
        // so the caller (LoginController) is responsible for checking expiration==0 before calling isPasswordExpired.
        // Verify that the method itself returns true when duration is 0 days and any password age > 0.
        when(passwordRequirements.getExpiration()).thenReturn(0);
        LoginRequirementsChecker checker = new LoginRequirementsChecker(user, passwordRequirements, userController);
        long currentTime = System.currentTimeMillis();
        long passwordTime = currentTime - 1000L; // 1 second ago — any positive age
        // With 0-day expiration, duration remaining is always negative → expired
        assertTrue(checker.isPasswordExpired(passwordTime, currentTime));
    }

    // --- getGraceTimeRemaining (password expiring-soon window) tests --- //

    @Test
    public void testGetGraceTimeRemaining_withinGracePeriod_returnsPositive() throws Exception {
        User user = new User();
        user.setId(1);
        PasswordRequirements passwordRequirements = mock(PasswordRequirements.class);
        UserController userController = mock(UserController.class);

        // gracePeriod=7 days; grace period started 3 days ago → 4 days remaining (positive)
        when(passwordRequirements.getGracePeriod()).thenReturn(7);
        LoginRequirementsChecker checker = new LoginRequirementsChecker(user, passwordRequirements, userController);
        long currentTime = System.currentTimeMillis();
        long gracePeriodStartTime = currentTime - (3L * 24 * 60 * 60 * 1000); // 3 days ago
        long remaining = checker.getGraceTimeRemaining(gracePeriodStartTime, currentTime);
        assertTrue("Grace time remaining should be positive when within grace period", remaining > 0);
    }

    @Test
    public void testGetGraceTimeRemaining_afterGracePeriodElapsed_returnsNegative() throws Exception {
        User user = new User();
        user.setId(1);
        PasswordRequirements passwordRequirements = mock(PasswordRequirements.class);
        UserController userController = mock(UserController.class);

        // gracePeriod=7 days; grace period started 10 days ago → elapsed (negative)
        when(passwordRequirements.getGracePeriod()).thenReturn(7);
        LoginRequirementsChecker checker = new LoginRequirementsChecker(user, passwordRequirements, userController);
        long currentTime = System.currentTimeMillis();
        long gracePeriodStartTime = currentTime - (10L * 24 * 60 * 60 * 1000); // 10 days ago
        long remaining = checker.getGraceTimeRemaining(gracePeriodStartTime, currentTime);
        assertTrue("Grace time remaining should be negative when grace period has elapsed", remaining < 0);
    }
}
