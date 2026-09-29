/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.client.core.api.servlets.UserServletInterface;
import com.mirth.connect.server.api.servlets.UserServlet;

/**
 * What reaches the parameter map reaches the EVENT table: AuthorizationController copies it
 * verbatim onto the audited ServerEvent. Param marks a parameter excludeFromAudit precisely so a
 * plaintext password never makes that trip.
 * <p>
 * The exclusion only applies to parameters the handler recognises. When it cannot read Param it
 * gives every argument a generated "argN" name instead, and a generated name is not null, so the
 * password is audited like any other value. That is what happened between the Jersey 2.48 upgrade
 * and IRT-1798: the annotations were being read off the implementation method, which declares
 * none.
 */
public class ServletParameterMapTest extends ServletTestBase {

    private static final String PLAINTEXT_PASSWORD = "N3verAuditThis!";

    @BeforeClass
    public static void setup() throws Exception {
        ServletTestBase.setup();
    }

    /**
     * Runs against both method objects Jersey could hand the handler, because the one it passes
     * has changed with a Jersey upgrade before.
     */
    @Test
    public void plaintextPasswordNeverReachesTheAuditedParameterMap() throws Throwable {
        for (Method method : new Method[] {
                UserServlet.class.getMethod("updateUserPassword", Integer.class, String.class),
                UserServletInterface.class.getMethod("updateUserPassword", Integer.class, String.class) }) {
            UserServlet servlet = new UserServlet(request, sc, controllerFactory);

            ih.invoke(servlet, method, new Object[] { 1, PLAINTEXT_PASSWORD });

            assertFalse("The password must not be audited, whichever method object Jersey passes (" + method.getDeclaringClass().getSimpleName() + ")", servlet.parameterMap.containsValue(PLAINTEXT_PASSWORD));
            assertEquals("The target user must be audited under its real name, not a generated one (" + method.getDeclaringClass().getSimpleName() + ")", 1, servlet.parameterMap.get("userId"));
            assertTrue("A generated \"argN\" name means Param was not read at all (" + method.getDeclaringClass().getSimpleName() + ")", servlet.parameterMap.keySet().stream().noneMatch(name -> name.startsWith("arg")));
        }
    }
}
