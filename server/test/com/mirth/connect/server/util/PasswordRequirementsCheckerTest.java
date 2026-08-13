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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.Test;

import com.mirth.connect.model.PasswordRequirements;
import com.mirth.connect.server.controllers.DefaultUserController;

/**
 * Covers the password rules and settings added for IRT-1786 and IRT-1791. The older
 * PasswordRequirementsTest covers the character-class rules and is written against JUnit 3.
 */
public class PasswordRequirementsCheckerTest {

    /**
     * No composition rules at all, so "admin" can only be rejected by the explicit check.
     */
    private static PasswordRequirements noRules() {
        return new PasswordRequirements(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }

    private static List<String> check(String plainPassword) {
        return PasswordRequirementsChecker.getInstance().doesPasswordMeetRequirements(null, plainPassword, noRules());
    }

    @Test
    public void adminIsRejectedRegardlessOfCase() {
        for (String password : new String[] { "admin", "ADMIN", "AdMiN" }) {
            List<String> violations = check(password);

            assertNotNull("\"" + password + "\" should be rejected", violations);
            assertEquals(1, violations.size());
            assertEquals("\"admin\" is not allowed as a password", violations.get(0));
        }
    }

    /**
     * The rule is an exact match, not a substring one — rejecting every password containing "admin"
     * would be a different and much broader rule than the ticket asked for.
     */
    @Test
    public void passwordsMerelyContainingAdminAreAllowed() {
        assertNull(check("administrator"));
        assertNull(check("myadmin1"));
        assertNull(check("admin1"));
    }

    @Test
    public void unrelatedPasswordStillPasses() {
        assertNull(check("Th1$isAtestTEST*#"));
    }

    /**
     * "admin" also fails the shipped composition rules, so it must not be reported twice or mask
     * the other violations.
     */
    @Test
    public void adminIsReportedAlongsideCompositionFailures() {
        PasswordRequirements shipped = new PasswordRequirements(8, 1, 1, 1, 1, 5, 1, 0, 0, 0, 0, false);
        List<String> violations = PasswordRequirementsChecker.getInstance().doesPasswordMeetRequirements(null, "admin", shipped);

        assertNotNull(violations);
        assertTrue("Should name the disallowed password", violations.contains("\"admin\" is not allowed as a password"));
        assertTrue("Should still report the length failure", violations.contains("Password is too short. Minimum length is 8 characters"));
    }

    @Test
    public void loginEnforcementIsOnAndRestrictionIsOffByDefault() {
        PasswordRequirements requirements = PasswordRequirementsChecker.getInstance().loadPasswordRequirements(new PropertiesConfiguration());

        assertTrue("Stored passwords should be checked at login by default", requirements.isEnforceAtLogin());
        assertFalse("Restricting grace sessions is a breaking change, so it must be opt in", requirements.isRestrictGraceSessions());
    }

    @Test
    public void loginSettingsAreReadFromProperties() throws Exception {
        PropertiesConfiguration properties = new PropertiesConfiguration();
        properties.setProperty("password.enforceatlogin", "false");
        properties.setProperty("password.restrictgracesessions", "true");

        PasswordRequirements requirements = PasswordRequirementsChecker.getInstance().loadPasswordRequirements(properties);

        assertFalse(requirements.isEnforceAtLogin());
        assertTrue(requirements.isRestrictGraceSessions());
    }

    @Test
    public void requirementsMessageListsEveryViolation() {
        String message = DefaultUserController.buildRequirementsMessage(check("admin"));

        assertTrue(message.startsWith("Your password no longer meets the password requirements."));
        assertTrue(message.contains("\n - \"admin\" is not allowed as a password"));
    }
}
