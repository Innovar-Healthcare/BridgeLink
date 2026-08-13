/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.api;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.client.core.Operation;
import com.mirth.connect.client.core.Operation.ExecuteType;
import com.mirth.connect.model.PasswordRequirements;

/**
 * Covers the restriction placed on a login that is serving out a password grace period. See
 * IRT-1791.
 */
public class MirthServletGracePeriodTest extends ServletTestBase {

    private static final Operation NORMAL_OPERATION = new Operation("getChannels", "Get channels", ExecuteType.SYNC, true);
    private static final Operation PASSWORD_OPERATION = new Operation("updateUserPassword", "Update a user's password", ExecuteType.SYNC, true);

    @BeforeClass
    public static void setup() throws Exception {
        ServletTestBase.setup();
    }

    @After
    public void tearDown() {
        // The session and controller mocks are shared across the class
        when(session.getAttribute("graceRestricted")).thenReturn(null);
        when(configurationController.getPasswordRequirements()).thenReturn(null);
    }

    private void givenGraceRestrictedSession(boolean restrictionEnabled) {
        when(session.getAttribute("graceRestricted")).thenReturn(Boolean.TRUE);

        PasswordRequirements passwordRequirements = new PasswordRequirements();
        passwordRequirements.setRestrictGraceSessions(restrictionEnabled);
        when(configurationController.getPasswordRequirements()).thenReturn(passwordRequirements);
    }

    private MirthServlet newServlet() {
        return new MirthServlet(request, sc, controllerFactory) {
        };
    }

    /**
     * The restriction ships off, so a grace period must not change what a login can do until an
     * operator opts in.
     */
    @Test
    public void restrictionDisabledLeavesGraceSessionAlone() throws Exception {
        givenGraceRestrictedSession(false);

        MirthServlet servlet = newServlet();
        servlet.setOperation(NORMAL_OPERATION);

        assertTrue(servlet.isUserAuthorized(false));
    }

    @Test
    public void restrictedSessionIsDeniedOrdinaryOperations() throws Exception {
        givenGraceRestrictedSession(true);

        MirthServlet servlet = newServlet();
        servlet.setOperation(NORMAL_OPERATION);

        try {
            servlet.isUserAuthorized(false);
            fail("Expected a grace-restricted session to be refused");
        } catch (Throwable t) {
            assertForbiddenException(t);
        }
    }

    /**
     * The whole point of the restriction is that the user can still clear it.
     */
    @Test
    public void restrictedSessionMayStillChangeItsPassword() throws Exception {
        givenGraceRestrictedSession(true);

        MirthServlet servlet = newServlet();
        servlet.setOperation(PASSWORD_OPERATION);

        assertTrue(servlet.isUserAuthorized(false));
    }

    /**
     * getCurrentUser carries no self-scoping annotation, so it is refused unless it is allowed
     * explicitly — and without it a client cannot learn which user it is changing the password for.
     */
    @Test
    public void restrictedSessionMayStillIdentifyItself() throws Exception {
        givenGraceRestrictedSession(true);

        MirthServlet servlet = newServlet();
        servlet.setOperation(new Operation("getCurrentUser", "Get current user", ExecuteType.SYNC, false));

        assertTrue(servlet.isUserAuthorized(false));
    }

    /**
     * Extension endpoints authorize through a separate method, so they need the same check.
     */
    @Test
    public void restrictedSessionIsDeniedExtensionOperations() throws Exception {
        givenGraceRestrictedSession(true);

        MirthServlet servlet = newServlet();
        servlet.setOperation(NORMAL_OPERATION);

        try {
            servlet.isUserAuthorizedForExtension("someextension", false);
            fail("Expected a grace-restricted session to be refused for an extension operation");
        } catch (Throwable t) {
            assertForbiddenException(t);
        }
    }

    @Test
    public void unrestrictedSessionIsUnaffected() throws Exception {
        when(session.getAttribute("graceRestricted")).thenReturn(null);

        PasswordRequirements passwordRequirements = new PasswordRequirements();
        passwordRequirements.setRestrictGraceSessions(true);
        when(configurationController.getPasswordRequirements()).thenReturn(passwordRequirements);

        MirthServlet servlet = newServlet();
        servlet.setOperation(NORMAL_OPERATION);

        assertTrue(servlet.isUserAuthorized(false));
    }

    /**
     * A password that meets requirements must not be treated as restricted just because the
     * requirements object says restriction is available.
     */
    @Test
    public void restrictionRequiresBothTheFlagAndTheSession() throws Exception {
        when(session.getAttribute("graceRestricted")).thenReturn(Boolean.FALSE);

        PasswordRequirements passwordRequirements = new PasswordRequirements();
        passwordRequirements.setRestrictGraceSessions(true);
        when(configurationController.getPasswordRequirements()).thenReturn(passwordRequirements);

        MirthServlet servlet = newServlet();
        servlet.setOperation(NORMAL_OPERATION);

        assertTrue(servlet.isUserAuthorized(false));
    }
}
