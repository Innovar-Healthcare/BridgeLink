/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.api.providers;

import static org.junit.Assert.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.reflections.Reflections;

import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.Param;
import com.mirth.connect.server.api.CheckAuthorizedChannelId;
import com.mirth.connect.server.api.CheckAuthorizedUserId;
import com.mirth.connect.server.api.MirthServlet;

/**
 * The contract that lets MirthResourceInvocationHandlerProvider scope an authorization check to a
 * particular user or channel: the annotation names a parameter, and the handler finds that
 * parameter by matching a {@link Param} name. If the name does not resolve, the handler falls back
 * to the unscoped checkUserAuthorized() overload and the scoping silently does not happen -- a
 * redacted channel becomes reachable, and a grace-restricted user is refused their own password
 * change (IRT-1798).
 * <p>
 * This runs across every servlet, core and plugin, rather than the ones with their own regression
 * tests, because the failure is invisible: nothing throws, nothing logs, and the endpoint keeps
 * working for everyone the restriction was not about to stop.
 */
public class ServletAuthorizationParamContractTest {

    /**
     * Param is declared only on the interfaces. That is the assumption the handler's resolution
     * rests on, so pin it: an implementation that started declaring its own Param would make the
     * two declarations disagree with nothing to catch it.
     */
    @Test
    public void servletImplementationsDeclareNoParamAnnotations() {
        List<String> offenders = new ArrayList<String>();

        for (Class<? extends MirthServlet> servlet : servlets()) {
            for (Method method : servlet.getDeclaredMethods()) {
                if (!paramNames(method).isEmpty()) {
                    offenders.add(servlet.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertTrue("Param belongs on the servlet interface, not the implementation: " + offenders, offenders.isEmpty());
    }

    /**
     * Every user- or channel-scoped operation must name a parameter the handler can actually find.
     */
    @Test
    public void everyScopedOperationNamesAResolvableParameter() {
        List<String> offenders = new ArrayList<String>();
        int checked = 0;

        for (Class<? extends MirthServlet> servlet : servlets()) {
            for (Method method : servlet.getDeclaredMethods()) {
                String wanted = scopedParamName(method);
                if (wanted == null) {
                    continue;
                }
                checked++;

                Method declaration = interfaceDeclarationOf(servlet, method);
                if (declaration == null) {
                    offenders.add(servlet.getSimpleName() + "." + method.getName() + " (no servlet interface declares it)");
                } else if (!paramNames(declaration).contains(wanted)) {
                    offenders.add(declaration.getDeclaringClass().getSimpleName() + "." + method.getName() + " (no @Param named \"" + wanted + "\")");
                }
            }
        }

        /*
         * Guards the enumeration itself. If the package scan ever stops matching, this test would
         * otherwise check nothing and pass. At the time of writing it is 30 servlets and 47 scoped
         * operations.
         */
        assertTrue("Found no scoped operations to check -- the servlet scan is broken", checked > 10);
        assertTrue("Scoped operations whose target parameter cannot be resolved: " + offenders, offenders.isEmpty());
    }

    /**
     * Reflections resolves classes without initializing them, so enumerating a servlet's methods
     * does not drag in the real controllers behind its static fields.
     */
    private Iterable<Class<? extends MirthServlet>> servlets() {
        return new Reflections("com.mirth.connect").getSubTypesOf(MirthServlet.class);
    }

    /**
     * The same walk the invocation handler does: up the class hierarchy, looking for a servlet
     * interface that declares this method.
     */
    private Method interfaceDeclarationOf(Class<?> servlet, Method method) {
        for (Class<?> clazz = servlet; clazz != null; clazz = clazz.getSuperclass()) {
            for (Class<?> interfaceClass : clazz.getInterfaces()) {
                if (!BaseServletInterface.class.isAssignableFrom(interfaceClass)) {
                    continue;
                }
                try {
                    return interfaceClass.getMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    // Try the next interface
                }
            }
        }
        return null;
    }

    private String scopedParamName(Method method) {
        CheckAuthorizedUserId userId = method.getAnnotation(CheckAuthorizedUserId.class);
        if (userId != null) {
            return userId.paramName();
        }
        CheckAuthorizedChannelId channelId = method.getAnnotation(CheckAuthorizedChannelId.class);
        if (channelId != null) {
            return channelId.paramName();
        }
        return null;
    }

    private List<String> paramNames(Method method) {
        List<String> names = new ArrayList<String>();
        for (Annotation[] parameterAnnotations : method.getParameterAnnotations()) {
            for (Annotation annotation : parameterAnnotations) {
                if (annotation instanceof Param) {
                    names.add(((Param) annotation).value());
                }
            }
        }
        return names;
    }
}
